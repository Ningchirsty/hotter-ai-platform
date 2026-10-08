#!/usr/bin/env bash
# =====================================================================
# 生产健康巡检（部署失败场景的告警防线）
# =====================================================================
# 为什么需要它（2026-10-08 P0 事故）：
#   磁盘写满 → 后端容器只创建成功、无法启动 → 部署健康检查超时 → 回滚也因写满失败
#   → **后端完全不可用，而且没有任何告警**。我是靠盯着 deploy run 才发现失败的。
#   更隐蔽的是：**从公网看不出来**——`ruoyi-web` 是 nginx，对任何路径都回 200 + SPA 的
#   index.html（含 `/actuator/health`、`/auth/code`），所以"站点是活的"与"后端是活的"
#   是两件事。实测确认：后端挂掉时 `https://pm.hottter.cn/actuator/health` 仍返回 200 的 HTML。
#
# 因此本脚本**只探直连后端** `127.0.0.1:18082`（本 job 跑在宿主机上的 self-hosted runner，
# 与后端共享主机网络）。判定不只看状态码，还要求响应体是 JSON——避免再被 HTML 骗过一次。
#
# 用法：bash script/deploy/health-check.sh
# 退出码：0=全部正常；1=有 CRITICAL；2=有 WARN（无 CRITICAL）
# =====================================================================
set -uo pipefail

BACKEND_URL="${HOTTER_BACKEND_PROBE_URL:-http://127.0.0.1:18082/auth/code}"
BACKEND_CONTAINER="${HOTTER_BACKEND_CONTAINER:-ai-video-poc-backend-1}"
FRONTEND_CONTAINER="${HOTTER_FRONTEND_CONTAINER:-ruoyi-web}"
MIN_FREE_KB="${HOTTER_MIN_FREE_KB:-3145728}"          # 与 hotter-release 同口径：3GB
STALE_CONTAINER_LIMIT="${HOTTER_STALE_LIMIT:-6}"     # previous-* 超过这个数就提醒（保留策略是 3）

critical=0
warn=0
report() { printf '%s\n' "$*"; }

report "=== production health check ($(date -u '+%Y-%m-%dT%H:%M:%SZ')) ==="

# ---- 1) 后端可用性（最关键） ----
# 只用一次 curl：`-w` 把状态码追加在正文之后，避免请求两次（两次之间状态可能变化，
# 且连接失败时第一次会输出空串、与第二次的 000 拼接成 000000）。
raw=$(curl --silent --show-error --max-time 15 --write-out $'\n__CODE__%{http_code}' "$BACKEND_URL" 2>/dev/null || true)
code="${raw##*__CODE__}"
body="${raw%$'\n'__CODE__*}"
code="${code:-000}"

# 判定：HTTP 2xx/4xx（应用在应答，401/403 也算活着）+ 响应体以 '{' 开头（是 JSON，不是 nginx 的 HTML）
if [ "$code" = "000" ]; then
  report "[CRITICAL] backend unreachable: $BACKEND_URL (curl failed)"
  critical=$((critical + 1))
elif [ "${code:0:1}" != "2" ] && [ "${code:0:1}" != "4" ]; then
  report "[CRITICAL] backend returned http=$code from $BACKEND_URL"
  critical=$((critical + 1))
elif [ "${body:0:1}" != "{" ]; then
  report "[CRITICAL] backend answered http=$code but body is NOT json (first char '${body:0:1}')"
  report "           -> this is the nginx/SPA trap: a 200 HTML page does not mean the backend is up"
  critical=$((critical + 1))
else
  report "[ok] backend alive: http=$code json=yes"
fi

# ---- 2) 容器状态 ----
for c in "$BACKEND_CONTAINER" "$FRONTEND_CONTAINER"; do
  if ! docker inspect "$c" >/dev/null 2>&1; then
    report "[CRITICAL] container missing: $c"
    critical=$((critical + 1))
    continue
  fi
  state=$(docker inspect "$c" --format '{{.State.Status}}')
  health=$(docker inspect "$c" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}')
  restarts=$(docker inspect "$c" --format '{{.RestartCount}}')
  if [ "$state" != "running" ]; then
    report "[CRITICAL] container $c state=$state (expected running)"
    critical=$((critical + 1))
  elif [ "$health" = "unhealthy" ]; then
    report "[CRITICAL] container $c is unhealthy (restarts=$restarts)"
    critical=$((critical + 1))
  else
    report "[ok] container $c state=$state health=$health restarts=$restarts"
  fi
done

# ---- 3) 磁盘水位（本次事故的直接原因） ----
free_kb=$(df -Pk / | awk 'NR==2 {print $4}')
if [[ "$free_kb" =~ ^[0-9]+$ ]]; then
  if [ "$free_kb" -lt "$MIN_FREE_KB" ]; then
    report "[CRITICAL] disk low: $((free_kb / 1024))MB free < $((MIN_FREE_KB / 1024))MB required"
    report "           (a deploy would fail AND its rollback would fail too - see the 2026-10-08 incident)"
    critical=$((critical + 1))
  elif [ "$free_kb" -lt $((MIN_FREE_KB * 2)) ]; then
    report "[WARN] disk getting low: $((free_kb / 1024))MB free"
    warn=$((warn + 1))
  else
    report "[ok] disk free: $((free_kb / 1024))MB"
  fi
else
  report "[WARN] could not read disk usage (df output unexpected)"
  warn=$((warn + 1))
fi

# ---- 4) 回滚凭据：当前运行镜像是否仍在本地 ----
cur_image=$(docker inspect "$BACKEND_CONTAINER" --format '{{.Config.Image}}' 2>/dev/null || true)
if [ -z "$cur_image" ]; then
  report "[WARN] cannot read running backend image, rollback target unknown"
  warn=$((warn + 1))
elif docker image inspect "$cur_image" >/dev/null 2>&1; then
  report "[ok] rollback target present locally"
else
  report "[CRITICAL] running image is NOT present locally: $cur_image (rollback would be impossible)"
  critical=$((critical + 1))
fi

# ---- 5) 陈旧 previous-* 容器堆积（单调增长，会吃干磁盘） ----
stale_count=$(docker ps -a --filter 'name=ruoyi-web-previous-' -q 2>/dev/null | wc -l | tr -d ' ')
if [ "$stale_count" -gt "$STALE_CONTAINER_LIMIT" ]; then
  report "[WARN] stale previous-* containers: $stale_count (limit $STALE_CONTAINER_LIMIT; release keeps 3)"
  warn=$((warn + 1))
else
  report "[ok] stale previous-* containers: $stale_count"
fi

# ---- 6) 模型健康探测的"新鲜度"（M-003） ----
# 为什么放在这里：2026-10-08 巡检查出，真正参与路由的 3 个模型里没有一个"近期测过且健康"
#   （1 个从未测过、1 个有状态无时间、1 个 14 天未复测）。探测能力早就有，缺的是"到点自动跑"；
#   在自动探测落地之前，至少要让**长期没人测**这件事可见，否则它会一直是盲区。
#
# ★ 判据必须与**探测范围**一致（2026-10-08 实测教训）：
#   探测是 only-bound=true —— 只测"有启用绑定"的模型，因为**没有绑定的模型根本不可能被路由到**
#   （路由候选集来自 aig_capability_model），为它们付费没有收益。
#   而本项原先按"启用 + 生命周期可调用"统计，口径更宽：`nvidia` / `openrouter/free`
#   两个模型启用、GRAY/PRODUCTION、但**绑定数为 0**，于是它们永远不会被探测刷新
#   => 报出一个**永久的 WARN**（实测 never_tested=0, older_than_7d=2），
#      把"探测没在跑"和"这两个模型不在探测范围内"混成同一个信号。
#   永久 WARN 等于没有告警（会训练人忽略它），所以这里按**可路由**（有启用绑定）统计，
#   并把"启用但没有绑定"的模型单独如实列出来，而不是塞进同一个 WARN 里。
MYSQL_CONTAINER="${HOTTER_MYSQL_CONTAINER:-ai-video-poc-mysql-1}"
STALE_PROBE_DAYS="${HOTTER_STALE_PROBE_DAYS:-7}"
if docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
  q="SELECT
       SUM(CASE WHEN g.health_time IS NULL THEN 1 ELSE 0 END) AS never_tested,
       SUM(CASE WHEN g.health_time IS NOT NULL
                 AND TIMESTAMPDIFF(DAY, g.health_time, NOW()) >= ${STALE_PROBE_DAYS} THEN 1 ELSE 0 END) AS stale,
       SUM(CASE WHEN g.health_status IS NOT NULL AND g.health_time IS NULL THEN 1 ELSE 0 END) AS status_without_time,
       SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM aig_capability_model c
                                  WHERE c.model_id = g.model_id AND c.status = '0' AND c.del_flag = '0')
                THEN 1 ELSE 0 END) AS unbound
     FROM aig_model_governance g
     JOIN sai_model_config m ON m.id = g.model_id AND m.is_enabled = 1
     WHERE g.del_flag='0' AND g.status='0'
       AND g.lifecycle_status IN ('TRIAL','GRAY','PRODUCTION')
       AND EXISTS (SELECT 1 FROM aig_capability_model c
                    WHERE c.model_id = g.model_id AND c.status = '0' AND c.del_flag = '0');"
  q_unbound="SELECT COUNT(*)
     FROM aig_model_governance g
     JOIN sai_model_config m ON m.id = g.model_id AND m.is_enabled = 1
     WHERE g.del_flag='0' AND g.status='0'
       AND g.lifecycle_status IN ('TRIAL','GRAY','PRODUCTION')
       AND NOT EXISTS (SELECT 1 FROM aig_capability_model c
                        WHERE c.model_id = g.model_id AND c.status = '0' AND c.del_flag = '0');"
  row=$(docker exec -i "$MYSQL_CONTAINER" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -D ai_video_poc -N -B -e \"$q\"" 2>/dev/null | tail -1)
  unbound=$(docker exec -i "$MYSQL_CONTAINER" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -D ai_video_poc -N -B -e \"$q_unbound\"" 2>/dev/null | tail -1)
  never=$(echo "$row" | awk '{print $1}')
  stale=$(echo "$row" | awk '{print $2}')
  notime=$(echo "$row" | awk '{print $3}')
  if [[ "$never" =~ ^[0-9]+$ ]]; then
    if [ "$notime" -gt 0 ]; then
      report "[WARN] $notime routable model(s) have health_status but NO health_time (undecidable state)"
      warn=$((warn + 1))
    fi
    if [ "$never" -gt 0 ] || [ "$stale" -gt 0 ]; then
      report "[WARN] routable model health probes stale: never_tested=$never, older_than_${STALE_PROBE_DAYS}d=$stale"
      report "           probes now run IN-PROCESS (aigov.scheduling.enabled + aigov.model.health-probe.enabled),"
      report "           first round at container start then every ~30min. For a ROUTABLE model staleness means the"
      report "           scheduler is NOT running. Check the startup log for logger"
      report "           org.dromara.aigov.config.AigSchedulingConfig, or probe one manually:"
      report "           POST /aigov/model/{id}/test"
      warn=$((warn + 1))
    else
      report "[ok] routable model health probes fresh (never_tested=0, stale=0)"
    fi
    if [[ "$unbound" =~ ^[0-9]+$ ]] && [ "$unbound" -gt 0 ]; then
      report "[note] $unbound enabled model(s) have NO capability binding => unreachable by routing and"
      report "       deliberately outside the probe scope (only-bound=true), so their health stays stale."
      report "       Owner decision: suspend/disable them, bind them, or accept. NOT counted as a WARN."
    fi
  else
    report "[WARN] could not read model-probe staleness from $MYSQL_CONTAINER"
    warn=$((warn + 1))
  fi
else
  report "[WARN] mysql container $MYSQL_CONTAINER not found; skipped model-probe staleness"
  warn=$((warn + 1))
fi

# ---- 7) 后台任务是否真的在跑（2026-10-08 发现：一个都没跑） ----
# 为什么必须**持续检测**而不是记在文档里：
#   本仓 @EnableScheduling 原先只在 ruoyi-common-job 的 SnailJobConfig 上，而它被
#   @ConditionalOnProperty(snail-job.enabled=true) 门控（生产为 false），
#   => **任务状态机扫描 / 审批超时扫描 / 健康探测一个都不会被触发，而且不报错、不留日志**。
#   2026-10-08 第 3 步已用 aigov.scheduling.enabled=true 打开调度，**但只激活了健康探测**：
#   另外两个写操作任务各自被自己的开关门控，生产**仍是关闭**（这是有意的，见 ADR-009）。
#   所以本项检查依然必要——它守的是"该被扫掉的悬挂行没有被扫掉"。
#   当前 aig_task 与 aig_call_approval 都是 0 行，所以"看不出问题"；
#   一旦有数据，症状才会出现（过期审批一直挂着、失败任务不再自动重试）。
# 判据用**"不该存在的悬挂行"**，而不是去猜调度器状态：
#   PENDING 且 expire_time 已过 = 审批超时扫描没在跑；
#   RETRY_WAIT/QUEUED 且已过 1 天 = 任务扫描没在跑。
if docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
  q2="SELECT
        (SELECT COUNT(*) FROM aig_call_approval
          WHERE del_flag='0' AND status='PENDING' AND expire_time IS NOT NULL
            AND expire_time < NOW()) AS overdue_pending,
        (SELECT COUNT(*) FROM aig_task
          WHERE del_flag='0' AND status IN ('RETRY_WAIT','QUEUED')
            AND create_time IS NOT NULL
            AND TIMESTAMPDIFF(DAY, create_time, NOW()) >= 1) AS stuck_tasks;"
  row2=$(docker exec -i "$MYSQL_CONTAINER" sh -c \
    "mysql --default-character-set=utf8mb4 -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -D ai_video_poc -N -B -e \"$q2\"" 2>/dev/null | tail -1)
  overdue=$(echo "$row2" | awk '{print $1}')
  stuck=$(echo "$row2" | awk '{print $2}')
  if [[ "$overdue" =~ ^[0-9]+$ ]]; then
    if [ "$overdue" -gt 0 ] || [ "$stuck" -gt 0 ]; then
      report "[WARN] background jobs look inactive: overdue_pending_approvals=$overdue, stuck_tasks=$stuck"
      report "           these rows SHOULD have been swept. The two write-sweeps are still switched off"
      report "           on purpose (aigov.approval.expire-scan-enabled / aigov.task.scheduler.enabled)."
      report "           Either enable one, or trigger manually: POST /aigov/approval/expire-scan"
      report "           , POST /aigov/task/scheduler/sweep"
      warn=$((warn + 1))
    else
      report "[ok] no overdue approvals / stuck tasks (background sweeps not yet needed)"
    fi
  else
    report "[WARN] could not read background-job backlog from $MYSQL_CONTAINER"
    warn=$((warn + 1))
  fi
fi

# ---- 8) 依赖服务（2026-10-08 补：它们的故障不会让第 1/2 项变红） ----
# 为什么必须单独查（这是我问自己"告警还漏了什么"时找出来的盲区）：
#   · 第 1 项探的是 /auth/code —— 它只依赖 Redis（写验证码），**完全不碰 MySQL**；
#     也就是说 **MySQL 单独挂掉时第 1 项照样绿**；
#   · 第 6/7 项虽然用 docker exec 查了 MySQL，但读不到时只报 **WARN**，不是 CRITICAL；
#   · **MinIO 完全没有被任何一项覆盖**：它挂了的表现是"上传/取图失败"，
#     而后端仍然正常应答 200。这是最危险的一类——功能坏了但告警是绿的。
DEP_CONTAINERS="${HOTTER_DEP_CONTAINERS:-ai-video-poc-mysql-1 ai-video-poc-redis-1 ai-video-poc-minio-1}"
for c in $DEP_CONTAINERS; do
  if ! docker inspect "$c" >/dev/null 2>&1; then
    report "[CRITICAL] dependency container missing: $c"
    critical=$((critical + 1))
    continue
  fi
  dstate=$(docker inspect "$c" --format '{{.State.Status}}')
  dhealth=$(docker inspect "$c" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}')
  if [ "$dstate" != "running" ]; then
    report "[CRITICAL] dependency $c state=$dstate (expected running)"
    critical=$((critical + 1))
  elif [ "$dhealth" = "unhealthy" ]; then
    report "[CRITICAL] dependency $c is unhealthy"
    critical=$((critical + 1))
  else
    report "[ok] dependency $c state=$dstate health=$dhealth"
  fi
done

# 容器在跑不等于服务可用——而且 MinIO **没有 healthcheck**（实测 health=none），
# 所以对存储必须真探一次活性。MinIO 的 9000/9001 没有映射到宿主机，
# 只有同一 docker 网络内的容器能连，因此从后端容器里发。
MINIO_PROBE_URL="${HOTTER_MINIO_PROBE_URL:-http://ai-video-poc-minio-1:9000/minio/health/live}"
if ! docker inspect "$BACKEND_CONTAINER" >/dev/null 2>&1; then
  report "[WARN] cannot probe storage liveness: backend container $BACKEND_CONTAINER missing"
  warn=$((warn + 1))
elif ! docker exec "$BACKEND_CONTAINER" sh -c 'command -v curl >/dev/null 2>&1'; then
  # 探不了 ≠ 探过了没问题：不能静默算通过（否则镜像一换就变成永久盲区）
  report "[WARN] cannot probe storage liveness: no curl inside $BACKEND_CONTAINER"
  warn=$((warn + 1))
else
  mcode=$(docker exec "$BACKEND_CONTAINER" curl -s -o /dev/null -m 10 -w '%{http_code}' "$MINIO_PROBE_URL" 2>/dev/null || true)
  mcode="${mcode:-000}"
  if [ "$mcode" = "200" ]; then
    report "[ok] storage liveness: http=200 ($MINIO_PROBE_URL)"
  else
    report "[CRITICAL] storage liveness failed: http=$mcode from $MINIO_PROBE_URL"
    report "           uploads/reads would fail while the backend still answers 200"
    critical=$((critical + 1))
  fi
fi

report ""
report "=== summary: critical=$critical warn=$warn ==="
if [ "$critical" -gt 0 ]; then
  report "RESULT: CRITICAL"
  exit 1
elif [ "$warn" -gt 0 ]; then
  report "RESULT: WARN"
  exit 2
fi
report "RESULT: OK"
exit 0

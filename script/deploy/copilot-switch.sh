#!/usr/bin/env bash
# =====================================================================
# 训练台「与培训助手对话」生产开关：一键打开 / 关闭 / 查看
#   aigov.studio.copilot.enabled  ←→  环境变量 AIGOV_STUDIO_COPILOT_ENABLED
#
# 为什么需要它
#   `application-prod.yml` 里已经写了 `aigov: studio: copilot: enabled: true`，
#   但那份配置是**打进镜像**的。在**不重新发布镜像**的前提下，让生产立刻生效的
#   唯一办法就是：给后端容器注入环境变量，然后重建容器
#   （Spring relaxed binding：AIGOV_STUDIO_COPILOT_ENABLED → aigov.studio.copilot.enabled）。
#
#   这个变量必须写进**容器**（compose 配置），不能只 `export` 在当前 shell —— 容器不会继承它。
#
# 这个脚本的两个设计原则
#   1) **默认干跑**：不看 `--apply` 一律不动文件、不动容器；
#   2) **自带验证与回滚**：改完重建容器后，用 `docker inspect` 检查容器里**真的**有这个变量；
#      没验证通过就自动恢复备份并重建回去（绝不留一半、也绝不"看起来成功了"）。
#
# 用法
#   script/deploy/copilot-switch.sh status              # 只读：现在容器里是什么值、项目/服务是谁
#   script/deploy/copilot-switch.sh on                  # 干跑：只打印将要改什么
#   script/deploy/copilot-switch.sh on  --apply         # 执行：打开
#   script/deploy/copilot-switch.sh off --apply         # 执行：关闭（回滚）
#   BASE_URL=https://pm.hottter.cn/prod-api PROBE_TOKEN=*** \
#     script/deploy/copilot-switch.sh probe              # 用接口零成本探针确认（不调模型）
#
# 退出码
#   0 成功（含干跑）  2 环境/容器没找到  3 改了但验证没过（已自动回滚）  4 参数错
#
# ⚠️ 诚实声明：本脚本**没有在真实宿主上跑过**（作者没有宿主访问权）。
#    因此每一步都打印它看到了什么、要改什么；请先 `status` + 干跑看一眼再 `--apply`。
# =====================================================================
set -euo pipefail

VAR_NAME="AIGOV_STUDIO_COPILOT_ENABLED"
DEFAULT_WORKDIR="/opt/ai-video-poc"

ACTION="${1:-status}"
APPLY=0
for arg in "${@:2}"; do
  case "$arg" in
    --apply) APPLY=1 ;;
    *) printf 'ERROR: 不认识的参数：%s（只有 --apply）\n' "$arg" >&2; exit 4 ;;
  esac
done

log()  { printf '%s\n' "$*"; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit "${2:-1}"; }
note() { printf '  %s\n' "$*"; }

command -v docker >/dev/null 2>&1 || die "找不到 docker（这个脚本要在跑容器的那台宿主机上执行）" 2

# `docker compose` 优先，退回 `docker-compose`
compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    die "找不到 docker compose / docker-compose" 2
  fi
}

label_of() { docker inspect -f "{{ index .Config.Labels \"$2\" }}" "$1" 2>/dev/null || true; }

# 容器里的环境变量（一行一个），供 grep
container_env() { docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$1" 2>/dev/null || true; }

find_backend_cid() {
  local cid
  cid=$(docker ps --filter 'label=com.docker.compose.service=backend' --format '{{.ID}}' | head -n1)
  if [ -z "$cid" ]; then
    cid=$(docker ps --format '{{.ID}} {{.Names}}' | awk 'tolower($2) ~ /backend/ {print $1; exit}')
  fi
  [ -n "$cid" ] || return 1
  printf '%s' "$cid"
}

#---------------------------------------------------------------------
# status：只读，把"看到的事实"全打出来
#---------------------------------------------------------------------
do_status() {
  local cid name current
  cid=$(find_backend_cid) || die "找不到后端容器（compose service=backend，或名字里有 backend）" 2
  name=$(docker inspect -f '{{.Name}}' "$cid" | sed 's#^/##')

  log "后端容器          : $name ($cid)"
  log "compose 项目      : $(label_of "$cid" com.docker.compose.project)"
  log "compose 服务      : $(label_of "$cid" com.docker.compose.service)"
  log "compose 项目目录  : $(label_of "$cid" com.docker.compose.project.working_dir)"
  log "compose 配置文件  : $(label_of "$cid" com.docker.compose.project.config_files)"
  log "容器镜像          : $(docker inspect -f '{{.Config.Image}}' "$cid")"

  current=$(container_env "$cid" | grep -E "^${VAR_NAME}=" || true)
  if [ -n "$current" ]; then
    log "容器内 ${VAR_NAME}: ${current}"
    log "  ⇒ 应用取到的是【${current#*=}】"
  else
    log "容器内 ${VAR_NAME}: (未设置)"
    log "  ⇒ 应用取的是**镜像里** application-prod.yml 的值："
    log "     部署的是 8d84ad0 及之后的镜像 ⇒ true（已打开）"
    log "     部署的是更早的镜像           ⇒ 无该配置 ⇒ 默认 false（关闭）"
  fi

  local status
  status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}(无 healthcheck){{end}}' "$cid" 2>/dev/null || true)
  log "健康状态          : $status"
}

#---------------------------------------------------------------------
# 改 .env（幂等：有就替换，没有就追加），改前备份
#---------------------------------------------------------------------
ENV_FILE=""
BACKUP_FILE=""

patch_env() {
  local value="$1" line
  ENV_FILE="${WORKDIR}/.env"
  log "环境文件          : ${ENV_FILE}"

  if [ -f "$ENV_FILE" ]; then
    BACKUP_FILE="${ENV_FILE}.bak.$(date +%Y%m%d%H%M%S)"
    note "备份：$BACKUP_FILE"
    if [ "$APPLY" = "1" ]; then cp -p "$ENV_FILE" "$BACKUP_FILE"; fi
    line=$(grep -E "^[[:space:]]*${VAR_NAME}=" "$ENV_FILE" || true)
    if [ -n "$line" ]; then
      note "把现有那行 ${line} 改成 ${VAR_NAME}=${value}"
      if [ "$APPLY" = "1" ]; then
        sed -i "s|^[[:space:]]*${VAR_NAME}=.*|${VAR_NAME}=${value}|" "$ENV_FILE"
      fi
    else
      note "追加一行：${VAR_NAME}=${value}"
      if [ "$APPLY" = "1" ]; then printf '\n%s=%s\n' "$VAR_NAME" "$value" >> "$ENV_FILE"; fi
    fi
  else
    note "$ENV_FILE 不存在 ⇒ 创建它，并写入 ${VAR_NAME}=${value}"
    if [ "$APPLY" = "1" ]; then printf '%s=%s\n' "$VAR_NAME" "$value" > "$ENV_FILE"; fi
  fi
}

recreate_backend() {
  log "重建后端容器      : compose --project-directory ${WORKDIR} -f ${COMPOSE_FILE} up -d --no-deps ${SERVICE}"
  if [ "$APPLY" = "1" ]; then
    compose --project-directory "$WORKDIR" -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE"
  fi
}

# 重建后容器 ID 会变，必须重新找；再核对容器里真的有这个变量
verify_applied() {
  local expected="$1" i cid current
  log "验证（最多等 30s）: 容器里 ${VAR_NAME} 是否等于 ${expected}"
  if [ "$APPLY" != "1" ]; then
    note "（干跑，跳过验证）"
    return 0
  fi
  for i in $(seq 1 10); do
    cid=$(find_backend_cid || true)
    if [ -n "$cid" ]; then
      current=$(container_env "$cid" | grep -E "^${VAR_NAME}=" || true)
      if [ "$current" = "${VAR_NAME}=${expected}" ]; then
        note "✅ 已验证：容器 $(docker inspect -f '{{.Name}}' "$cid" | sed 's#^/##') 内 ${current}"
        return 0
      fi
    fi
    sleep 3
  done
  note "❌ 验证失败：等了 30s，容器里仍看不到 ${VAR_NAME}=${expected}"
  return 1
}

rollback_env() {
  log "回滚：恢复备份并重建容器"
  if [ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_FILE" ]; then
    cp -p "$BACKUP_FILE" "$ENV_FILE"
    note "已恢复 $ENV_FILE ← $BACKUP_FILE"
  else
    note "没有备份可恢复（原先没有 .env）⇒ 请手工检查 $ENV_FILE"
  fi
  compose --project-directory "$WORKDIR" -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE" || true
}

#---------------------------------------------------------------------
# 主流程
#---------------------------------------------------------------------
do_switch() {
  local value="$1"
  local cid
  cid=$(find_backend_cid) || die "找不到后端容器（compose service=backend，或名字里有 backend）" 2

  WORKDIR=$(label_of "$cid" com.docker.compose.project.working_dir)
  COMPOSE_FILE=$(label_of "$cid" com.docker.compose.project.config_files)
  SERVICE=$(label_of "$cid" com.docker.compose.service)
  [ -n "$WORKDIR" ] || WORKDIR="$DEFAULT_WORKDIR"
  [ -n "$SERVICE" ] || SERVICE="backend"
  [ -n "$COMPOSE_FILE" ] || COMPOSE_FILE="${WORKDIR}/docker-compose.yml"

  log "目标：把 ${VAR_NAME} 设为 ${value}（$([ "$APPLY" = "1" ] && echo 执行 || echo 干跑)）"
  log "  容器        : $(docker inspect -f '{{.Name}}' "$cid" | sed 's#^/##')"
  log "  项目目录    : $WORKDIR"
  log "  配置文件    : $COMPOSE_FILE"
  log "  服务        : $SERVICE"
  if [ ! -f "$COMPOSE_FILE" ]; then
    die "配置文件不存在：$COMPOSE_FILE（标签没读到或路径变了，请手工确认后再跑）" 2
  fi

  patch_env "$value"
  recreate_backend

  if ! verify_applied "$value"; then
    rollback_env
    log ""
    log "⚠️ 自动回滚完成。这个项目很可能**不是**用 .env 给后端注入环境变量的。"
    log "   请手工看 ${COMPOSE_FILE} 里 ${SERVICE} 服务的 environment:/env_file: 段，二选一："
    log "     a) 在 environment: 下加一行  - ${VAR_NAME}=${value}"
    log "     b) 让它 env_file: 指向一个包含 ${VAR_NAME}=${value} 的文件"
    log "   改完再执行： compose --project-directory ${WORKDIR} -f ${COMPOSE_FILE} up -d --no-deps ${SERVICE}"
    exit 3
  fi

  log ""
  log "完成。别忘了最终判据是**接口探针**（不调模型、不花钱）："
  log "  BASE_URL=https://pm.hottter.cn/prod-api PROBE_TOKEN=<登录token> $0 probe"
  log "  回「训练草稿不存在」= 开；回「当前是关闭的」= 仍关。"
  if [ "$APPLY" != "1" ]; then
    log "（以上为干跑结果，未做任何改动。加 --apply 才会真正执行。）"
  else
    log "回滚：$0 off --apply"
  fi
}

#---------------------------------------------------------------------
# probe：零成本接口探针（用不存在的草稿，绝不可能走到模型调用）
#---------------------------------------------------------------------
do_probe() {
  BASE_URL="${BASE_URL:-https://pm.hottter.cn/prod-api}"
  CLIENT_ID="${CLIENT_ID:-e5cd7e4891bf95d1d19206ce24a7b32e}"
  [ -n "${PROBE_TOKEN:-}" ] || die "需要 PROBE_TOKEN=<已登录的 access_token>" 4
  local body
  body=$(curl -s -X POST "${BASE_URL}/aigov/studio/drafts/999999999/copilot/chat" \
    -H 'Content-Type: application/json' \
    -H "clientid: ${CLIENT_ID}" \
    -H "Authorization: Bearer ${PROBE_TOKEN}" \
    -d '{"message":"x","dataLevel":"INTERNAL"}')
  log "响应：${body}"
  case "$body" in
    *关闭*)      log "⇒ 开关仍是【关】" ;;
    *训练草稿不存在*) log "⇒ 开关已是【开】（已越过开关校验，且因为草稿不存在，没有产生任何模型调用）" ;;
    *)           log "⇒ 结果需要人工判断" ;;
  esac
}

case "$ACTION" in
  status) do_status ;;
  on)     do_switch true ;;
  off)    do_switch false ;;
  probe)  do_probe ;;
  *)      printf 'ERROR: 未知动作 %s（可用：status | on | off | probe）\n' "$ACTION" >&2; exit 4 ;;
esac

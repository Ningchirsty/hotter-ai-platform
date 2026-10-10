#!/bin/bash
# ---------------------------------------------------------------------------
# hotter-sandbox 作业 worker：从队列目录里**认领**作业、调用沙箱执行器、把结果写成文件。
#
# 为什么需要它（而不是"某个脚本顺手跑一下"）：
#   slice-1 交付的执行器 sandbox-run.sh 只回答"怎么把一段不可信命令关进容器"，
#   它不认领作业、不记录归属、不保证只跑一次。少了 worker 这一层，
#   "某次沙箱运行"在平台侧就没有任何可核对的凭据——而 SANDBOX_RUN 是五个发布闸门之一
#   （docs/platform-v2/01-ADR），现在它只有"声明"，没有"证据"。
#   worker 写的 result.json 就是那份证据的来源：谁（agentCode/versionId）在哪个镜像里
#   跑了什么、退出码、耗时、产物 sha256，全部落成文件。
#
# 队列协议（DIR 就是作业目录）：
#   $JOBS_ROOT/<jobId>/request.json    生产方**原子写入**（先写 .tmp 再 mv 进来）
#   $JOBS_ROOT/<jobId>/work/           容器内 /work（worker 创建；产物落这里）
#   $JOBS_ROOT/<jobId>/result.json     worker 完成后原子写入（存在即"已完成"）
#   $JOBS_ROOT/<jobId>/.lock           flock 认领锁（防两个 worker 同时跑同一个作业）
#   $JOBS_ROOT/<jobId>/.worker-started 执行中的时间戳（给运维看"在飞的作业"）
#
# request.json 字段（未列出的字段忽略）：
#   image       必填，镜像 ref；必须在执行器的白名单里**且本地已存在**（沙箱不拉远端镜像）
#   cmd         必填，要跑的 shell 命令
#   timeoutSec  选填，默认 120（执行器默认）
#   cpus/memMb/pids  选填，默认 1/512/128
#   network     选填 bool，默认 false；true 会传 --allow-network（出网策略属 F-09，尚未定）
#   agentCode / versionId / requestId  选填，原样抄进 result.json（**归属凭据**，后端据此断言闸门）
#
# 用法：
#   sandbox-worker.sh --once                 # 处理完当前队列就退出（自检/被 cron 拉起时用）
#   sandbox-worker.sh                        # 常驻，每 --interval 秒扫一次队列
# 选项：
#   --jobs-root DIR   默认 /var/lib/hotter-sandbox/work/jobs
#   --executor FILE   默认 /opt/hotter-sandbox/sandbox-run.sh
#   --interval S      默认 5
#   --max-jobs N      一次扫描最多认领几个（默认不限；执行器并发上限 1，所以会串行）
#   --archive-root DIR  默认 /var/lib/hotter-sandbox/archive（**必须在真磁盘上**）
#   --no-archive        关闭归档（只在排查时用：关掉它，宿主重启后产物就没了）
#
# 产物归档（为什么必须有）：
#   scratch 是 tmpfs，**宿主一重启产物就没了**，而账本里那条"产物 sha256=..."还在——
#   于是只剩一句"曾经有过"，拿不出东西。worker 跑完会把 result.json、执行器日志与产物
#   复制到归档目录（默认在真磁盘上），并写一份 archive.json（何时、多少字节、原件哈希、是否持久）。
#   · **不碰 result.json**：账本是按它的 SHA-256 存证的，事后改它等于让证据对不上。
#   · 保留策略：默认最多 20 个作业 / 2GB，超了删**最旧**的（用 SANDBOX_ARCHIVE_MAX_MB、
#     SANDBOX_ARCHIVE_KEEP_JOBS 覆盖）；单个作业就超过容量上限时会停下并告警，不删刚归档的那个。
#   · 归档目录不存在/不可写 = **直接拒绝启动**：静默不归档会让产物无声消失，而账本看起来一切正常。
#   · 归档目录与 scratch 同一个文件系统 = 不阻断，但每个作业记 durable=false 并告警。
#
# 交互边界（诚实说明，不假装）：
#   · 本脚本**只运行在宿主机上**，不由平台后端调用：后端容器没有 docker CLI/socket，
#     这是刻意保持的（见 sandbox-run.sh 头部）。
#   · 首版不引入 MQ：队列就是文件系统。**单实例**运行；两个 worker 抢同一作业时靠 per-job
#     flock 互斥，抢不到的那个直接跳过，不会重复执行。
#   · 执行器自身有全局并发上限 1，拿不到锁会"拒绝"。worker 把这种拒绝当**瞬态**处理：
#     不写 result.json，留给下一轮，避免把"现在忙"误记成"这个作业失败"。
# ---------------------------------------------------------------------------
set -Eeuo pipefail

JOBS_ROOT="/var/lib/hotter-sandbox/work/jobs"
EXECUTOR="/opt/hotter-sandbox/sandbox-run.sh"
INTERVAL=5
ONCE=0
MAX_JOBS=0
# 产物归档：scratch 是 tmpfs，宿主一重启就只剩账本里的哈希。worker 跑完把
# result.json、执行器日志与产物**复制**到真磁盘上的归档目录（复制而非移动：
# scratch 里那份要留给登记用，而且删掉它并不能换来任何空间——那是 tmpfs）。
ARCHIVE_ROOT="${SANDBOX_ARCHIVE_ROOT:-/var/lib/hotter-sandbox/archive}"
ARCHIVE_MAX_MB="${SANDBOX_ARCHIVE_MAX_MB:-2048}"
ARCHIVE_KEEP_JOBS="${SANDBOX_ARCHIVE_KEEP_JOBS:-20}"
ARCHIVE_ON=1

log() { printf '%s sandbox-worker: %s\n' "$(date -Is)" "$*"; }
die() { echo "sandbox-worker: $*" >&2; exit 1; }

# 用法段落自动取到 `set -Eeuo pipefail` 前一行：原先写死行号（2,45），
# 头部注释一长就会静默截断帮助文本。
usage() { sed -n '2,/^set -Eeuo pipefail$/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --once) ONCE=1; shift ;;
    --jobs-root) JOBS_ROOT="${2:-}"; shift 2 ;;
    --executor) EXECUTOR="${2:-}"; shift 2 ;;
    --interval) INTERVAL="${2:-}"; shift 2 ;;
    --max-jobs) MAX_JOBS="${2:-}"; shift 2 ;;
    --archive-root) ARCHIVE_ROOT="${2:-}"; shift 2 ;;
    --no-archive) ARCHIVE_ON=0; shift ;;
    -h|--help) usage ;;
    *) die "未知参数：$1" ;;
  esac
done

[ -d "$JOBS_ROOT" ] || die "队列目录不存在：$JOBS_ROOT（先跑 sandbox-install.sh）"
[ -x "$EXECUTOR" ] || die "执行器不存在或不可执行：$EXECUTOR"
command -v jq >/dev/null 2>&1 || die "缺 jq：request.json/result.json 的解析与转义都由它负责，不要用 sed 拼 JSON"
command -v flock >/dev/null 2>&1 || die "缺 flock：无法保证同一作业只被执行一次"

# 归档目录缺失/不可写是**硬错误**：静默不归档会让产物在宿主重启后无声消失，而账本里
# 看起来一切正常（哈希还在）。宁可起不来，也不要"以为归档了"。
ARCHIVE_DURABLE=1
if [ "$ARCHIVE_ON" = "1" ]; then
  [ -d "$ARCHIVE_ROOT" ] || die "归档目录不存在：$ARCHIVE_ROOT（先跑 sandbox-install.sh，或用 --archive-root 指定）"
  [ -w "$ARCHIVE_ROOT" ] || die "归档目录不可写：$ARCHIVE_ROOT（owner 应是运行 worker 的用户）"
  if [ "$(stat -c %d "$JOBS_ROOT")" = "$(stat -c %d "$ARCHIVE_ROOT")" ]; then
    # 同文件系统 = 归档也在 tmpfs 上 = 重启一起丢。不阻断作业（证据在账本里，与归档无关），
    # 但每个作业的 archive.json 都会记 durable=false，让"看起来归档了"不可能蒙混过去。
    ARCHIVE_DURABLE=0
    log "警告：归档目录与 scratch 在同一个文件系统（$ARCHIVE_ROOT）——重启会一起丢失，归档不算持久"
  fi
fi

now_iso() { date -Is; }

# 执行单个作业。作业目录由调用者保证已加锁。
run_job() {
  local dir="$1"
  local id req started ended exec_rc=0 err="" json="{}"
  id="$(basename "$dir")"
  req="$dir/request.json"
  local empty_result
  empty_result="$(jq -n --arg id "$id" '{jobId:$id, artifacts:[]}')"

  local image cmd ts cpus mem pids net agent code requestid
  image="$(jq -r '.image // empty' "$req")"
  cmd="$(jq -r '.cmd // empty' "$req")"
  ts="$(jq -r '.timeoutSec // 120' "$req")"
  cpus="$(jq -r '.cpus // 1' "$req")"
  mem="$(jq -r '.memMb // 512' "$req")"
  pids="$(jq -r '.pids // 128' "$req")"
  net="$(jq -r 'if .network == true then "1" else "0" end' "$req")"
  agent="$(jq -r '.agentCode // empty' "$req")"
  code="$(jq -r '.versionId // empty' "$req")"
  requestid="$(jq -r '.requestId // empty' "$req")"

  if [ -z "$image" ] || [ -z "$cmd" ]; then
    log "$id 参数不全（image/cmd 必填），记为 failed"
    write_result "$dir" "$id" "failed" "$exec_rc" "$(now_iso)" "$(now_iso)" \
      "$empty_result" "request.json 缺 image 或 cmd" "$agent" "$code" "$requestid"
    return 0
  fi

  mkdir -p "$dir/work"
  started="$(now_iso)"
  printf '%s\n' "$started" > "$dir/.worker-started"

  local args=(--image "$image" --cmd "$cmd" --work-dir "$dir/work"
              --timeout "$ts" --cpus "$cpus" --mem "$mem" --pids "$pids" --job-id "$id")
  if [ "$net" = "1" ]; then args+=(--allow-network); fi

  local out_file="$dir/.executor-stdout.log"
  local err_file="$dir/.executor-stderr.log"
  log "$id 开始：image=$image timeout=${ts}s net=$([ "$net" = 1 ] && echo bridge || echo none)"
  set +e
  json="$("$EXECUTOR" "${args[@]}" 2>"$err_file")"
  exec_rc=$?
  set -e
  printf '%s\n' "$json" > "$out_file"
  ended="$(now_iso)"

  if [ "$exec_rc" != "0" ]; then
    err="$(tail -3 "$err_file" 2>/dev/null | tr '\n' ' ')"
    # 执行器"现在忙"不是这个作业的错：不落 result.json，留给下一轮
    case "$err" in
      *并发上限*)
        log "$id 执行器忙（并发上限 1），本轮跳过，稍后重试"
        rm -f "$dir/.worker-started"
        return 0 ;;
    esac
    log "$id 被执行器拒绝（rc=$exec_rc）：$err"
    json="$empty_result"
    write_result "$dir" "$id" "refused" "$exec_rc" "$started" "$ended" "$json" "$err" "$agent" "$code" "$requestid"
    return 0
  fi

  # 执行器只保证 stdout 上有一行 JSON；解析失败要如实记为 failed 而不是写成空结果。
  # 注意：错误消息里必须引用**原始** stdout（$raw），不能引用已经被替换掉的 $json——
  # R100 实测踩过：那条 "执行器输出不是合法 JSON：<内容>" 打的是替换后的空结果，
  # 把真正的线索（执行器到底吐了什么）盖住了，白跑一轮排查。
  local raw="$json"
  if ! printf '%s' "$raw" | jq -e . >/dev/null 2>&1; then
    log "$id 执行器输出不是合法 JSON，记为 failed"
    json="$empty_result"
    write_result "$dir" "$id" "failed" "$exec_rc" "$started" "$ended" "$json" \
      "执行器 stdout 不是合法 JSON：$(printf '%s' "$raw" | head -c 300)" "$agent" "$code" "$requestid"
    return 0
  fi

  # 容器跑完 = completed；容器自身非零退出（业务失败）在 exitCode 里，不当成 worker 故障
  log "$id 完成：exitCode=$(printf '%s' "$json" | jq -r '.exitCode') timedOut=$(printf '%s' "$json" | jq -r '.timedOut')"
  write_result "$dir" "$id" "completed" "$exec_rc" "$started" "$ended" "$json" "" "$agent" "$code" "$requestid"
}

# result.json 原子落盘：先写隐藏临时文件再 mv，消费者永远看不到半截 JSON
write_result() {
  local dir="$1" id="$2" status="$3" exec_rc="$4" started="$5" ended="$6" json="$7" err="$8"
  local agent="$9" code="${10}" requestid="${11}"
  local tmp="$dir/.result.json.tmp"
  jq -n \
    --argjson r "$json" \
    --arg status "$status" --argjson executorRc "$exec_rc" \
    --arg startedAt "$started" --arg endedAt "$ended" \
    --arg error "$err" --arg agentCode "$agent" --arg versionId "$code" --arg requestId "$requestid" \
    '$r + {status:$status, executorRc:$executorRc, startedAt:$startedAt, endedAt:$endedAt,
           finishedAt:$endedAt, agentCode:$agentCode, versionId:$versionId, requestId:$requestId}
        + (if $error == "" then {} else {error:$error} end)' > "$tmp"
  mv -f "$tmp" "$dir/result.json"
  chmod a+r "$dir/result.json" 2>/dev/null || true
  # 执行器的 stdout/stderr **保留**：结果行只是摘要，"当时到底发生了什么"要靠这两份日志。
  # （第一版在这里把它们删了，R100 排查产物 JSON bug 时因此丢了现场，只能重跑一遍。）
  chmod a+r "$dir/.executor-stdout.log" "$dir/.executor-stderr.log" 2>/dev/null || true
  rm -f "$dir/.worker-started"
  # 归档放在 result.json 落盘**之后**：它是"这个作业的记录"，记录以结果为准
  if [ "$ARCHIVE_ON" = "1" ]; then
    archive_job "$dir" "$id" || log "$id 归档失败（不影响门槛证据，见 archive.json）"
  fi
}

# 把作业的记录与产物复制到持久归档目录，并按保留策略清理最旧的。
#
# 三条刻意设计：
#   · **不碰 result.json**：账本里存的是它的 SHA-256，事后改它等于让证据对不上。
#     归档信息写在旁边单独的 archive.json 里。
#   · **产物先复制到 .artifacts.tmp 再改名**：半截复制不能被当成"归档完成"。
#   · **失败不致命，但必须留痕**：archive.json 里 ok=false + 原因（磁盘满是最可能的那个）。
archive_job() {
  local dir="$1" id="$2"
  local dest="$ARCHIVE_ROOT/$id" tmp="$ARCHIVE_ROOT/.tmp.$id.$$"
  [ -f "$dir/result.json" ] || return 0
  if [ -d "$dest" ]; then
    log "$id 归档已存在，跳过"
    return 0
  fi

  local ok=1 err="" files=0 bytes=0 status=""
  status="$(jq -r '.status // empty' "$dir/result.json" 2>/dev/null || true)"
  rm -rf -- "$tmp"
  mkdir -p "$tmp/artifacts" || { log "$id 无法创建归档临时目录：$tmp"; return 1; }

  # result.json 与执行器日志：作业记录本体，务必先落地
  cp -a "$dir/result.json" "$tmp/result.json" || { ok=0; err="result.json 复制失败"; }
  cp -a "$dir"/.executor-stdout.log "$dir"/.executor-stderr.log "$tmp/" 2>/dev/null || true

  # 产物：work/ 可能不存在（作业在起容器前就被拒了）
  if [ -d "$dir/work" ]; then
    files="$(find "$dir/work" -type f ! -name '.sandbox-*' 2>/dev/null | wc -l)"
    bytes="$(du -sb "$dir/work" 2>/dev/null | awk '{print $1}')"
    if ! cp -a "$dir/work/." "$tmp/artifacts/" 2>/dev/null; then
      ok=0; err="${err:+$err; }产物复制失败（磁盘空间？）"
    fi
  fi
  bytes="${bytes:-0}"

  # 记录归档事实：谁、何时、多少、原件哈希、是否持久
  jq -n --arg jobId "$id" --arg at "$(now_iso)" --arg status "$status" \
        --argjson files "${files:-0}" --argjson bytes "${bytes:-0}" \
        --arg resultSha "$(sha256sum "$dir/result.json" | cut -d' ' -f1)" \
        --argjson ok "$([ "$ok" = 1 ] && echo true || echo false)" \
        --argjson durable "$([ "$ARCHIVE_DURABLE" = 1 ] && echo true || echo false)" \
        --arg err "$err" \
        '{jobId:$jobId, archivedAt:$at, status:$status, artifactFiles:$files, artifactBytes:$bytes,
          resultSha256:$resultSha, ok:$ok, durable:$durable}
         + (if $err == "" then {} else {error:$err} end)' > "$tmp/archive.json" \
    || { log "$id 写 archive.json 失败"; rm -rf -- "$tmp"; return 1; }

  # 原子改名：改名前叫 .tmp.<id>.<pid>，不会被当成已完成归档（隐藏目录也不参与清理）
  if [ "$ok" != "1" ]; then
    mv -f -- "$tmp" "$dest" 2>/dev/null || true
    chmod -R a+rX "$dest" 2>/dev/null || true
    log "$id 归档不完整：$err（保留在 $dest，archive.json 已记 ok=false）"
    prune_archive
    return 0
  fi
  if ! mv -f -- "$tmp" "$dest"; then
    log "$id 归档改名失败：$tmp -> $dest（磁盘空间？）"
    rm -rf -- "$tmp"
    return 1
  fi
  chmod -R a+rX "$dest" 2>/dev/null || true
  log "$id 已归档：产物 $files 个 / $bytes 字节 -> $dest"
  prune_archive
  return 0
}

# 保留策略：先按作业数，再按总容量，都删**最旧**的。
# 单个作业就可能超过容量上限（scratch 上限 1G），所以删到只剩它一个时必须停下来并告警，
# 而不是把刚归档的那个也删掉——那等于归档从来没发生。
prune_archive() {
  local -a names=()
  local name
  while IFS= read -r name; do
    [ -n "$name" ] && names+=("$name")
  done < <(find "$ARCHIVE_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name '.*' \
             -printf '%T@ %f\n' 2>/dev/null | sort -n | awk '{print $2}')

  while [ "${#names[@]}" -gt "$ARCHIVE_KEEP_JOBS" ]; do
    name="${names[0]}"
    log "归档超出保留数（$ARCHIVE_KEEP_JOBS），删除最旧：$name"
    rm -rf -- "$ARCHIVE_ROOT/$name"
    names=("${names[@]:1}")
  done

  local used_mb
  used_mb="$(du -sm "$ARCHIVE_ROOT" 2>/dev/null | awk '{print $1}')"
  while [ "${used_mb:-0}" -gt "$ARCHIVE_MAX_MB" ] && [ "${#names[@]}" -gt 1 ]; do
    name="${names[0]}"
    log "归档超出容量（${used_mb}MB > ${ARCHIVE_MAX_MB}MB），删除最旧：$name"
    rm -rf -- "$ARCHIVE_ROOT/$name"
    names=("${names[@]:1}")
    used_mb="$(du -sm "$ARCHIVE_ROOT" 2>/dev/null | awk '{print $1}')"
  done
  if [ "${used_mb:-0}" -gt "$ARCHIVE_MAX_MB" ]; then
    log "警告：归档仍超出容量上限（${used_mb}MB > ${ARCHIVE_MAX_MB}MB）：单个作业本身就超过上限，已保留不删"
  fi
  return 0
}

scan_once() {
  local n=0 dir id
  # 只认领"有 request.json 且还没有 result.json"的目录；隐藏目录（.claim 之类）跳过
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    id="$(basename "$dir")"
    [ -f "$dir/request.json" ] || continue
    [ -f "$dir/result.json" ] && continue
    if [ "$MAX_JOBS" != "0" ] && [ "$n" -ge "$MAX_JOBS" ]; then break; fi
    # 认领：拿不到锁说明另一个 worker 正在跑它，跳过（不重复执行、不重复记结果）
    exec 8>"$dir/.lock"
    if ! flock -n 8; then
      log "$id 已被另一个 worker 认领，跳过"
      exec 8>&-
      continue
    fi
    # 单个作业出错不能带倒常驻 worker：记日志后继续下一个（写日志本身也可能失败，忽略）
    run_job "$dir" || log "$id 处理过程异常（见上），继续下一个"
    exec 8>&-
    n=$((n + 1))
  done < <(find "$JOBS_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name '.*' 2>/dev/null | LC_ALL=C sort)
  return 0
}

log "启动：jobs-root=$JOBS_ROOT executor=$EXECUTOR once=$ONCE interval=${INTERVAL}s archive=$([ "$ARCHIVE_ON" = 1 ] && echo "$ARCHIVE_ROOT durable=$ARCHIVE_DURABLE max=${ARCHIVE_MAX_MB}MB keep=$ARCHIVE_KEEP_JOBS" || echo off)"
if [ "$ONCE" = "1" ]; then
  scan_once
  log "本轮结束（--once）"
  exit 0
fi
while :; do
  scan_once || log "本轮扫描出错（继续）"
  sleep "$INTERVAL"
done

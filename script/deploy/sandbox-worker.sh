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

log() { printf '%s sandbox-worker: %s\n' "$(date -Is)" "$*"; }
die() { echo "sandbox-worker: $*" >&2; exit 1; }

usage() { sed -n '2,45p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --once) ONCE=1; shift ;;
    --jobs-root) JOBS_ROOT="${2:-}"; shift 2 ;;
    --executor) EXECUTOR="${2:-}"; shift 2 ;;
    --interval) INTERVAL="${2:-}"; shift 2 ;;
    --max-jobs) MAX_JOBS="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) die "未知参数：$1" ;;
  esac
done

[ -d "$JOBS_ROOT" ] || die "队列目录不存在：$JOBS_ROOT（先跑 sandbox-install.sh）"
[ -x "$EXECUTOR" ] || die "执行器不存在或不可执行：$EXECUTOR"
command -v jq >/dev/null 2>&1 || die "缺 jq：request.json/result.json 的解析与转义都由它负责，不要用 sed 拼 JSON"
command -v flock >/dev/null 2>&1 || die "缺 flock：无法保证同一作业只被执行一次"

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

log "启动：jobs-root=$JOBS_ROOT executor=$EXECUTOR once=$ONCE interval=${INTERVAL}s"
if [ "$ONCE" = "1" ]; then
  scan_once
  log "本轮结束（--once）"
  exit 0
fi
while :; do
  scan_once || log "本轮扫描出错（继续）"
  sleep "$INTERVAL"
done

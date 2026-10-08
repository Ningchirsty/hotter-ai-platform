#!/usr/bin/env bash
# =====================================================================
# 主机侧健康告警（cron 驱动）
# =====================================================================
# 为什么需要它（2026-10-08 实测）：
#   `production-health-alert.yml` 的 `*/5` cron **从未触发过**——扫 100 次运行，
#   event=schedule 的次数为 0；我另加了一个"只有 schedule"的最小 workflow，同样连续
#   错过 7 个窗口。本仓侧的排查已全部排除（文件在默认分支、state=active、公开、非 fork…），
#   所以**不能依赖 GitHub 的 scheduler**。而告警的内容通路本身是好的（建/关 issue 都验证过），
#   缺的只是"谁来定时点它"——这就是本脚本：由宿主机 cron 每 5 分钟执行。
#
# 另有一个**结构性**优点：这条路不依赖 self-hosted runner 能否取到 job，
# 因此 runner/网络层面的问题不再让告警一起消失。
# （它仍有盲区：整机宕机时它自己也发不出——那一层要用外部视角补，见 docs。）
#
# 用法：
#   bash health-alert.sh [--dry-run]        # --dry-run 只打印将要做的事，不发任何请求
#
# 配置（root-only，权限必须是 600，否则本脚本拒绝运行）：
#   /etc/hotter-alert/config
#     WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxx   # 二选一（推荐）
#     GH_TOKEN=github_pat_xxx                                        # 或：GitHub 细粒度令牌
#     GH_REPO=Ningchirsty/hotter-ai-platform
#     GH_LABEL=ops/health-alert
#     REPEAT_MINUTES=30            # 持续异常时最多每 30 分钟提醒一次（默认 30）
#
# 状态与日志：
#   /var/lib/hotter-alert/state        上次状态（ok / warn / critical / 上次通知时间）
#   /var/log/hotter-health-alert.log   追加日志（超过 LOG_MAX_KB 时自动截断）
#
# 退出码：0=正常（含"无需动作"）；2=已发出"异常"通知；3=配置缺失/不合法；
#         4=通知发送失败（检查结果本身仍是有效的）；1=其他错误
# =====================================================================
set -uo pipefail

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

CONFIG_FILE="${HOTTER_ALERT_CONFIG:-/etc/hotter-alert/config}"
STATE_DIR="${HOTTER_ALERT_STATE_DIR:-/var/lib/hotter-alert}"
STATE_FILE="$STATE_DIR/state"
LOG_FILE="${HOTTER_ALERT_LOG:-/var/log/hotter-health-alert.log}"
LOG_MAX_KB="${HOTTER_ALERT_LOG_MAX_KB:-512}"
HEALTH_CHECK="${HOTTER_ALERT_HEALTH_CHECK:-/opt/hotter-alert/health-check.sh}"
REPORT_FILE="${HOTTER_ALERT_REPORT:-$STATE_DIR/last-report.txt}"

log() {
  # 同时写日志与 stdout：cron 里 stdout 被丢弃，不影响；手工/--dry-run 时能直接看到判断过程。
  printf '%s %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >>"$LOG_FILE" 2>/dev/null || true
  printf '%s %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

mkdir -p "$STATE_DIR" 2>/dev/null || true
# 日志截断：只保留最后 200 行，避免无限增长（cron 场景没人管日志）
if [ -f "$LOG_FILE" ] && [ "$(stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)" -gt $((LOG_MAX_KB * 1024)) ]; then
  tail -n 200 "$LOG_FILE" >"$LOG_FILE.tmp" 2>/dev/null && mv "$LOG_FILE.tmp" "$LOG_FILE"
fi

# ---- 配置 ----
if [ ! -f "$CONFIG_FILE" ]; then
  log "SKIP: no config at $CONFIG_FILE (nothing to notify with)"
  exit 3
fi
perms=$(stat -c '%a' "$CONFIG_FILE" 2>/dev/null || echo "???")
if [ "$perms" != "600" ]; then
  log "REFUSE: $CONFIG_FILE mode is $perms, expected 600 (it holds a secret)"
  exit 3
fi
# shellcheck disable=SC1090
. "$CONFIG_FILE"
REPEAT_MINUTES="${REPEAT_MINUTES:-30}"

# ---- 巡检 ----
mkdir -p "$STATE_DIR" 2>/dev/null || true
rc=0
if [ ! -f "$HEALTH_CHECK" ]; then
  log "ERROR: health check not found: $HEALTH_CHECK"
  exit 1
fi
bash "$HEALTH_CHECK" >"$REPORT_FILE" 2>&1 || rc=$?
report=$(cat "$REPORT_FILE" 2>/dev/null || true)

case "$rc" in
  0) now_state=ok ;;
  1) now_state=critical ;;
  2) now_state=warn ;;
  *) now_state="unknown($rc)" ;;
esac

# 上次状态。注意：状态文件里的键是 `state=` / `notified=`，必须映射到 prev_* 上——
# 直接 source 后读 $prev_state 是空值，会导致"每次都判成首次异常"（重复提醒）
# 且**恢复通知永远不会发出**。这个 bug 是被 deploy/ha 自检脚本抓出来的。
prev_state="none"
prev_notified=0
if [ -f "$STATE_FILE" ]; then
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  prev_state="${state:-none}"
  prev_notified="${notified:-0}"
fi

now_epoch=$(date +%s)
since_min=$(( (now_epoch - ${prev_notified:-0}) / 60 ))

# ---- 决定动作 ----
action="none"
if [ "$now_state" = "ok" ]; then
  [ "$prev_state" = "ok" ] || [ "$prev_state" = "none" ] || action="recover"
else
  if [ "$prev_state" = "ok" ] || [ "$prev_state" = "none" ]; then
    action="raise"
  elif [ "$since_min" -ge "$REPEAT_MINUTES" ]; then
    action="repeat"
  fi
fi

if [ "$action" = "none" ]; then
  log "state=$now_state (prev=$prev_state), nothing to send"
  printf 'state=%s\nnotified=%s\n' "$now_state" "${prev_notified:-0}" >"$STATE_FILE"
  exit 0
fi

title="[production] health check $now_state"
if [ "$action" = "recover" ]; then
  title="[production] health check recovered"
fi
host=$(hostname 2>/dev/null || echo host)
body="$title
host: $host   utc: $(date -u '+%Y-%m-%dT%H:%M:%SZ')   rc=$rc   action=$action

$report"

# ---- 通知：webhook 优先（不需要 GitHub 凭据），否则用 GitHub issue ----
notify_webhook() {
  kind="text"
  case "${WEBHOOK_URL:-}" in
    *open.feishu.cn*|*feishu*|*larksuite*) payload=$(printf '{"msg_type":"text","content":{"text":%s}}' "$(json_str "$body")") ;;
    *qyapi.weixin.qq.com*|*wecom*|*dingtalk*) payload=$(printf '{"msgtype":"text","text":{"content":%s}}' "$(json_str "$body")") ;;
    *) payload=$(printf '{"text":%s}' "$(json_str "$body")") ;;
  esac
  if [ "$DRY_RUN" = 1 ]; then
    log "DRY: would POST $WEBHOOK_URL with ${#payload} bytes"
    echo "DRY: would POST webhook ($kind) ${#payload} bytes"
    return 0
  fi
  code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "$payload" "$WEBHOOK_URL" 2>/dev/null || echo 000)
  [ "$code" = "200" ] || { log "WEBHOOK FAILED http=$code"; return 1; }
  log "webhook sent (http=200)"
  return 0
}

# 极简 JSON 字符串转义（避免依赖 jq；报告里可能有引号/反斜杠/换行）
json_str() {
  printf '%s' "$1" | awk 'BEGIN{ORS=""} {gsub(/\\/,"\\\\"); gsub(/"/,"\\\""); printf "%s%s", sep, $0; sep="\\n"} END{printf ""}' \
    | awk 'BEGIN{printf "\""} {printf "%s", $0} END{printf "\""}'
}

# 取 JSON 里第一个 "number": N（GitHub 的 REST 响应是逐行缩进的美化 JSON，
# 因此按行解析即可；不引入 jq 依赖，避免"主机上有没有 jq"变成新的失效点）
json_number() { sed -n 's/.*"number": *\([0-9]\+\).*/\1/p' | head -1; }

gh_api() { # method path [json-body]
  method="$1"; path="$2"; data="${3:-}"
  if [ -n "$data" ]; then
    curl -s -m 20 -X "$method" \
      -H "Authorization: Bearer ${GH_TOKEN}" \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      -H 'User-Agent: hotter-health-alert' \
      -d "$data" "https://api.github.com$path"
  else
    curl -s -m 20 -X "$method" \
      -H "Authorization: Bearer ${GH_TOKEN}" \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      -H 'User-Agent: hotter-health-alert' \
      "https://api.github.com$path"
  fi
}

notify_github() {
  [ -n "${GH_TOKEN:-}" ] || return 1
  [ -n "${GH_REPO:-}" ] || return 1
  label="${GH_LABEL:-ops/health-alert}"
  if [ "$DRY_RUN" = 1 ]; then
    log "DRY: would query open issues with label $label and then $action"
    echo "DRY: would query issues(label=$label) and $action"
    return 0
  fi
  num=$(gh_api GET "/repos/${GH_REPO}/issues?state=open&labels=${label}&per_page=5" | json_number)
  body_json=$(printf '{"body":%s}' "$(json_str "$body")")
  if [ "$action" = "recover" ]; then
    if [ -n "$num" ]; then
      gh_api POST "/repos/${GH_REPO}/issues/${num}/comments" "$body_json" >/dev/null
      gh_api PATCH "/repos/${GH_REPO}/issues/${num}" '{"state":"closed","state_reason":"completed"}' >/dev/null
      log "issue #$num commented and closed"
    else
      log "no open issue to close"
    fi
    return 0
  fi
  if [ -n "$num" ]; then
    gh_api POST "/repos/${GH_REPO}/issues/${num}/comments" "$body_json" >/dev/null
    log "issue #$num commented"
  else
    title_json=$(printf '{"title":%s,"body":%s,"labels":["%s"]}' "$(json_str "$title")" "$(json_str "$body")" "$label")
    out=$(gh_api POST "/repos/${GH_REPO}/issues" "$title_json")
    newnum=$(printf '%s' "$out" | json_number)
    log "issue created${newnum:+ #$newnum}"
  fi
  return 0
}

sent=0
if [ -n "${WEBHOOK_URL:-}" ]; then
  notify_webhook && sent=1
elif [ -n "${GH_TOKEN:-}" ]; then
  notify_github && sent=1
else
  log "ERROR: neither WEBHOOK_URL nor GH_TOKEN configured; cannot notify"
  exit 3
fi

if [ "$sent" != 1 ]; then
  # 通知失败时**不改状态**，这样下一轮会立即重试（而不是等一个重复窗口）
  log "NOTIFY FAILED for action=$action; state left as $prev_state"
  exit 4
fi

printf 'state=%s\nnotified=%s\n' "$now_state" "$now_epoch" >"$STATE_FILE"
log "action=$action sent ok; state=$now_state"
if [ "$action" = "recover" ]; then exit 0; fi
exit 2

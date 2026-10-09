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
ISSUE_NUM=""
if [ -f "$STATE_FILE" ]; then
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  prev_state="${state:-none}"
  prev_notified="${notified:-0}"
  # 记下上次用的 issue 号：恢复时**直接用它**，而不是重新按 label 查。
  # 理由（2026-10-09 实测）：刚创建的 issue 可能还没被 label 过滤索引到，
  # 此时"按 label 查"会返回空 ⇒ 恢复路径报"no open issue to close"，留下一个永不关闭的告警。
  ISSUE_NUM="${issue:-}"
  LAST_HEARTBEAT="${heartbeat:-}"
fi

now_epoch=$(date +%s)
since_min=$(( (now_epoch - ${prev_notified:-0}) / 60 ))
today_utc=$(date -u '+%Y-%m-%d')
hour_utc=$(date -u '+%H')

# ---- 决定动作 ----
action="none"
if [ "$now_state" = "ok" ]; then
  if [ "$prev_state" != "ok" ] && [ "$prev_state" != "none" ]; then
    action="recover"
  # 每日心跳（dead-man's switch，默认关闭）：告警系统**自身**失效时，唯一能被察觉的方式是
  # "该来的消息没来"。所以配了 HEARTBEAT_HOUR（0-23，UTC）就每天在该小时的头一次运行发一条
  # 心跳；只发聊天通道（不往 issue 里灌日常噪音）。若连续多天收不到心跳，就说明 cron 或脚本坏了。
  elif [ -n "${HEARTBEAT_HOUR:-}" ] && [ "$hour_utc" = "$HEARTBEAT_HOUR" ] \
       && [ "${LAST_HEARTBEAT:-}" != "$today_utc" ]; then
    action="heartbeat"
  fi
else
  if [ "$prev_state" = "ok" ] || [ "$prev_state" = "none" ]; then
    action="raise"
  elif [ "$since_min" -ge "$REPEAT_MINUTES" ]; then
    action="repeat"
  fi
fi

if [ "$action" = "none" ]; then
  log "state=$now_state (prev=$prev_state), nothing to send"
  printf 'state=%s\nnotified=%s\nissue=%s\nheartbeat=%s\n' \
    "$now_state" "${prev_notified:-0}" "${ISSUE_NUM:-}" "${LAST_HEARTBEAT:-}" >"$STATE_FILE"
  exit 0
fi

title="[production] health check $now_state"
if [ "$action" = "recover" ]; then
  title="[production] health check recovered"
elif [ "$action" = "heartbeat" ]; then
  title="[production] health check alive (daily heartbeat)"
fi
host=$(hostname 2>/dev/null || echo host)
body="$title
host: $host   utc: $(date -u '+%Y-%m-%dT%H:%M:%SZ')   rc=$rc   action=$action

$report"

# 有些机器人平台要求消息里必须含某个"自定义关键词"，否则直接拒收（飞书/钉钉都有这一项）。
# 配了 WEBHOOK_KEYWORD 就把它放进每条消息里，避免"装了却发不出去"。
if [ -n "${WEBHOOK_KEYWORD:-}" ]; then
  body="$WEBHOOK_KEYWORD
$body"
fi

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
    # 打印正文预览：既能确认"关键词有没有被带上"，也让 --dry-run 真正可用。
    # 正文是巡检报告，不含任何密钥（只有容器名、计数与判定）。
    echo "DRY: payload preview: $(printf '%s' "$body" | head -c 220 | tr '\n' ' ')"
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
  num=""
  # 优先用状态文件里记下的 issue 号（避免"刚创建、label 尚未索引"的竞态）；
  # 状态丢失时再退回按 label 查。
  if [ -n "${ISSUE_NUM:-}" ]; then num="$ISSUE_NUM"; fi
  if [ -z "$num" ]; then
    num=$(gh_api GET "/repos/${GH_REPO}/issues?state=open&labels=${label}&per_page=5" | json_number)
  fi
  body_json=$(printf '{"body":%s}' "$(json_str "$body")")
  if [ "$action" = "recover" ]; then
    if [ -n "$num" ]; then
      # 确认它仍是 open 再关（已经关了就当无事，避免重复评论）
      st=$(gh_api GET "/repos/${GH_REPO}/issues/${num}" | sed -n 's/.*"state": *"\([a-z]*\)".*/\1/p' | head -1)
      if [ "$st" = "open" ]; then
        gh_api POST "/repos/${GH_REPO}/issues/${num}/comments" "$body_json" >/dev/null
        gh_api PATCH "/repos/${GH_REPO}/issues/${num}" '{"state":"closed","state_reason":"completed"}' >/dev/null
        log "issue #$num commented and closed"
      else
        log "issue #$num is already ${st:-unknown}; nothing to close"
      fi
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
    num=$(printf '%s' "$out" | json_number)
    log "issue created${num:+ #$num}"
  fi
  ISSUE_NUM="$num"
  return 0
}

sent=0
# 两个通道都配了就**都发**：聊天工具负责"有人会看到"，issue 负责"留痕 + 自动关闭"。
# 语义：只要有一个成功就算通知已送达（避免因为其中一个平台抖动而每 5 分钟重发）；
# 失败的通道写进日志，但不改变结论。
if [ -z "${WEBHOOK_URL:-}" ] && [ -z "${GH_TOKEN:-}" ]; then
  log "ERROR: neither WEBHOOK_URL nor GH_TOKEN configured; cannot notify"
  exit 3
fi
if [ -n "${WEBHOOK_URL:-}" ]; then
  if notify_webhook; then sent=1; else log "webhook notify failed (action=$action)"; fi
fi
# 心跳只走聊天通道：往 issue 里灌每日"我还活着"会变成噪音。心跳也不需要 GitHub 令牌。
if [ -n "${GH_TOKEN:-}" ] && [ "$action" != "heartbeat" ]; then
  if notify_github; then sent=1; else log "github issue notify failed (action=$action)"; fi
fi

if [ "$sent" != 1 ]; then
  # 通知失败时**不改状态**，这样下一轮会立即重试（而不是等一个重复窗口）
  log "NOTIFY FAILED for action=$action; state left as $prev_state"
  exit 4
fi

printf 'state=%s\nnotified=%s\nissue=%s\nheartbeat=%s\n' \
  "$now_state" "$(if [ "$action" = "heartbeat" ]; then echo "${prev_notified:-0}"; else echo "$now_epoch"; fi)" \
  "${ISSUE_NUM:-}" "$(if [ "$action" = "heartbeat" ]; then echo "$today_utc"; else echo "${LAST_HEARTBEAT:-}"; fi)" >"$STATE_FILE"
log "action=$action sent ok; state=$now_state"
if [ "$action" = "recover" ] || [ "$action" = "heartbeat" ]; then exit 0; fi
exit 2

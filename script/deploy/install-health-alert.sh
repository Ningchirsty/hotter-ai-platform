#!/usr/bin/env bash
# =====================================================================
# 安装 / 升级主机侧健康告警（需要 root）
# =====================================================================
# 幂等：可以反复运行（升级等于重新 copy 两个脚本 + 重写 cron）
# 用法：sudo bash script/deploy/install-health-alert.sh
#
# 装了什么：
#   /opt/hotter-alert/health-check.sh   巡检脚本（从仓库复制）
#   /opt/hotter-alert/health-alert.sh   告警脚本（从仓库复制）
#   /etc/hotter-alert/config            配置（0600，首次生成模板，含秘密）
#   /var/lib/hotter-alert/              状态与最近一次巡检报告
#   /var/log/hotter-health-alert.log    追加日志
#   /etc/cron.d/hotter-health-alert     每 5 分钟，flock 防叠加
#
# 为什么不直接用 self-hosted runner 的 checkout 路径：那是 gh-deploy 的临时目录
# （每次运行路径可能变化、由 CI 清理），不适合 root cron 长期引用。
# =====================================================================
set -euo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST=/opt/hotter-alert
CONF_DIR=/etc/hotter-alert
STATE_DIR=/var/lib/hotter-alert
CRON_FILE=/etc/cron.d/hotter-health-alert
LOCK=/var/lock/hotter-health-alert.lock

if [ "$(id -u)" != "0" ]; then
  echo "run with sudo: sudo bash $0" >&2
  exit 1
fi

for f in health-check.sh health-alert.sh; do
  test -f "$SRC_DIR/$f" || { echo "missing source: $SRC_DIR/$f" >&2; exit 1; }
done

install -d -m 0755 "$DEST"
if [ "$SRC_DIR" = "$DEST" ]; then
  # 从 /opt/hotter-alert 自身运行（本安装器也放一份在那里）：脚本已就位，
  # 只修正权限。**必须跳过 copy**——`install` 把一个文件拷到它自己身上会报错甚至截断。
  chmod 0750 "$DEST/health-check.sh" "$DEST/health-alert.sh"
  echo "sources already in place: $DEST"
else
  install -m 0750 "$SRC_DIR/health-check.sh" "$DEST/health-check.sh"
  install -m 0750 "$SRC_DIR/health-alert.sh" "$DEST/health-alert.sh"
  install -m 0750 "$SRC_DIR/install-health-alert.sh" "$DEST/install-health-alert.sh"
fi
install -d -m 0700 "$CONF_DIR"
install -d -m 0700 "$STATE_DIR"

if [ ! -f "$CONF_DIR/config" ]; then
  cat >"$CONF_DIR/config" <<'EOF'
# 主机侧健康告警配置（本文件含秘密，权限必须是 600）
#
# 二选一（webhook 优先）：填了 WEBHOOK_URL 就只用 webhook，否则用 GitHub issue。
# 飞书/企微/Slack 机器人地址都能自动识别格式。
#WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx

# 或者 GitHub 细粒度令牌（仅该仓库 Issues: Read and write）
#GH_TOKEN=github_pat_xxxxxxxx
#GH_REPO=Ningchirsty/hotter-ai-platform
#GH_LABEL=ops/health-alert

# 飞书/钉钉的自定义机器人常要求消息里含"自定义关键词"，否则拒收。
# 填了它，脚本会把关键词放进每条告警正文（配套 WEBHOOK_URL 使用）。
#WEBHOOK_KEYWORD=hotter

# 持续异常时最多多久提醒一次（分钟）
REPEAT_MINUTES=30
EOF
  chmod 600 "$CONF_DIR/config"
  echo "created $CONF_DIR/config  <-- fill in WEBHOOK_URL or GH_TOKEN"
else
  chmod 600 "$CONF_DIR/config"
  echo "kept existing $CONF_DIR/config"
fi

# 关键：**没有配置通知通道时不装 cron**。
# 否则会出现"看起来装了告警、其实发不出去"的假安全感——那比没装更糟。
# 判定：配置里存在未被注释的 WEBHOOK_URL= 或 GH_TOKEN=。
has_sink() {
  grep -Eq '^[[:space:]]*(WEBHOOK_URL|GH_TOKEN)=..*' "$CONF_DIR/config"
}

if has_sink; then
  cat >"$CRON_FILE" <<EOF
# 生产健康告警（2026-10-08 新增）：GitHub 的 schedule 触发器在本仓从未生效，
# 因此改由宿主机 cron 驱动。细节见 script/deploy/README.md。
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
*/5 * * * * root flock -n $LOCK $DEST/health-alert.sh >/dev/null 2>&1
EOF
  chmod 644 "$CRON_FILE"
  echo "installed cron: $CRON_FILE (every 5 minutes)"
else
  rm -f "$CRON_FILE"
  echo "NOT scheduled: $CONF_DIR/config has neither WEBHOOK_URL nor GH_TOKEN yet."
  echo "  -> fill one in (as root), then re-run this installer to enable the 5-minute cron."
fi

echo "installed:"
echo "  $DEST/health-check.sh  $DEST/health-alert.sh"
[ -f "$CRON_FILE" ] && echo "  $CRON_FILE"
echo
echo "dry run now:"
"$DEST/health-alert.sh" --dry-run || echo "  (dry run exit=$?)"

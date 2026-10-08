#!/usr/bin/env bash
# =====================================================================
# 镜像回收（保留策略）—— 2026-10-08 R73
# =====================================================================
# 为什么需要它：`hotter-release` 只清**容器**（previous-*）与**构建缓存**，
# 从不删**镜像**。而每次发布都会 `docker pull` 一个新镜像（约 1.2GB）：
# 实测主机上已累积 23 个后端镜像（≈27GB 中 20.43GB 可回收），
# 这正是 R61"根分区写满 → 部署与回滚一起失败"的同一成因，只是镜像这一层当时没被处理。
#
# ⚠️ 绝不能用 `docker image prune`：
#   宿主机上的镜像都是按 digest 拉取、当前无容器引用 —— 在 docker 眼里就是 dangling，
#   而它们正是**回滚锚点**（R61 教训：回滚需要本地镜像，且回滚也要写盘）。
#
# 保留规则（两条同时满足才删）：
#   ① **未被任何容器引用**（含已停止的 previous-* 容器 —— 它们是回滚路径）；
#   ② 不在"按 repo 最新 N 个"的保留集里（N 默认 6，见 HOTTER_KEEP_IMAGES）。
#
# 用法：
#   bash image-gc.sh [--dry-run|--apply]     # 默认 --dry-run（只报告，不删）
# 退出码：0=正常（含"无可回收"）；1=参数错；2=枚举失败（拒绝执行删除）
# =====================================================================
set -uo pipefail

MODE="${1:---dry-run}"
# 默认保留"每个 repo 最新 10 个"：
#   · 今天实测发布节奏极高（约 36 小时里 23 次发布），keep=6 只等于几小时的历史；
#   · keep=10 的稳态占用约 (10×1.2GB + 10×94MB) ≈ 13GB，主机 58GB 盘仍然宽裕；
#   · 更老的 digest 并非消失——GHCR 凭据已验证可用，需要时可按 digest 重新拉取。
KEEP="${HOTTER_KEEP_IMAGES:-10}"
REPOS="${HOTTER_IMAGE_REPOS:-ghcr.io/ningchirsty/hotter-ai-platform-backend ghcr.io/ningchirsty/hotter-ai-platform-frontend}"

case "$MODE" in
  --dry-run|--apply) ;;
  *) echo "usage: $0 [--dry-run|--apply]" >&2; exit 1 ;;
esac

# ---- ① 被任何容器引用的镜像（运行中 + 已停止）都要保留 ----
in_use_ids=$(
  docker ps -a --format '{{.Image}}' | sort -u | while read -r img; do
    [ -n "$img" ] || continue
    docker image inspect "$img" --format '{{.Id}}' 2>/dev/null
  done | sort -u
)
if [ -z "$in_use_ids" ]; then
  # 枚举失败时**不删**（宁可不清，也不要在信息不全时删回滚锚点）
  echo "REFUSE: could not enumerate container-referenced images" >&2
  exit 2
fi
in_use_count=$(printf '%s\n' "$in_use_ids" | wc -l | tr -d ' ')
echo "images referenced by containers: $in_use_count (always kept)"
echo "keep newest per repo: $KEEP   mode: $MODE"
echo

total_kb=0
deleted=0
for repo in $REPOS; do
  # 收集该 repo 的镜像：ID|CreatedAt（同一 ID 可能有多行，取最新）
  rows=$(docker images --no-trunc --format '{{.ID}}|{{.CreatedAt}}|{{.Repository}}' \
    | awk -F'|' -v r="$repo" '$3==r {print $1"|"$2}')
  if [ -z "$rows" ]; then
    echo "== $repo: no local images =="
    continue
  fi
  # 每个 ID 一行，按 CreatedAt 从新到旧
  uniq_rows=$(printf '%s\n' "$rows" | sort -t'|' -k2 -r | awk -F'|' '!seen[$1]++')
  count=$(printf '%s\n' "$uniq_rows" | wc -l | tr -d ' ')
  echo "== $repo: $count image(s) locally =="

  idx=0
  printf '%s\n' "$uniq_rows" | while IFS='|' read -r id created; do
    idx=$((idx + 1))
    short=$(printf '%s' "$id" | sed 's/^sha256://' | cut -c1-12)
    if printf '%s\n' "$in_use_ids" | grep -qx "$id"; then
      echo "   KEEP   $short  (referenced by a container)  $created"
      continue
    fi
    if [ "$idx" -le "$KEEP" ]; then
      echo "   KEEP   $short  (newest $KEEP)               $created"
      continue
    fi
    size=$(docker image inspect "$id" --format '{{.Size}}' 2>/dev/null || echo 0)
    size_kb=$((size / 1024))
    if [ "$MODE" = "--apply" ]; then
      if docker rmi "$id" >/dev/null 2>&1; then
        echo "   DELETE $short  (${size_kb}KB reclaimed)     $created"
      else
        echo "   SKIP   $short  (rmi refused: still in use?) $created"
      fi
    else
      echo "   WOULD DELETE $short (${size_kb}KB)          $created"
    fi
  done
  echo
done

echo "note: 删除的是**未被任何容器引用**且不在最新 $KEEP 之内的镜像；"
echo "      当前运行镜像与所有 previous-* 容器用到的镜像都在保留集内。"

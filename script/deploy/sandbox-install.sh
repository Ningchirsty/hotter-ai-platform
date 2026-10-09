#!/bin/bash
# ---------------------------------------------------------------------------
# hotter-sandbox 一次性安装：准备**定长的宿主 scratch** 与镜像白名单。
#
# 为什么需要这一步（而不是让 sandbox-run.sh 自己搞定）：
#   沙箱作业会往工作目录里写不可信内容。三种做法里只有一种是同时对：
#     ① 直接 bind 挂宿主目录      → **内存上限管不住磁盘**：实测 128m 内存限制下 dd 照样
#                                  写出 400MB（写文件走 page cache，不占容器内存）。
#                                  "把宿主机磁盘写满"正是 2026-10-08 那次生产事故的成因。
#     ② 容器内 tmpfs 当 /work     → 尺寸上限由内核强制（好），但 **tmpfs 随容器停止而消失**，
#                                  跑完 docker cp 什么都取不到（实测产物全丢）。
#     ③ 宿主侧定长文件系统 + 绑定挂载 → 尺寸由内核强制 **且** 产物在容器停止后仍在宿主机上。
#   因此用 ③：这里挂一个定长 tmpfs 作为 scratch，sandbox-run.sh 只在它内部开工。
#
# scratch 用 tmpfs 的取舍（写清楚，别当成免费的）：
#   · 它占内存：默认 1G，占宿主机 16G 中的 1G；写满即为上限，不会去吃磁盘。
#   · 宿主机重启后 scratch 清空——沙箱作业本来就是一次性的，产物应由 worker 尽快取走。
#   · 它与容器的 --memory **互不相干**：宿主挂载点不记在容器的内存 cgroup 上。
#
# 用法：sudo sandbox-install.sh [--size-mb 1024] [--allow <image> ...]
# 幂等：已挂载则跳过挂载；白名单按传入内容重写。
# ---------------------------------------------------------------------------
set -Eeuo pipefail

SIZE_MB="1024"
ROOT_DIR="/var/lib/hotter-sandbox"
SCRATCH="${ROOT_DIR}/work"
ALLOW_DIR="/etc/hotter-sandbox"
ALLOW_FILE="${ALLOW_DIR}/images.allow"
IMAGES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --size-mb) SIZE_MB="${2:-}"; shift 2 ;;
    --root) ROOT_DIR="${2:-}"; shift 2; SCRATCH="${ROOT_DIR}/work" ;;
    --allow) shift; while [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; do IMAGES+=("$1"); shift; done ;;
    -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

if [ "$(id -u)" != "0" ]; then
  echo "sandbox-install: 需要 root（挂载文件系统）" >&2
  exit 1
fi

mkdir -p "$ROOT_DIR" "$SCRATCH" "$ALLOW_DIR"

if mountpoint -q "$SCRATCH"; then
  echo "scratch 已挂载：$SCRATCH（跳过）"
else
  mount -t tmpfs -o "size=${SIZE_MB}m,mode=0755" tmpfs "$SCRATCH"
  echo "已挂载定长 scratch：$SCRATCH（size=${SIZE_MB}m）"
fi
# 作业子目录由 worker 创建；这里给一个共用的 jobs 目录并放开写权限，
# 让非 root 的 worker（如 aiadmin）能建自己的工作目录。
mkdir -p "${SCRATCH}/jobs"
chmod 1777 "${SCRATCH}/jobs"

if [ "${#IMAGES[@]}" -gt 0 ]; then
  : >"$ALLOW_FILE"
  for img in "${IMAGES[@]}"; do printf '%s\n' "$img" >>"$ALLOW_FILE"; done
  chmod 0644 "$ALLOW_FILE"
  echo "白名单已写入（${#IMAGES[@]} 条）：$ALLOW_FILE"
fi
if [ -f "$ALLOW_FILE" ]; then
  echo "当前白名单："; sed 's/^/  /' "$ALLOW_FILE"
else
  echo "注意：白名单还不存在（$ALLOW_FILE）——sandbox-run.sh 会拒绝运行任何作业。"
fi

echo "-- 自检 --"
df -h "$SCRATCH" | tail -1
ls -ld "$SCRATCH/jobs"
echo "SANDBOX_INSTALL_DONE"

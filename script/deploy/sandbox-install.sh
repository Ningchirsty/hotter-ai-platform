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
# 因此本脚本还要建一个**持久归档目录**（默认 /var/lib/hotter-sandbox/archive，在真磁盘上）：
#   worker 跑完作业会把 result.json、执行器日志与产物复制过去。第一版没有它时，
#   账本里只有产物的哈希，而产物本体只存在于 tmpfs —— 宿主一重启就只剩"曾经有过"的哈希。
#
# 用法：sudo sandbox-install.sh [--size-mb 1024] [--archive-root DIR] [--allow <image> ...]
# 幂等：已挂载则跳过挂载；白名单按传入内容重写；归档目录已存在则只纠正权限。
# ---------------------------------------------------------------------------
set -Eeuo pipefail

SIZE_MB="1024"
ROOT_DIR="/var/lib/hotter-sandbox"
SCRATCH="${ROOT_DIR}/work"
ARCHIVE="${ROOT_DIR}/archive"
ALLOW_DIR="/etc/hotter-sandbox"
ALLOW_FILE="${ALLOW_DIR}/images.allow"
IMAGES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --size-mb) SIZE_MB="${2:-}"; shift 2 ;;
    --root) ROOT_DIR="${2:-}"; shift 2; SCRATCH="${ROOT_DIR}/work"; ARCHIVE="${ROOT_DIR}/archive" ;;
    --archive-root) ARCHIVE="${2:-}"; shift 2 ;;
    --allow) shift; while [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; do IMAGES+=("$1"); shift; done ;;
    -h|--help) sed -n '2,/^set -Eeuo pipefail$/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
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

# 持久归档目录：**必须在真磁盘上**（不是那个 tmpfs）。
# 归属给调用者（sudo 时是 SUDO_USER）：worker 通常以该用户运行，产物直接归它，不需要事后特权 chown。
mkdir -p "$ARCHIVE"
ARCHIVE_OWNER="${SUDO_USER:-root}"
if id "$ARCHIVE_OWNER" >/dev/null 2>&1; then
  chown "$ARCHIVE_OWNER" "$ARCHIVE"
fi
chmod 0755 "$ARCHIVE"

# 归档与 scratch 必须在**不同**文件系统上：同一个文件系统意味着"归档"其实也在 tmpfs 里，
# 宿主一重启连归档一起没——那正是这一步存在的理由。
if [ "$(stat -c %d "$SCRATCH")" = "$(stat -c %d "$ARCHIVE")" ]; then
  echo "警告：归档目录与 scratch 在同一个文件系统上（$ARCHIVE）——归档会在宿主重启时一起丢失。"
  echo "      请把 --archive-root 指到真磁盘上（worker 会在每个作业的 archive.json 里记 durable=false）。"
else
  echo "持久归档：$ARCHIVE（owner=$ARCHIVE_OWNER，与 scratch 不同文件系统）"
fi

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
df -h "$ARCHIVE" | tail -1
ls -ld "$SCRATCH/jobs" "$ARCHIVE"
echo "SANDBOX_INSTALL_DONE"

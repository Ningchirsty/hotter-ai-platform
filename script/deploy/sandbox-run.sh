#!/bin/bash
# ---------------------------------------------------------------------------
# hotter-sandbox 执行器：把一段不可信命令放进一个**受限的一次性容器**里跑，并把产物带出来。
#
# 它为什么必须是宿主侧独立进程（而不是平台后端的一部分）：
#   生产实测：平台后端容器**既没有 /var/run/docker.sock、也没有 docker CLI**，
#   宿主机上只有 aiadmin / gh-deploy 在 docker 组。这是对的，且必须保持——
#   "docker-socket" 就在平台自己的禁用工具表里（§6.2-1），包体扫描器也把它的特征串
#   当成拒绝理由。若为了让后端能开容器而把 socket 给它，等于让治理平台持有它自己禁止的能力。
#   因此按 ADR-001 的口径：**不可信执行隔离在独立进程与容器**，本脚本是那个独立进程的执行体。
#
# 隔离强度（每一条都是硬约束，不是"注意"）：
#   --network none                 默认无网（要网必须显式 --allow-network，且属 F-09 待定策略）
#   --read-only                    根文件系统只读；只给 /tmp 与 /work 两个 tmpfs/挂载点
#   --cap-drop ALL                 丢掉全部 Linux capabilities
#   --security-opt no-new-privileges  禁止 setuid 提权
#   --pids-limit                   防 fork 炸弹
#   --memory / --memory-swap 相等   内存硬上限，且不给交换（否则上限形同虚设）
#   --cpus                          CPU 配额
#   --ulimit nofile/nproc          文件描述符与进程数上限
#   --user                         **非 root** 运行（默认取本进程 uid，见下方说明）
#   不使用 --privileged，不挂宿主机路径（除作业工作目录），不共享 pid/network 命名空间
#
# 时间上限有两层：容器内的 `timeout` 与宿主侧的 `timeout --signal=KILL`；后者保证即使
# 容器内的进程忽略信号，整次执行也会被终止并如实记为 timedOut。
#
# 用法：
#   sandbox-run.sh --image <ref> --cmd '<shell command>' --work-dir <host dir> [选项]
# 选项：
#   --cpus N          默认 1
#   --mem MB          默认 512
#   --pids N          默认 128
#   --timeout S       默认 120
#   --allow-network   允许出网（默认 none）
#   --allowlist FILE  镜像白名单（默认 /etc/hotter-sandbox/images.allow）
#   --job-id ID       结果里的作业标识（默认按时间生成）
# 输出：stdout 上**只有一行 JSON**（结果）；容器自身的输出落在工作目录的 stdout/stderr 文件里。
# 退出码：0 = 容器跑完（含非零退出，业务失败在 JSON 里）；非 0 = 执行器自身拒绝或出错。
# ---------------------------------------------------------------------------
set -Eeuo pipefail

IMAGE=""
CMD=""
WORK_DIR=""
CPUS="1"
MEM_MB="512"
PIDS="128"
TIMEOUT_S="120"
ALLOW_NETWORK=0
ALLOWLIST="/etc/hotter-sandbox/images.allow"
JOB_ID="job-$(date +%Y%m%d%H%M%S)-$$"
# 并发上限：宁可排队也不要让突发把宿主机打满（8 核 / 12GB 可用，见 R99 实测）。
# 首版固定 1，等有真实负载测量之后再调；用 flock 做互斥，拿不到就直接拒绝而不是排队等死。
LOCK_FILE="${SANDBOX_LOCK_FILE:-/tmp/hotter-sandbox.lock}"
ALLOW_CONCURRENT="${SANDBOX_ALLOW_CONCURRENT:-0}"

usage() {
  sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
}

die() { echo "sandbox-run: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --image) IMAGE="${2:-}"; shift 2 ;;
    --cmd) CMD="${2:-}"; shift 2 ;;
    --work-dir) WORK_DIR="${2:-}"; shift 2 ;;
    --cpus) CPUS="${2:-}"; shift 2 ;;
    --mem) MEM_MB="${2:-}"; shift 2 ;;
    --pids) PIDS="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_S="${2:-}"; shift 2 ;;
    --allow-network) ALLOW_NETWORK=1; shift ;;
    --allowlist) ALLOWLIST="${2:-}"; shift 2 ;;
    --job-id) JOB_ID="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) die "未知参数：$1" ;;
  esac
done

[ -n "$IMAGE" ] || die "必须给 --image"
[ -n "$CMD" ] || die "必须给 --cmd"
[ -n "$WORK_DIR" ] || die "必须给 --work-dir"

# 工作目录必须存在、是目录、且**不**是危险路径。
# 另外必须落在定长 scratch 内（见 sandbox-install.sh）：
#   · 直接 bind 挂宿主普通目录 ⇒ 作业能把宿主机磁盘写满（内存上限管不住），
#     而那正是 2026-10-08 生产事故的成因；
#   · 容器内 tmpfs ⇒ 尺寸虽然被内核强制，但随容器停止消失，跑完取不到产物（实测产物全丢）。
#   ⇒ 只有"宿主侧定长文件系统 + 绑定挂载"同时满足"有上限"与"产物留得下"。
# 测试可以用 SANDBOX_SCRATCH_ROOT 指到别处，但默认必须是安装脚本建立的那个挂载点。
SCRATCH_ROOT="${SANDBOX_SCRATCH_ROOT:-/var/lib/hotter-sandbox/work}"
if [ ! -d "$WORK_DIR" ]; then die "--work-dir 不存在或不是目录：$WORK_DIR"; fi
ABS_WORK="$(cd "$WORK_DIR" && pwd -P)"
case "$ABS_WORK" in
  /|/etc|/usr|/var|/root|/home|/opt|/run|/sys|/proc|/boot|/dev)
    die "--work-dir 落在受保护路径上，拒绝：$ABS_WORK" ;;
esac
SCRATCH_ABS="$(cd "$SCRATCH_ROOT" 2>/dev/null && pwd -P || echo "$SCRATCH_ROOT")"
case "$ABS_WORK" in
  "$SCRATCH_ABS"/*) : ;;
  *) die "--work-dir 必须在定长 scratch 内（$SCRATCH_ABS），实际 $ABS_WORK。
     请先跑 sandbox-install.sh 建立 scratch；否则作业能把宿主机磁盘写满。" ;;
esac
# scratch 必须是独立挂载点，否则"定长"是假的（只是普通目录，照样能写满宿主盘）
if command -v mountpoint >/dev/null 2>&1 && ! mountpoint -q "$SCRATCH_ABS"; then
  die "scratch 不是挂载点（$SCRATCH_ABS）：定长上限不成立，拒绝运行。请跑 sandbox-install.sh。"
fi

# 镜像白名单：默认只允许运维显式列出的镜像。这不只是"防手滑"——
# 没有它，任何能改作业参数的人都等于能拉取并运行任意镜像。
if [ ! -f "$ALLOWLIST" ]; then
  die "镜像白名单不存在（$ALLOWLIST）：先由运维写下允许的镜像 ref，每行一个。"
fi
if ! grep -Fxq -- "$IMAGE" "$ALLOWLIST"; then
  die "镜像不在白名单里：$IMAGE（白名单 $ALLOWLIST）"
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  die "本地没有该镜像：$IMAGE（沙箱不自行拉取远端镜像）"
fi

# 非 root 运行：默认用本进程的 uid/gid，这样产物直接归作业账户所有，不需要事后特权 chown。
# 如果执行器本身以 root 跑，则退到 65534（nobody），绝不 root-in-container。
RUN_UID="$(id -u)"
RUN_GID="$(id -g)"
if [ "$RUN_UID" = "0" ]; then RUN_UID=65534; RUN_GID=65534; fi

NET_ARGS=(--network none)
if [ "$ALLOW_NETWORK" = "1" ]; then NET_ARGS=(--network bridge); fi

# 容器内的时间上限比宿主侧略短，让容器有机会自己收尾
INNER_TIMEOUT=$(( TIMEOUT_S > 2 ? TIMEOUT_S - 2 : TIMEOUT_S ))

# 并发互斥：宁可排队也不要让突发把宿主机打满（8 核 / 12GB 可用，见 R99 实测）。
# 首版固定 1，等有真实负载测量之后再调；用 flock 做互斥，拿不到就直接拒绝而不是排队等死。
exec 9>"$LOCK_FILE"
if [ "$ALLOW_CONCURRENT" != "1" ]; then
  if ! flock -n 9; then die "已有沙箱作业在执行（并发上限 1），本次拒绝"; fi
fi

STDOUT_FILE="$ABS_WORK/.sandbox-stdout.log"
STDERR_FILE="$ABS_WORK/.sandbox-stderr.log"
START_MS=$(date +%s%3N)
CONTAINER="hotter-sandbox-${JOB_ID}"

# 为什么不用 `docker run --rm -v ...:/work`（第一版就是那样，被实测推翻）：
#   /work 若是宿主普通目录的绑定挂载，**内存上限管不住磁盘**——实测 128m 内存限制下 dd 照样
#   写出 400MB（写文件走 page cache，不占容器内存）；而"把宿主机磁盘写满"正是 2026-10-08
#   那次生产事故的成因。先用 0.5s 轮询补救也不行：300MB 在 387ms 内就写完了，轮询根本来不及。
#   ⇒ 最终做法：宿主侧**定长 scratch**（sandbox-install.sh 挂的 tmpfs）+ 把它绑定挂载进容器。
#   内核在 scratch 写满时给 ENOSPC（硬上限），而产物写在宿主挂载点上、容器停止后仍在，
#   不需要 docker cp（tmpfs 当 /work 时容器一停内容就没了——实测产物全丢）。
docker create --name "$CONTAINER" \
  "${NET_ARGS[@]}" \
  --read-only \
  --tmpfs "/tmp:rw,noexec,nosuid,nodev,size=64m" \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --pids-limit "$PIDS" \
  --memory "${MEM_MB}m" --memory-swap "${MEM_MB}m" \
  --cpus "$CPUS" \
  --ulimit nofile=256:256 \
  --user "${RUN_UID}:${RUN_GID}" \
  -e HOTTER_SANDBOX_CMD="$CMD" \
  -v "$ABS_WORK:/work:rw" \
  -w /work \
  "$IMAGE" \
  sh -lc "timeout -s KILL ${INNER_TIMEOUT}s sh -lc \"\$HOTTER_SANDBOX_CMD\"" \
  >/dev/null 2>"$STDERR_FILE"
CREATE_RC=$?
if [ "$CREATE_RC" != "0" ]; then
  echo "sandbox-run: docker create 失败（rc=$CREATE_RC）：$(tail -1 "$STDERR_FILE" 2>/dev/null)" >&2
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  exit 3
fi

set +e
# 宿主侧 timeout 兜底（容器内已有一层）。注意：杀掉的是 docker CLI，所以下一步必须显式清容器。
timeout --signal=KILL "${TIMEOUT_S}s" docker start -a "$CONTAINER" \
  >"$STDOUT_FILE" 2>>"$STDERR_FILE"
RC=$?
set -e
END_MS=$(date +%s%3N)
DURATION_MS=$(( END_MS - START_MS ))

TIMED_OUT=false
# 124 = timeout 自己超时；137 = 被 KILL（128+9，含 OOM）
if [ "$RC" = "124" ] || [ "$RC" = "137" ]; then TIMED_OUT=true; fi

# 产物已经在宿主挂载点上（不需要 docker cp）；只清容器。
# 不清理容器的话，一个死循环作业会安静地留在机器上占着资源。
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
chmod -R a+rX "$ABS_WORK" 2>/dev/null || true
# 容器内以 $RUN_UID 跑，取出来的文件可能属于别的 uid；这里只保证可读（不 chown）
chmod -R a+rX "$ABS_WORK" 2>/dev/null || true

# 产物清单：只列工作目录里的常规文件，排除执行器自己的日志；逐个算 sha256。
ARTIFACTS="[]"
if command -v sha256sum >/dev/null 2>&1; then
  ARTIFACTS="$(
    cd "$ABS_WORK"
    find . -type f ! -name '.sandbox-*' -printf '%P\n' | LC_ALL=C sort | while IFS= read -r f; do
      size=$(stat -c %s -- "$f")
      hash=$(sha256sum -- "$f" | cut -d' ' -f1)
      printf '{"path":"%s","bytes":%s,"sha256":"%s"}' "$f" "$size" "$hash"
    done | paste -sd, - | sed 's/^/[/; s/$/]/'
  )"
  [ -n "$ARTIFACTS" ] || ARTIFACTS="[]"
fi

# 结果：**只有一行 JSON**，便于调用方解析；不把容器输出混进来（那是文件里的东西）。
printf '{"jobId":"%s","image":"%s","exitCode":%s,"timedOut":%s,"durationMs":%s,"network":"%s","scratchFreeMb":%s,"artifacts":%s}\n' \
  "$JOB_ID" "$IMAGE" "$RC" "$TIMED_OUT" "$DURATION_MS" \
  "$([ "$ALLOW_NETWORK" = "1" ] && echo bridge || echo none)" \
  "$(df -Pm "$SCRATCH_ABS" 2>/dev/null | awk 'NR==2{printf "%d", $4}')" \
  "$ARTIFACTS"

# 执行器视角的成功 = 容器跑完；容器自身非零退出由调用方按 exitCode 判断（业务失败不是执行器故障）
exit 0

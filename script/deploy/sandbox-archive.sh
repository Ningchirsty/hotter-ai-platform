#!/bin/bash
# ---------------------------------------------------------------------------
# hotter-sandbox 归档运维工具：看有哪些作业归档了、某个作业产出了什么、以及**产物是否还对得上**。
#
# 为什么需要它：
#   归档目录是人能看懂的文件树，但"某个 jobId 到底产出了什么、有没有被改过"要逐个算哈希。
#   而登记门槛时最该做的一步恰恰是：**登记前先确认手上这份 result.json 与产物仍然一致**
#   （产物是宿主上的普通文件，人为改动不会有任何提示）。
#
# 用法：
#   sandbox-archive.sh list                    # 列出归档的作业
#   sandbox-archive.sh show <jobId>            # 看某个作业的记录与产物清单
#   sandbox-archive.sh verify <jobId>          # 逐个重算产物 sha256，与 result.json 里声明的对比
#   sandbox-archive.sh verify <jobId> --scratch # 同时对比 scratch 里那份（若还在）
#   sandbox-archive.sh export <jobId> [--out FILE] [--force]
#                                              # 把某个作业的产物打包成可交付的 tar.gz（含清单）
# 选项：
#   --archive-root DIR   默认 /var/lib/hotter-sandbox/archive
#   --jobs-root DIR      默认 /var/lib/hotter-sandbox/work/jobs（verify --scratch 用）
#
# 退出码：list/show 恒为 0（除非作业不存在）；**verify 有不一致时为 1**，便于接进脚本。
# ---------------------------------------------------------------------------
set -Eeuo pipefail

ARCHIVE_ROOT="${SANDBOX_ARCHIVE_ROOT:-/var/lib/hotter-sandbox/archive}"
JOBS_ROOT="${SANDBOX_JOBS_ROOT:-/var/lib/hotter-sandbox/work/jobs}"
CMD=""
JOB=""
WITH_SCRATCH=0
OUT=""
FORCE=0

usage() { sed -n '2,/^set -Eeuo pipefail$/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 2; }
die() { echo "sandbox-archive: $*" >&2; exit 2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --archive-root) ARCHIVE_ROOT="${2:-}"; shift 2 ;;
    --jobs-root) JOBS_ROOT="${2:-}"; shift 2 ;;
    --scratch) WITH_SCRATCH=1; shift ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) usage ;;
    list|show|verify|export)
      [ -z "$CMD" ] || die "只能给一个子命令（已给 $CMD）"
      CMD="$1"; shift
      # show/verify/export 需要 jobId；list 不需要
      if [ "$CMD" != "list" ] && [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; then JOB="$1"; shift; fi
      ;;
    *) die "未知参数：$1" ;;
  esac
done

[ -n "$CMD" ] || usage
command -v jq >/dev/null 2>&1 || die "缺 jq"
[ -d "$ARCHIVE_ROOT" ] || die "归档目录不存在：$ARCHIVE_ROOT"

case "$CMD" in
  list)
    printf '%-34s %-19s %-11s %7s %10s %s\n' JOBID ARCHIVED_AT STATUS FILES BYTES DURABLE
    found=0
    while IFS= read -r d; do
      found=1
      a="$d/archive.json"
      if [ -f "$a" ]; then
        jq -r '[.jobId, (.archivedAt // "-"), (.status // "-"), (.artifactFiles // 0),
                (.artifactBytes // 0), (if .durable == true then "yes" else "NO" end)]
               | @tsv' "$a" \
        | awk -F'\t' '{printf "%-34s %-19s %-11s %7s %10s %s\n", $1, $2, $3, $4, $5, $6}'
      else
        # 没有 archive.json：只会是人为放进去的目录，或早期版本的归档——如实标出来
        printf '%-34s %-19s %-11s %7s %10s %s\n' "$(basename "$d")" "-" "NO_MANIFEST" "-" "-" "-"
      fi
    done < <(find "$ARCHIVE_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%T@ %p\n' \
               | sort -rn | awk '{print $2}')
    [ "$found" = "1" ] || echo "（归档为空）"
    ;;

  show)
    [ -n "$JOB" ] || die "show 需要 jobId"
    d="$ARCHIVE_ROOT/$JOB"
    [ -d "$d" ] || die "没有这个作业的归档：$d"
    echo "== archive.json =="
    [ -f "$d/archive.json" ] && cat "$d/archive.json" || echo "(缺 archive.json)"
    echo
    echo "== result.json 摘要 =="
    if [ -f "$d/result.json" ]; then
      jq '{jobId, status, image, exitCode, timedOut, network, durationMs, artifactCount,
           agentCode, versionId, startedAt, endedAt, error}' "$d/result.json"
      echo
      echo "== 产物清单（result.json 里声明的） =="
      jq -r '.artifacts // [] | .[] | "  \(.path)  \(.bytes) bytes  \(.sha256)"' "$d/result.json" \
        | sed 's/^/ /' || echo "  （没有产物）"
    else
      echo "(缺 result.json)"
    fi
    ;;

  verify)
    [ -n "$JOB" ] || die "verify 需要 jobId"
    d="$ARCHIVE_ROOT/$JOB"
    [ -d "$d" ] || die "没有这个作业的归档：$d"
    [ -f "$d/result.json" ] || die "归档里缺 result.json，无法核对"
    bad=0
    total=0
    echo "== 逐个产物重算 sha256（与 result.json 声明的对比） =="
    while IFS=$'\t' read -r path bytes sha; do
      [ -n "$path" ] || continue
      total=$((total + 1))
      f="$d/artifacts/$path"
      if [ ! -f "$f" ]; then
        echo "  MISSING  $path（归档里没有这个文件）"; bad=$((bad + 1)); continue
      fi
      real_bytes=$(stat -c %s -- "$f")
      real_sha=$(sha256sum -- "$f" | cut -d' ' -f1)
      if [ "$real_sha" != "$sha" ]; then
        echo "  MISMATCH $path（声明 $sha / 实际 $real_sha）"; bad=$((bad + 1)); continue
      fi
      if [ "$real_bytes" != "$bytes" ]; then
        echo "  SIZE-DIFF $path（声明 $bytes / 实际 $real_bytes）"; bad=$((bad + 1)); continue
      fi
      echo "  OK       $path  $bytes bytes"
    done < <(jq -r '.artifacts // [] | .[] | [.path, (.bytes|tostring), .sha256] | @tsv' "$d/result.json")

    # 归档这份 result.json 自身是否与 archive.json 记的哈希一致（有没有人动过记录）
    if [ -f "$d/archive.json" ]; then
      want=$(jq -r '.resultSha256 // empty' "$d/archive.json")
      got=$(sha256sum "$d/result.json" | cut -d' ' -f1)
      total=$((total + 1))
      if [ -n "$want" ] && [ "$want" != "$got" ]; then
        echo "  MISMATCH result.json（archive.json 记 $want / 实际 $got）"; bad=$((bad + 1))
      else
        echo "  OK       result.json（未被改动）"
      fi
    fi

    if [ "$WITH_SCRATCH" = "1" ]; then
      echo "== 与 scratch 里那份对比（若还在） =="
      s="$JOBS_ROOT/$JOB"
      if [ -d "$s" ]; then
        if [ -f "$s/result.json" ]; then
          a=$(sha256sum "$d/result.json" | cut -d' ' -f1)
          b=$(sha256sum "$s/result.json" | cut -d' ' -f1)
          total=$((total + 1))
          if [ "$a" = "$b" ]; then echo "  OK       result.json 两边一致"; else echo "  MISMATCH result.json 两边不同（归档 $a / scratch $b）"; bad=$((bad + 1)); fi
        else
          echo "  （scratch 里已无 result.json）"
        fi
      else
        echo "  （scratch 里已无该作业目录 —— 这正是归档存在的意义）"
      fi
    fi

    echo
    if [ "$bad" = "0" ]; then
      echo "核对通过：$total 项全部一致"
      exit 0
    fi
    echo "核对失败：$total 项里有 $bad 项对不上"
    exit 1
    ;;
  export)
    # 把一个作业的产物打包成可交付的 tar.gz。
    # 为什么需要：产物是宿主上的普通文件，交给别人（评审/业务/事故复盘）需要确定的包 + 一份能独立
    # 核对的清单；手工 tar 容易漏清单，事后说不清包里是什么。
    # 刻意不做：**不往任何地方上传**——归档目录不是分发渠道，出网策略（F-09）尚未冻结；
    # 平台侧的产物登记（制品账本/对象存储）还没做，那是下一步。
    [ -n "$JOB" ] || die "export 需要 jobId"
    d="$ARCHIVE_ROOT/$JOB"
    [ -d "$d" ] || die "没有这个作业的归档：$d"
    [ -f "$d/result.json" ] || die "归档里缺 result.json"
    [ -n "$OUT" ] || OUT="./$JOB.tar.gz"
    if [ -e "$OUT" ] && [ "$FORCE" != "1" ]; then die "输出已存在：$OUT（要覆盖请加 --force）"; fi
    # 先核对再打包：把"对不上"的东西交出去比不交出去更糟
    if ! "$0" --archive-root "$ARCHIVE_ROOT" verify "$JOB" >/dev/null 2>&1; then
      echo "sandbox-archive: 归档自检未通过，拒绝导出（先跑 verify $JOB 看是哪一项）" >&2
      exit 1
    fi
    tmp="${OUT}.tmp.$$"
    tar -czf "$tmp" -C "$ARCHIVE_ROOT" "$JOB"
    mv -f "$tmp" "$OUT"
    files=$(jq -r '.artifactFiles // 0' "$d/archive.json" 2>/dev/null || echo "?")
    bytes=$(jq -r '.artifactBytes // 0' "$d/archive.json" 2>/dev/null || echo "?")
    echo "已导出：$OUT"
    echo "  作业 $JOB：产物 $files 个 / $bytes 字节（已通过 verify 自检）"
    echo "  包 sha256：$(sha256sum "$OUT" | cut -d' ' -f1)"
    echo "  包大小：$(stat -c %s "$OUT") 字节"
    echo "  包内条目数：$(tar -tzf "$OUT" | wc -l)"
    echo "  用途：交给评审或业务；**平台侧登记（制品账本/对象存储）尚未实现**"
    ;;
esac

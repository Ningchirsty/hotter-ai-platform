#!/usr/bin/env bash
#
# 预取 ffmpeg 静态归档到 build-cache/，供 Dockerfile 直接 COPY。
#
# 为什么需要这一步：johnvansickle.com 会对单个连接限速（实测同一台机器上
# 每次请求 20 秒只能收到 0.8–7 MB，全程 41.9 MB 经常超时），而 curl 的
# --retry 不会对「传输太慢」重试。把这 41.9 MB 放在构建的每一步里重复下载，
# 等于把镜像构建的成败押在一个不稳定的第三方源上——实际已因此让 CI 失败过两次。
#
# 因此改为：CI 里下载一次并缓存，Dockerfile 只负责 COPY。
# 本脚本自带重试与完整性校验，且**校验失败即失败**，绝不产出可疑文件。
#
# 用法：bash script/ci/fetch-ffmpeg.sh [输出目录]
set -euo pipefail

OUT_DIR="${1:-build-cache}"
URL="${FFMPEG_URL:-https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz}"
SHA256="${FFMPEG_SHA256:-abda8d77ce8309141f83ab8edf0596834087c52467f6badf376a6a2a4c87cf67}"
EXPECTED_BYTES="${FFMPEG_EXPECTED_BYTES:-41888096}"
TARGET="$OUT_DIR/ffmpeg-release-amd64-static.tar.xz"

mkdir -p "$OUT_DIR"

if [ -f "$TARGET" ] \
   && [ "$(stat -c %s "$TARGET" 2>/dev/null || stat -f %z "$TARGET")" = "$EXPECTED_BYTES" ] \
   && echo "${SHA256}  ${TARGET}" | sha256sum -c - >/dev/null 2>&1; then
  echo "已存在且校验通过，跳过下载：$TARGET"
  exit 0
fi

# 单次尝试的时长上限放宽到 900 秒：限速下 41.9 MB 约需 100+ 秒；
# 低于 50 KB/s 持续 60 秒即判失败，换连接重试（限速是每连接生效的）。
for attempt in 1 2 3 4 5; do
  echo "第 ${attempt} 次尝试下载…"
  rm -f "$TARGET"
  if curl -fsSL \
        --connect-timeout 30 --max-time 900 \
        --speed-limit 51200 --speed-time 60 \
        -o "$TARGET" "$URL" \
     && [ "$(stat -c %s "$TARGET" 2>/dev/null || stat -f %z "$TARGET")" = "$EXPECTED_BYTES" ] \
     && echo "${SHA256}  ${TARGET}" | sha256sum -c -; then
    echo "下载并校验通过：$TARGET（$EXPECTED_BYTES 字节）"
    exit 0
  fi
  echo "第 ${attempt} 次失败，稍后重试…" >&2
  sleep 15
done

echo "连续 5 次下载或校验失败，放弃。可用 FFMPEG_URL/FFMPEG_SHA256 指向内网镜像。" >&2
rm -f "$TARGET"
exit 1

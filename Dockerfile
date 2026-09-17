# 若依后端镜像（构建上下文 = 仓库根）
#
# 为什么放在仓库根、而不用 ruoyi-admin/Dockerfile：
#   视频创作模块的契约与工作流模板在仓库根的 `script/video/workflows/` 下，
#   运行时由 `WorkflowContractRegistry` 从 `VIDEO_CONTRACT_ROOT`（默认 `script`）读取。
#   Docker 不允许 COPY 构建上下文之外的文件，而 `ruoyi-admin/.dockerignore` 又把
#   上下文限制为「仅 Dockerfile + target/ruoyi-admin.jar」，导致 `../script` 既越界又被忽略。
#   因此必须把上下文改为仓库根，并在根目录放 .dockerignore 控制发送内容。
#
# CI 调用方式：
#   docker build --pull -f Dockerfile --tag "$IMAGE" .        # 注意上下文是 `.`

# 贝尔实验室 Spring 官方推荐镜像 JDK下载地址 https://bell-sw.com/pages/downloads/
FROM bellsoft/liberica-openjdk-rocky:21.0.12-cds
# FROM bellsoft/liberica-openjdk-rocky:25.0.4-cds
# FROM findepi/graalvm:java21-native

LABEL maintainer="Lion Li"

# 视频创作模块需要对成片做 ffprobe 实测与 ffmpeg 截断：
# ComfyUI 的 SaveVideo 不返回宽高/时长，而 H3 模板固定产出 124 帧@24fps = 5.167 秒，
# 超过产品 5 秒上限；没有这两个工具就无法判定超时、也无法填写真实分辨率。
#
# 为什么不装发行版包：本镜像基于 Rocky Linux 9，且该基础镜像**没有 dnf/yum**（只有 microdnf），
# Rocky 默认源也没有 ffmpeg（实测 `microdnf install ffmpeg[-free]` 报 No package matches）。
# 因此改用**静态构建**，它不依赖发行版与 glibc 版本（实测可在本镜像内直接运行）。
#
# 如需离线/内网构建，用 --build-arg 覆盖为内网镜像地址与对应校验值即可。
ARG FFMPEG_URL=https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz
ARG FFMPEG_SHA256=abda8d77ce8309141f83ab8edf0596834087c52467f6badf376a6a2a4c87cf67
ARG FFMPEG_EXPECTED_BYTES=41888096

# 基础镜像有 curl 与 tar，但没有 xz 命令：GNU tar 的 -J 会调用外部 xz 程序，
# 因此先从基础源装上 xz（Rocky 9 基础源可用，实测），再解包。
RUN set -eux; \
    microdnf install -y xz; \
    microdnf clean all; \
    curl -fsSL --retry 3 --retry-delay 2 -o /tmp/ffmpeg.tar.xz "$FFMPEG_URL"; \
    echo "DIAG size=$(stat -c %s /tmp/ffmpeg.tar.xz) expected=$FFMPEG_EXPECTED_BYTES" >&2; \
    echo "DIAG sha256=$(sha256sum /tmp/ffmpeg.tar.xz | cut -d' ' -f1)" >&2; \
    echo "DIAG head=$(head -c 120 /tmp/ffmpeg.tar.xz | od -c | head -4 | tr '\n' '|')" >&2; \
    echo "${FFMPEG_SHA256}  /tmp/ffmpeg.tar.xz" | sha256sum -c -; \
    mkdir -p /opt/ffmpeg; \
    tar -xJf /tmp/ffmpeg.tar.xz -C /opt/ffmpeg --strip-components=1 \
        --wildcards '*/ffmpeg' '*/ffprobe' '*/GPLv3.txt'; \
    install -m 0755 /opt/ffmpeg/ffmpeg /usr/local/bin/ffmpeg; \
    install -m 0755 /opt/ffmpeg/ffprobe /usr/local/bin/ffprobe; \
    rm -rf /tmp/ffmpeg.tar.xz /opt/ffmpeg; \
    ffprobe -version | head -1; \
    ffmpeg -version | head -1

RUN mkdir -p /ruoyi/server/logs \
    /ruoyi/server/temp \
    /ruoyi/skywalking/agent

WORKDIR /ruoyi/server

ENV SERVER_PORT=8080 SNAIL_JOB_PORT=28080 SNAIL_AI_PORT=38080 LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""

# 显式声明 PATH 与 JAVA_HOME：不依赖基础镜像是否自带这些变量。
# 若基础镜像的 Env 为空（例如用 `docker export` 快照本地兜底构建时），
# 缺少 JDK 目录会导致容器以 127（command not found）启动失败——这是实际踩过的坑。
ENV JAVA_HOME=/usr/lib/jvm/jdk-21.0.12.1-bellsoft-x86_64 \
    PATH=/usr/lib/jvm/jdk-21.0.12.1-bellsoft-x86_64/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

EXPOSE ${SERVER_PORT}
# 暴露 snail 客户端端口 用于定时任务调度中心通信
EXPOSE ${SNAIL_JOB_PORT}
EXPOSE ${SNAIL_AI_PORT}

ADD ./ruoyi-admin/target/ruoyi-admin.jar ./app.jar

# 工作流契约与 API Format 模板必须在镜像内：
# 后端启动时从 VIDEO_CONTRACT_ROOT（默认 script）读取并校验 SHA-256，
# 缺失会导致上下文初始化失败。
COPY ./script/video/workflows /ruoyi/server/script/video/workflows

SHELL ["/bin/bash", "-c"]

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} \
           -Dsnail-job.port=${SNAIL_JOB_PORT} \
           -Dsnail-ai.port=${SNAIL_AI_PORT} \
           # 应用名称 如果想区分集群节点监控 改成不同的名称即可
           #-Dskywalking.agent.service_name=ruoyi-server \
           #-javaagent:/ruoyi/skywalking/agent/skywalking-agent.jar \
           -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} \
           -jar app.jar

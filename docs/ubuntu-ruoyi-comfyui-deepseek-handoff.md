# Ubuntu 服务器若依 × ComfyUI 联调执行交接（给 DeepSeek）

> 目标：在有 ComfyUI 工作流的 Ubuntu/Linux 服务器上，验证集团 AI 创作平台的若依前端、若依后端和三种 MiniMax H3 视频工作流，并补齐从页面提交到成片回显所缺的代码。此文档是执行任务说明，不是联调已通过的证明。
>
> 仓库：`https://github.com/Ningchirsty/hotter-ai-platform`；目标分支：`feature/frontend-plus-ui-v6`。执行时记录远端实际完整 Git SHA；编写本文时已核对远端 HEAD 为 `95da9d6a6a02f1cd3c853ab3b3700e7e0e36b948`，不能据此假定远端始终不变。

## 1. 任务边界和当前事实

最终必须验证的链路是：浏览器登录和视频创作页面 → `/prod-api/` → 若依鉴权后的任务/素材接口 → 服务端选择并填充受控 ComfyUI 模板 → ComfyUI 排队、执行及输出 → 服务端保存任务状态、素材和成片 → “我的任务”与“素材库”显示真实数据。

当前 GitHub 分支**不能直接完成上述闭环**：

- `frontend/src/views/video/index.vue` 有“创建任务 / 我的任务 / 素材库”三个视图和文生视频、图生视频、首尾帧生视频三种创建模式，但提交按钮禁用；任务为内存示例，选中文件只记录文件名，素材库也无持久化。
- `script/video/workflows/api/wf-{t2v,i2v,fl2v}-h3-v0.1.0.json` 为清理后的 API Format 模板；`script/video/workflows/video-workflow-contracts.json` 和 `import-h3.mjs` 是契约与字段映射参考。三个工作流仍为 `DRAFT`，没有运行服务自动调用它们。
- `script/sql/ry_video_workflow.sql` 仅是工作流版本表的目标结构，未包含完整视频任务/素材持久化实现。现有 `ruoyi-modules/ruoyi-ai` 的 SnailAI 功能不等于视频任务服务。
- `docs/04-data-model-and-task-state.md`、`docs/07-workflow-onboarding-checklist.md` 被现有文件引用，但目前不在此仓库；不得声称已经按这两份文档验收。
- `.github/workflows/ci.yml` 与 `frontend-ci.yml` 构建、测试及镜像冒烟；只有**仓库默认分支**的 push 发布 GHCR 镜像。功能分支需要在目标机自行构建，或使用该分支 `workflow_dispatch` 产物；CI 绿色不等于应用已连接数据库或 ComfyUI。

请先报告服务器的实际状态，再执行对应路径。不要把仓库中的 `script/docker/docker-compose.yml` 当作本项目的可用 POC 部署配置：它是上游通用示例。生产部署工作流依赖服务器私有的 `/opt/ai-video-poc/compose.yaml`、`.env`、自托管 Runner、已有数据库/Redis 数据卷及 `/usr/local/sbin/hotter-release`，这些运行时资源不随 GitHub 仓库部署到新服务器。生产部署的受控入口见 `script/deploy/README.md`，联调优先使用隔离环境，不要绕过它直接替换生产服务。

## 2. Ubuntu 盘点与代码基线

在服务器上运行以下只读检查，记录输出中的版本、服务、端口及文件是否存在；**不要向报告粘贴 `.env`、令牌、数据库密码或完整 `docker inspect` 环境变量**：

```bash
uname -a
docker version --format '{{.Server.Version}}'
docker compose version
java -version
node --version
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
test -f /opt/ai-video-poc/compose.yaml && echo 'POC compose exists' || echo 'POC compose missing'
test -s /opt/ai-video-poc/.env && echo 'POC env exists' || echo 'POC env missing'
```

若这台机已部署 ComfyUI，确认实际 API 地址、监听范围、GPU、节点插件和模型文件；若在另一个容器或主机，确认**后端容器到它的网络路径**，不能以浏览器能访问代替后端可访问。MySQL、Redis、对象存储也须确认现有实例和数据是否需要保留。不得为了测试覆盖已有数据库、模型目录或容器。记录现有备份/回滚办法。

在可写的工作目录检出指定分支，记录 SHA；如果已有仓库，先检查工作区状态，不能覆盖未提交更改：

```bash
git clone --branch feature/frontend-plus-ui-v6 https://github.com/Ningchirsty/hotter-ai-platform.git
cd hotter-ai-platform
git status --short --branch
git rev-parse HEAD
git remote -v
```

如 `git clone` 因私有仓库认证失败，使用服务器已有的只读 GitHub 凭据或请操作者提供授权；不要在命令、文档或日志中写明文 token。

## 3. 复现构建并启动若依基线

所需工具：JDK 21、Python 3、Docker/Compose、可访问依赖源的网络；宿主机运行映射测试还需要 Node 22，实际成片验收需要 `ffprobe`。前端 Dockerfile 自带 Node 22/pnpm。与 GitHub CI 相同地执行后端测试，再构建两个**来自同一 SHA**的本地镜像：

```bash
bash mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=false '-Dtest.groups=!exclude' verify
python3 script/ci/check-test-results.py
node --test script/video/workflows/import-h3.test.mjs
git_sha=$(git rev-parse HEAD)
docker build --pull -f Dockerfile -t "hotter-ai-platform-backend:${git_sha}" .
docker build --pull --build-arg "BUILD_SHA=${git_sha}" \
  -t "hotter-ai-platform-frontend:${git_sha}" frontend
```

构建失败应记录首个真实错误并修复原因，不得通过跳过测试掩盖失败。后端 JAR 来源是 `ruoyi-admin/target/ruoyi-admin.jar`。当前前端**生产镜像**使用 `frontend/nginx.production.conf.template`，默认监听 80，由 `LISTEN_PORT` 控制监听端口，`BACKEND_ORIGIN` 控制 `/prod-api/` 的后端地址；`frontend/nginx.conf` 是另一份静态配置，不是此镜像的生产入口。必须在拟使用的网络模式下验证后端地址实际可达。`/version.json` 必须包含通过 `BUILD_SHA` 写入的完整 SHA。

**已有生产 POC 编排**：在不打印密钥的前提下检查 `/opt/ai-video-poc/compose.yaml`、`.env`、容器名、数据卷和服务网络，并由有权限的管理员运行：

```bash
cd /opt/ai-video-poc
docker compose --env-file .env -f compose.yaml config --quiet
docker volume inspect ai-video-poc-mysql-data ai-video-poc-redis-data >/dev/null
```

当前生产发布脚本要求 root 拥有运行时配置、现有数据卷，并只接受 GHCR 的不可变 digest；它通过服务健康检查及版本检查后切换，并在失败时尝试回滚代码。前端生产容器名为 `ruoyi-web`，使用 host 网络，监听 80，代理到宿主机 `127.0.0.1:18082`；发布前会用 18083 检查候选镜像。**不要将功能分支的本地镜像直接塞进生产 Compose，亦不要手动删除生产容器。**在相同 Ubuntu 服务器验证功能分支时，另建隔离的 Compose/容器、数据库与端口；确需发布正式环境时，先按 `script/deploy/README.md` 审核和准备默认分支 GHCR digest，再由现有 `deploy-poc.yml` / `deploy-frontend-poc.yml` 调用受控入口。前端发布还需填写镜像内 `/version.json` 的完整 SHA。

**新服务器、没有私有编排，或现有生产机上的隔离联调环境**：在服务器**私有位置**建立适合实际环境的 Compose 与 `.env`，不要提交密钥到 Git。至少包括数据库、Redis、若依后端、前端，及按实际部署方式访问的 ComfyUI/素材存储；已有基础设施可复用，但联调数据不能覆盖生产数据。根据实际网络模式设置前端 `LISTEN_PORT` 和 `BACKEND_ORIGIN`，并验证 `/version.json` 与 `/prod-api/`；不要沿用仅适配生产 host 网络的默认后端地址。为后端配置正确的 Spring `prod` 数据源、Redis 地址与密码、对象存储和必要的安全配置。容器中的 `localhost` 是容器自身，不能用仓库 `application-prod.yml` 的演示连接值去连接其他容器。检查 `docker compose config --quiet`、启动日志与实际健康检查，并只在内网暴露 MySQL、Redis、存储和 ComfyUI。

若依数据库应先有对应版本的基础表（参考 `script/sql/ry_vue.sql`）；根据目标库真实情况备份后，选择 `script/sql/ry_video_menu.sql` **或** `script/sql/ry_video_menu_migration.sql`，不可对已存在的菜单重复 INSERT。迁移脚本的 `COMMIT` 有意注释，须先检查所选行和角色可见性；不要机械执行可选的工作流表改名。`ry_video_workflow.sql` 目前只是目标 DDL，不会使任务接口自动出现。

基线验收：后端进程正常、数据库和 Redis 可连接；前端页面可通过服务器提供的地址打开并登录；浏览器中的 `/prod-api/` 请求到达该若依后端；有权限的用户可从若依左侧菜单打开视频创作页面。`/actuator/health` 可作为辅助检查，但应以实际认证/网络配置解释 401、403、502 或连接失败，不能只看镜像结构冒烟结果。此阶段的页面仍会显示禁用提交与示例任务，**不得标记端到端完成**。

## 4. ComfyUI 三个模板的实机验证

从后端所在网络核实 ComfyUI API 地址（下面的 URL 仅是同机进程示例，不假定端口必为 8188；Docker 容器内的 `127.0.0.1` 不指向 Ubuntu 宿主机）：

```bash
COMFY_URL=http://127.0.0.1:8188
curl --fail --silent --show-error "$COMFY_URL/system_stats" >/dev/null
curl --fail --silent --show-error "$COMFY_URL/object_info" >/dev/null
```

逐份检查 `wf-t2v-h3`、`wf-i2v-h3`、`wf-fl2v-h3`：读取契约中的 `apiJsonFile`、`checksum`、字段映射和输出规则；比对服务器的节点 class、模型与 LoRA 文件是否齐全。特别检查 `MiniMaxH3Director`、`LoraLoaderModelOnly`、`PathchSageAttentionKJ` 以及模板实际要求的其余自定义节点，缺少依赖时记录精确错误和安装版本，不要随意改写已确认校验和的模板。工作流模板和节点 ID 只保存在服务端，浏览器不能获取或提交原始图。

使用**获准用于测试**的文字、首帧与尾帧样本：图片先按 ComfyUI 上传协议进入它可读取的输入目录；按 `mapping` 白名单深拷贝并填入提示词/文件名，按 ComfyUI API 提交图、记录 `prompt_id`，轮询历史/队列，解析输出并用 `ffprobe` 检查视频尺寸、帧率和实际时长。分别记录成功/失败、耗时、显存、输出路径和脱敏日志。此步骤是 ComfyUI 单侧验收，不能代替从若依页面发起的联调。

目前模板目标为 1920×1080、24 fps、124 帧；124/24 约 5.17 秒。产品当前仅允许 **高清 1080P，最多 5 秒**。必须测量成片，必要时在后端输出环节可靠截断至不超过 5 秒并验证音视频一致性。720P/10 秒、480P/20 秒虽在 UI 中展示，但 H3 模板未交付这些档位；不要声称它们可提交。其他模型也仍为占位。

## 5. 补齐代码并进行真正的端到端联调

在目标分支上按仓库现有后端/前端模式实现最小闭环，提交前逐项检查：

1. 任务与素材的数据库结构、迁移及用户/租户隔离；工作流版本由服务端受控文件与校验和加载，只有实机验收通过的版本才允许发布。任务的排队、执行、成功、失败、超时和重试状态可持久化。
2. 若依鉴权下的素材上传/查询与任务创建、列表、详情、取消或失败查询接口；输入白名单及 `workflowCode`、模型、时长、输出档位在服务端校验。文件内容、大小、类型与文件访问权限都要校验，返回素材 ID，不能只传浏览器本地文件名。
3. 服务端通过素材 ID 获取可供 ComfyUI 访问的文件，深拷贝 API Format 模板，仅写入契约 `mapping` 指明的字段；调用 ComfyUI、保存 `prompt_id`、监控队列/历史，处理网络失败与任务去重，归档成片和封面，更新任务状态。不能让浏览器直接访问 ComfyUI 或传任意节点图。
4. 将 `frontend/src/views/video/index.vue` 的静态任务和素材替换为真实 API 数据，上传返回素材 ID，正式环境的提交按钮只对已发布且已验证的 H3 工作流开放；隔离的联调环境可通过受控测试权限验证 `TESTING` 工作流。保留文生、图生、首尾帧三个模式与现有若依菜单。后端未通过时应显示明确状态，不得用演示任务冒充真实任务。
5. 补充有意义的后端及前端测试，覆盖三种合法 payload、用户隔离、非法 workflowCode/字段、缺失图片、ComfyUI 故障与超时、输出超 5 秒的处理；先用可控的 ComfyUI 替身做接口测试，再进行 GPU 实机验收。重新执行 CI 对应命令和前端 `pnpm lint`、`pnpm build:prod`，随后部署**同一提交**的前后端镜像联调。

若选择新增模块、表或接口，先阅读仓库对应 controller/service/mapper、前端 API 和生成器模板，保持 RuoYi-Vue-Plus 约定。契约可以在实现时细化，但必须同步后端验证、前端安全视图和迁移脚本；不要把历史 `ZCODE-HANDOFF.md` 中的旧 `ai/studio` 路径当作当前路径。

在隔离联调环境将已完成单侧验收的三份工作流设为 `TESTING`，用真实测试用户从浏览器逐个提交任务，确认状态变化、成片可预览/下载、素材库可复用、刷新页面数据仍在，并在另一个用户账号下确认不能看到或下载前一个用户的素材。输出视频实测满足 1080P 且不超过 5 秒、端到端测试通过后，才将对应工作流经审核推进为 `PUBLISHED` 并开放正式环境提交。没有后端实现、GPU 可用性或输出证据时，请如实报告阻塞，不能改前端文案伪装为已联通。代码变更应在目标功能分支提交并与 GitHub 同步；不要提交服务器私有配置，也不要把未经验证的工作流发布到正式环境。

## 6. 交付给操作者的报告

报告须包含：服务器拓扑和已有/新建编排的区别、Git SHA、构建与测试结果、数据库/菜单迁移记录、每个工作流的节点/模型/输出实测表、三条浏览器到成片的任务 ID 与时长/分辨率、失败原因与修复提交、仍未开放的档位/模型、镜像标签或 digest、回滚方式。日志和截图须脱敏；不要发送 `.env`、密码、令牌、私钥或用户原始素材。未执行的步骤标为“未验证”，不能写成“通过”。

## 核对入口

- `frontend/src/views/video/index.vue`、`frontend/src/views/video/modules.ts`：当前界面和安全视图。
- `script/video/workflows/README.md`、`video-workflow-contracts.json`、`api/*.json`、`import-h3.mjs`：模板与映射。
- `script/sql/ry_vue.sql`、`ry_video_menu.sql`、`ry_video_menu_migration.sql`、`ry_video_workflow.sql`：数据库基线与目标结构。
- `ruoyi-admin/src/main/resources/application-prod.yml`、仓库根 `Dockerfile` 与 `.dockerignore`：后端运行配置与镜像（根上下文构建，负责安装静态 ffmpeg 并复制 `script/video/workflows` 契约）。
- `frontend/.env.production`、`frontend/nginx.production.conf.template`、`frontend/Dockerfile`：前端生产构建、版本校验和 API 代理。
- `.github/workflows/ci.yml`、`frontend-ci.yml`、`deploy-poc.yml`、`deploy-frontend-poc.yml`、`script/deploy/README.md`：CI 与受控生产部署前置条件。

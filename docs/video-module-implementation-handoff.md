# 视频创作模块实现进展与交接（DeepSeek → 下一执行者）

日期：2026-09-16；目标分支：`feature/frontend-plus-ui-v6`；执行机：`ai-edge-sz`（Ubuntu 24.04.5 LTS，192.168.2.134）

本文记录本次执行到**什么程度**、**哪些已实测通过**、**哪些未做**，以及环境上踩过的坑。
未执行的项一律标为「未验证」，不得当成通过。

---

## 1. 实际服务器状态（§2 盘点结论）

| 项 | 实测值 |
|---|---|
| 主机 | `ai-edge-sz`，Ubuntu 24.04.5 LTS，kernel 6.8.0-139，8 vCPU / 15 GiB / 根分区 58 G（可用 37 G） |
| Docker | 29.8.0，Compose v5.5.1 |
| JDK | OpenJDK **21**.0.12（符合要求） |
| Node | v22.14.0（符合要求） |
| `ffprobe` / `ffmpeg` | **均缺失**（`dpkg` 计数 0） |
| `sudo` | aiadmin 在 sudo 组，**需要密码** |
| GPU | 本机无 `nvidia-smi` → **不是 GPU 节点** |
| 生产编排 | `/opt/ai-video-poc/`（`root:gh-deploy 750`，含 `compose.yaml`、`.env`、`sql/`）；`/usr/local/sbin/hotter-release` 存在但 750 不可读 |
| 数据库 | `ai_video_poc`，执行前 97 张表；用户 `admin`/`test`/`test1` |
| 生产后端 | `SPRING_PROFILES_ACTIVE=prod`，`127.0.0.1:18082→8080`，含 `sai_*`(SnailAI) 表与 `aichat`/`snailai`/`workflow/task` 菜单 |
| 前端容器 | `ruoyi-web`，host 网络，监听 80 |
| 运行中容器 | `ai-video-poc-{backend,mysql,redis}-1`、`ruoyi-web`、`ai-edge-gateway-mvp`、`ai-edge-test`、`omfy-{mysql,redis}` |

### 与文档不符之处（已实测纠正）

1. **GPU 地址过时**：`docs/11` 记录的 `192.168.10.20:8188` 从本机 **100% 丢包、8188 不通**。
   实际地址是 **`192.168.2.223:8188`**，且从 `.134` 可达（22 与 8188 均开放）。
2. **`docs/04`、`docs/07` 不在 GitHub 仓库**，但存在于服务器
   `/opt/ai-edge/ruoyi-platform/comfyui-platform-main/docs/`（完整 00–12 共 13 份）。
3. **平台版本分叉**：服务器另有一套完整可用 MVP
   （`/opt/ai-edge/ruoyi-platform/comfyui-platform-main`，RuoYi-Vue-Plus **v5.6.2** + Java 17），
   与本次目标 hotter-ai-platform **v6.0.0** 是两条不同代码线，不可混用。
4. **生产镜像来源**：GHCR 上的 tag 为 `sha-5e42c7fc…`、`sha-5dab2570…`，而运行中的后端是
   `7afb23b39bde`，三者互不相同——生产镜像并非当前 feature 分支构建。

---

## 2. 代码基线（§2）

- 目标分支 HEAD：**`e4136653731afe30790ae2d3455e1c2b06328776`**
- 文档所载 `95da9d6a…` 是该 HEAD 的**父提交**；文档写作后又多了一个提交（即文档本身）。
- **GitHub 从 `.134` 不可达**（`GnuTLS recv error (-110)`，TLS 被中断）；Maven Central 与
  npm registry 均可达（200）。因此服务器上的仓库是通过 **git bundle 离线传输**建立的：
  `git bundle create --all` → base64 分块 → 服务器 `base64 -d` → `git clone <bundle>`。
  克隆后 `git rev-parse HEAD` 与本地一致，校验通过。

## 3. 数据库迁移（已执行并校验）

生产库 `ai_video_poc`（操作者确认在生产库执行）：

| 脚本 | SHA-256（LF） | 结果 |
|---|---|---|
| `script/sql/ry_video_menu.sql` | `03bece88558ad7ef6f38df317e2d34549aa1c933c058c50e3b909ac5b9fe5944` | 插入菜单 2 行 |
| `script/sql/ry_video_workflow.sql` | `c9c898cb9864fa4fb052e96d84f2e80e659a567fdfde82351e6dce209e159086` | 建表 `video_workflow_version`（20 列） |
| `script/sql/ry_video_task.sql`（本次新增） | `f6f7596a587d7ce0ee6b7423174f2dad29b18e5db397d19ae5a39d13704ea986` | 建表 3 张 |

执行前的**预检结论**（决定了必须用 `ry_video_menu.sql` 而非 `ry_video_menu_migration.sql`）：

- `menu_id` 920000 / 920001 **空闲**，无 `video:creation%` 权限冲突；
- `component='ai/studio/index'` 行为 **0** → migration 脚本的 UPDATE 不会命中，故不适用；
- `sys_menu` 列清单与 INSERT 列清单**完全一致**；
- `create_dept=1761000000000000103`、`create_by=1761100000000000001` 均存在。

执行结果：

| 项 | 变化 |
|---|---|
| `sys_menu` | +2 行（`920000` 视频创作 / `920001` 提交视频任务），156 → 158 |
| `sys_role_menu` | +2 行（授予角色 `1761300000000000003` = test1，该角色已持有 AI 菜单） |
| `video_workflow_version` | 新建，20 列，**0 行种子数据**（契约导入程序尚未实现） |
| `video_task` / `video_asset` / `video_task_event` | 新建，36 / 20 / 7 列 |
| 总表数 | 97 → **101** |

备份：`/tmp/sys_menu_backup_20260916-145357.sql`（33994 字节）。
菜单中文名经 hex 校验为合法 UTF-8（`视频创作`、`提交视频任务`）。

> 注意：`order_num=6` 与现有顶级菜单 `1761400000000011616` 相同，仅影响排序显示，
> 不构成约束冲突。

## 4. ComfyUI 实机核验（§4，已实质完成）

ComfyUI 位于 **`192.168.2.223:8188`**：版本 `0.35.0`，Python 3.10.12，**PyTorch 2.11.0+cu130**，
A100-SXM4-80GB（启动参数含 `--cuda-device 1`），队列空闲。共 **1843** 个节点类。

- **三个 H3 自定义节点全部存在**：`MiniMaxH3Director`、`LoraLoaderModelOnly`、`PathchSageAttentionKJ`
  （另有 `SaveVideo`、`LoadImage`）。
- **模板引用的 5 个权重全部交叉核验通过**：

| 模板引用 | ComfyUI 中的位置 | 结果 |
|---|---|---|
| `minimax_h3_fl2va_bf16.safetensors` | UNETLoader | OK |
| `qwen3vl_32b_minimax_h3_nvfp4_awq.safetensors` | CLIPLoader | OK |
| `minimax_h3_video_vae_fp16.safetensors` | VAELoader | OK |
| `minimax_h3_audio_vae_fp32.safetensors` | VAELoader | OK |
| `minimax_h3_turbo_v4_step600_ema.safetensors` | LoraLoaderModelOnly | OK |

- 三个模板的 SHA-256 与契约 `checksum` **逐字节一致**：
  `wf-t2v-h3 620e53c9…`、`wf-i2v-h3 fd6ce1f4…`、`wf-fl2v-h3 ec9c817d…`。
- 模板固定参数核对：`task_type` 分别为 `t2v/i2v/fl2v`；1920×1088、24 fps、**124 帧**、
  `steps=8`、`cfg=1`、`res_multistep/simple`、输出节点 `7 = SaveVideo`。
- `/history` 中存在一次真实 H3 执行记录，说明该链路此前跑通过。

**未验证**：成片的实测尺寸/帧率/时长。原因：该机**没有 ffprobe/ffmpeg**，而交接文档要求用
`ffprobe` 度量。GPU 主机 `192.168.2.223` 的 22 端口开放，可在其本机完成度量。

## 5. 构建与测试基线（§3，全部通过）

在服务器工作区 `/opt/ai-edge/ruoyi-platform/hotter-ai-platform`：

```
bash mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=false '-Dtest.groups=!exclude' verify
python3 script/ci/check-test-results.py
node --test script/video/workflows/import-h3.test.mjs
```

| 门禁 | 结果 |
|---|---|
| 完整 reactor 构建 | **BUILD SUCCESS**（33 模块；冷缓存 19.4 s，热缓存 6.2 s） |
| 后端测试 | **44 executed / 1 skipped / 0 failures / 0 errors**（6 份 surefire 报告） |
| `check-test-results.py` | exit 0 |
| Node 工作流测试 | **3 tests / 3 pass / 0 fail** |
| 产物 | `ruoyi-admin/target/ruoyi-admin.jar`（245 M） |

> 关键坑：父 POM 默认 `maven.test.skip=true`，**必须显式 `-Dmaven.test.skip=false`**，
> 否则测试会被静默跳过并向上述脚本报告「0 个报告」。另外单独构建 `-pl ruoyi-modules/ruoyi-ai`
> 时必须加 `-am`，否则兄弟模块依赖无法解析。

### 前端门禁（与实际 CI `frontend-ci.yml` 一致）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 依赖安装 | `pnpm install --frozen-lockfile` | OK（仓库用 **pnpm 10.34.5**，`pnpm-lock.yaml`） |
| Lint | `pnpm lint`（oxlint） | **0 warnings / 0 errors**，240 文件 / 156 规则 |
| 生产构建 | `pnpm build:prod` | **✓ built in 4.03s**，无 error/warning，产出 `video-DP6PSIu_.js` 等分块 |
| 镜像构建 | `docker build --build-arg BUILD_SHA=$(git rev-parse HEAD) -t hotter-ai-platform-frontend:$SHA frontend` | 成功，89.7 MB |
| 镜像冒烟 | 容器内 `wget /version.json` 断言含完整 SHA | **SMOKE PASS**：`{"gitSha":"e4136653731afe30790ae2d3455e1c2b06328776"}` |
| 代理配置 | `nginx.production.conf.template` 的 `/prod-api/` | 实测 `proxy_pass http://127.0.0.1:18082/;`，与生产约定一致 |

> 注意：服务器**没有 `corepack`**，需用 `npx --yes pnpm@10.34.5` 代替 CI 的
> `corepack prepare`。另外 `docker pull nginx:1.27-alpine` 曾报 Docker Hub 拒连
> （`connection refused`），重试后成功；构建出的镜像根层与 `nginx:1.27-alpine` 一致，
> 即 Dockerfile 声明的版本。若再次遇到，可先 `docker tag` 已有本地镜像兜底。

## 6. 本次实现的代码（§5 后端，已编译并通过测试）

模块 `ruoyi-modules/ruoyi-ai`，包 `org.dromara.ai.video`：

| 文件 | 职责 |
|---|---|
| `domain/VideoCapability.java` | I2V/T2V/FL2V 能力编码与 task_type 前缀 |
| `domain/VideoTaskStatus.java` | 状态机（QUEUED→RUNNING→终态；终态不可流转） |
| `domain/WorkflowMapping.java`、`WorkflowVersion.java` | 契约映射与版本定义 |
| `exception/VideoTaskException.java` | 带 `errorCode` 的脱敏业务异常 |
| `service/H3TemplatePreparer.java` | **深拷贝模板、只覆写 mapping 白名单、校验和守卫、固定档位校验** |
| `service/WorkflowContractRegistry.java` | 从后端受控目录加载契约，校验失败的模板一律不加载 |
| `service/AssetStorage.java`、`LocalFileAssetStorage.java` | 素材存储；存储键按租户/用户分目录，防路径穿越 |
| `service/VideoTaskRepository.java`、`JdbcVideoTaskRepository.java` | **每条查询都显式带 tenant_id + user_id** |
| `service/VideoTaskOrchestrator.java` | 可达性预检 → 上传素材 → 填充 → 提交 → 轮询 → 归档 → 记事件；输出超限标记截断 |
| `comfy/ComfyClient.java`、`ComfyOutput.java`、`HttpComfyClient.java` | ComfyUI 上传/提交/历史/下载；**默认拒绝把回环地址当 ComfyUI 地址** |
| `config/VideoModuleConfiguration.java` | `video.*` 配置装配；默认 `video.enabled=false` |
| `controller/VideoCreationController.java` | 素材上传/列表/删除、任务创建/执行/列表/详情/取消；`@SaCheckPermission` 鉴权 |
| `script/sql/ry_video_task.sql` | 任务/素材/事件三张表 |

前端接入（新增/修改）：

| 文件 | 职责 |
|---|---|
| `frontend/src/api/video/types.ts`（新增） | 与控制器一一对应的 TS 类型；**只含能力编码/字段白名单，不含节点 ID 与模板** |
| `frontend/src/api/video/index.ts`（新增） | 9 个接口封装：工作流视图、素材上传/列表/删除、任务创建/执行/列表/详情/取消 |
| `frontend/src/views/video/index.vue`（修改） | 移除全部示例数据；接真实 API；文件选择后**立即上传换素材 ID**；提交按钮由服务端 `status` 决定可用性并显示真实原因 |

测试（`src/test/java/org/dromara/ai/video/`，共 23 个用例，全部通过）：

- `H3TemplatePreparerTest`（15）：契约加载、**模板被改动后拒绝加载**、T2V/I2V/FL2V 填充、
  **采样参数与模型路径不被覆写**、深拷贝语义、缺图拒绝、720P/10 秒拒绝、空提示词拒绝、
  字段白名单拒绝、能力不匹配拒绝、无样例附件引用、校验和一致。
- `VideoTaskOrchestratorTest`（8）：用 **ComfyUI 替身**验证——不可达时不提交并落库 FAILED、
  执行失败落库 FAILED、**轮询超时落库 TIMEOUT**、**超 5 秒标记 truncation_applied**、
  5 秒内不标记、跨用户素材按不存在处理且不上传、**DRAFT 工作流拒绝提交**、非法 workflowCode 拒绝。

### 未实现（明确未验证）

1. **`video_workflow_version` 种子导入**：契约 JSON → 表的导入程序未实现，表内 0 行。
   当前工作流版本由启动时读契约文件提供，尚未以数据库为权威。
2. **截断精度**：**已按「严格 ≤5.000 秒」实现并实机验证通过**（见 §6.7）：
   24fps 下换算为恰好 120 帧，实测 `duration=5.000000`、`nb_frames=120`。
3. **越权测试**：已验证「用他人 assetId 会被拒绝」（API 层），
   但**未用两个真实账号**验证「看不到/下载不到他人素材与成片」的完整链路。
4. **后端镜像**：含 video 模块的镜像**未正式构建**（Docker Hub 拉取 bellsoft 基础镜像失败，
   且镜像内缺 ffmpeg）。联调用的是「生产镜像 + 挂载新 jar + 挂载静态 ffmpeg」。
5. **前端与后端联合联调**：**请求链路、加密登录、真实浏览器点击均已完成**（§6.4 / §6.5 / §6.6）。
   页面上提交按钮按设计仍禁用（工作流为 TESTING，`submittable=false`），
   因此**「从页面真实创建一个视频任务」仍未通过 UI 走通**——但同样的接口已由脚本实测跑通 4 次。
6. **并发与多节点**：未验证同一时刻多个任务的并发行为，也未验证多 GPU 调度
   （实测期间 GPU 被他人任务占用 68 GiB，我的任务仍能跑完，但耗时明显变长）。
7. **主机 `8080` 上的旧后端**：`comfyui-platform-6.0.0-stage` 仍在运行且也响应 `/video/*`，
   未做任何改动；但它是排查代理问题时的混淆源（详见 §6.4）。

## 6.1 前端接入（已完成，见 §7）

前端已接入真实 API，并通过 lint 与生产构建、镜像冒烟测试。由于工作流仍为 DRAFT，
提交按钮在页面上**保持禁用并显示真实原因**——因此「页面 → 接口」的调用尚未真实发生过，
这一点不得当作已联通。

## 6.2 后端接口联调（已真实执行，2026-09-16）

在服务器上以**隔离实例**完成了「页面 → 接口 → 数据库」的真实调用验证。

### 隔离实例的搭建方式（可复现）

生产镜像不含 video 模块，且服务器**无法从 Docker Hub 拉取 bellsoft 基础镜像**。因此采用：

- 使用生产镜像 `ghcr.io/ningchirsty/hotter-ai-platform-backend@sha256:7afb23b9…`；
- 把本次构建的 `ruoyi-admin/target/ruoyi-admin.jar` **只读挂载覆盖** 容器内 `/ruoyi/server/app.jar`；
- 新建 `ai-video-isolated` 网络，把 `ai-video-poc-mysql-1` / `-redis-1` 接入并加 `mysql`/`redis` 网络别名
  （容器在新网络里默认没有这两个主机名，会造成 `UnknownHostException: mysql`）；
- 复用生产容器注入的数据源与 Redis 凭据（**从容器环境读取，不落盘、不回显**）；
- 端口 `127.0.0.1:18084`，与生产 `18082` 隔离；
- 联调专用开关：`API_DECRYPT_ENABLED=false`、`CAPTCHA_ENABLE=false`
  （否则登录接口因 `@ApiEncrypt` 与验证码无法用脚本调用）、
  `VIDEO_REQUIRE_PUBLISHED=false`、`VIDEO_TESTING_WORKFLOWS=wf-t2v-h3,wf-i2v-h3,wf-fl2v-h3`。

启动脚本：服务器 `~/.mig/start-isolated.sh`。**未修改任何生产容器**（联调后核实
`ai-video-poc-backend-1` 仍为 `Up 32 hours`、`restarts=0`）。

### 验证结果

| # | 场景 | 结果 |
|---|---|---|
| 1 | 服务启动 | **成功**，日志 `工作流契约加载完成：可提交版本 3 个` |
| 2 | `GET /video/capabilities` | 200，三个工作流及 `supportedTier=高清 · 1080P`、`supportedDuration=5 秒`、`maxDurationSeconds=5` |
| 3 | `POST /video/assets`（真实 PNG） | 200，返回 `assetId=2100252589757857794`，**素材库回读可见**（69 字节，`source_kind=UPLOAD`） |
| 4 | `GET /video/assets` 分页 | 200，`total/rows` 正确 |
| 5 | 非法 `workflowCode` | 400 `不支持的工作流：wf-nope` |
| 6 | 越界字段 `sampler` | 400 `不支持的字段：sampler` |
| 7 | 越界字段 `nodeId` | 400 `不支持的字段：nodeId` |
| 8 | 档位 720P | 400 `输出档位只支持 高清 · 1080P` |
| 9 | 时长 10 秒 | 400 `视频时长只支持 5 秒` |
| 10 | 空提示词 | 400 `视频描述不能为空` |
| 11 | I2V 缺图片 | 400 `图生视频必须提供图片素材` |
| 12 | FL2V 缺尾帧 | 400 `首尾帧生视频必须同时提供首帧和尾帧` |
| 13 | 能力与工作流错配 | 400 `能力与工作流不匹配` |
| 14 | **跨用户素材**（用别人的 assetId） | 400 `素材不存在或无权访问` |
| 15 | 幂等键重复提交 | 两次返回**同一 taskId**，第二次带 `idempotent:true` |
| 16 | 未鉴权访问 4 个端点 | 全部 `{"code":401,"msg":"登录状态异常，请重新登录"}` |
| 17 | DRAFT 工作流提交 | 400 `工作流尚未通过实机验收，暂不可提交`（`submittable=false`） |

**未执行**：`POST /video/tasks/{id}/execute`。即**没有向 ComfyUI 提交真实生成任务**，
未占用 A100、未产出成片、未做 ffprobe 度量。因此 §4 的成片实测项仍未验证。

### 联调中发现并修复的 4 个真实缺陷

1. **空提示词任务被错误入库**：控制器在入库前调用 `validateFields`，但当时该方法不校验提示词，
   只有执行阶段的 `prepare()` 才检查——导致空提示词任务先入库、后失败。
   已把提示词校验移入 `validateFields`，并补 3 个回归用例锁定。
2. **主键为 NULL 导致上传失败**：`video_asset.id` / `video_task.id` 为 `NOT NULL` 且无
   `AUTO_INCREMENT`，代码却传 `null` 期望自增，报 `Column 'id' cannot be null`。
   已改为应用侧生成（`IdGeneratorUtil.nextLongId()`）。
3. **校验异常被兜底处理器吞成 500**：`VideoTaskException` 落到全局 `Exception` 处理器，
   所有正确的拒绝都返回「发生未知异常」+错误编号，前端拿不到原因、也无法区分。
   已在控制器内加 `@ExceptionHandler`（控制器内优先级高于全局 advice），统一返回 400 + 具体原因。
4. **`IdGeneratorUtil` 无法在纯 JUnit 环境使用**：其静态初始化从 Spring 容器取 bean，
   离线单测抛 `ExceptionInInitializerError`。已把主键生成改为**可注入依赖**，
   编排器因此保持可离线测试。

另有一个**非缺陷的行为澄清**：未鉴权时 HTTP 状态仍为 200，错误在响应体 `code=401`。
这是若依的既有约定，安全边界在 body 上；已实测确认 `@SaCheckPermission` 确实生效。

### 环境坑（新增）

- 生产容器环境里 `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME` 用 `cut -d= -f2-`
  读出来为空，需回落 `root`；密码字段读取正常。
- `docker export` + `docker import` 生成的“基础镜像”会**丢失 `PATH`/`JAVA_HOME`**，
  容器以退出码 127 启动失败；因此改用挂载 jar 的方式，不要走 import 快照。
- **不要用 PowerShell 的 `Set-Content` 处理含中文的文本文件**：PS 5.1 默认 ANSI 编码回写，
  会把 UTF-8 中文压成 GBK 乱码（我已因此损坏过 2 个 Java 文件，其中代码内的中文字符串
  如错误提示也被破坏）。同类教训：`edit` 在 CRLF 文件上可能只保留首个换行，
  应改用显式 UTF-8 写入，或在服务器侧用 python 修改。
- 向服务器传含中文的脚本时，PowerShell here-string **会损坏多字节字符**。
  可靠做法见本仓库的 `push-utf8.ps1`：字节级 CRLF→LF 规整 + base64 分块 + **MD5 校验**。

## 6.3 GPU 实机端到端（已真实完成，2026-09-16）

在 A100 上完成了**从接口提交到成片归档的全部环节**，并用 ffprobe 实测了输出。

### 实测结果（任务 `VIDEO-20260916-42904`）

| 环节 | 结果 |
|---|---|
| 提交 | `prompt_id = 23da47e7-…`（真实 ComfyUI 任务） |
| 事件序列 | `SUBMITTED` → `TRUNCATED` → `SUCCEEDED` |
| 状态 | `SUCCEEDED`，`attempt_count=1`，`truncation_applied=1` |

**原始成片（ComfyUI 产出）实测**：

| 项 | 值 |
|---|---|
| 分辨率 | **1920×1080** |
| 帧率 | **24/1** |
| 帧数 | **124** |
| **时长** | **5.167 秒（5167 ms）** |
| 大小 | 5,718,800 字节 |

**截断后交付成片实测**：

| 项 | 值 |
|---|---|
| 分辨率 | **1920×1080** |
| 帧率 | 24/1 |
| 帧数 | 122 |
| **时长** | **5.083 秒（5083 ms）** |
| 大小 | **5,683,394 字节** |
| SHA-256 | `cafb45b8a06b43323549a4937fe81d7d7ff0950e396468e86c6a188717d7fca9` |

数据库 `video_asset` 与磁盘文件的**大小、分辨率、时长、存储键完全一致**，且原未截断文件已删除。
API 也返回了真实实测值：`width=1920, height=1080, fps=24, durationMillis=5083, truncated=true`。

> 结论：三份模板确实产出 **124 帧 @24fps = 5.167 秒**，**超出产品 5 秒上限**，
> 服务端已用 ffmpeg 截断到 5.083 秒交付。这条结论此前只是文档推测，现在是实测数据。

### 三种能力全部实机通过

同一隔离实例上依次真实提交，**T2V / I2V / FL2V 三种能力全部成功**：

| 能力 | workflowCode | 状态 | 截断 | 分辨率 | 帧数 | 时长 | 成片大小 | 事件序列 |
|---|---|---|---|---|---|---|---|---|
| 文生视频 | `wf-t2v-h3` | SUCCEEDED | ✓ | 1920×1080 | 122 | 5083 ms | 5,683,394 B | SUBMITTED→TRUNCATED→SUCCEEDED |
| 图生视频 | `wf-i2v-h3` | SUCCEEDED | ✓ | 1920×1080 | 122 | 5083 ms | 1,658,193 B | SUBMITTED→TRUNCATED→SUCCEEDED |
| 首尾帧生视频 | `wf-fl2v-h3` | SUCCEEDED | ✓ | 1920×1080 | 122 | 5083 ms | 1,515,803 B | SUBMITTED→TRUNCATED→SUCCEEDED |

- I2V 真实走了「上传图片 → 解析为 ComfyUI 输入文件名 → 写入 timeline 的 keyframe/segment/shot」；
  FL2V 额外写了尾帧（`endImage`）。
- 每条成片都用 ffprobe 复测：**数据库记录的分辨率/时长/大小与磁盘文件逐项一致**。
- 原始产出均为 5167 ms，均被判超时并截断至 5083 ms —— 说明超时判定在三种能力上都生效。
- 三者均在 A100 上真实推理完成；FL2V 因 GPU 被他人任务占用 68 GiB 而耗时较长（仍在轮询预算内）。

### 本轮在真实运行中发现并修复的 5 个缺陷（离线测试无法发现）

1. **响应反序列化失败**：`restClient...body(JsonNode.class)` 在 RuoYi 的精简 Jackson 转换器下
   抛 `HttpMessageConversionException`（`JsonNode` 是抽象类型，无法被构造），任务无法提交。
   已改为以 `String` 接收响应，再用模块自有 `ObjectMapper` 解析。
2. **成片被误判为「没有产出视频」**：ComfyUI 的 `SaveVideo` 把视频放在 **`images`** 数组
   （字段 `filename`/`subfolder`），而解析只读 `videos` 数组，导致一次**成功执行**被记为失败。
   已同时支持两种形态 + 扩展名判断，并补 5 个回归用例。
3. **截断后存储键失效**：截断生成 `_capped.mp4` 并删除原文件，但库里仍存旧键（指向已删除文件）。
   已新增 `AssetStorage.keyOf(Path)` 由最终路径还原存储键。
4. **任务指标全为 NULL**：`markSucceeded` 用的是 ComfyUI 输出的宽高/时长（ComfyUI 根本不返回这些字段），
   导致 `video_task.output_width/height/fps/duration_ms` 全空。已改为一律使用 ffprobe 实测值。
5. **大小/校验和未随截断更新**：截断后仍记录截断前的字节数与校验和。已统一以最终落盘文件为准。

另外：错误信息原先只记录异常类名（如 `HttpMessageConversionException`），根因丢失、
排查困难；已改为记录根因链（`describe(e)`）。

### 部署约束（已解决）

后端镜像原先**不含 `ffprobe`/`ffmpeg`**，且：
- 基础镜像基于 Rocky Linux 9 且**没有 dnf/yum**（只有 `microdnf`）；
- Rocky 默认源**也没有 ffmpeg 包**（实测 `microdnf install ffmpeg-free|ffmpeg` 报 `No package matches`）；
- 基础镜像**没有 `xz` 命令**，而 GNU tar 的 `-J` 会调用外部 xz。

**已在 `ruoyi-admin/Dockerfile` 中解决**：先 `microdnf install -y xz`，再下载
**静态构建**（不依赖发行版与 glibc）并校验 SHA-256，安装到 `/usr/local/bin`：

| 构建参数 | 默认值 |
|---|---|
| `FFMPEG_URL` | `https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz` |
| `FFMPEG_SHA256` | `abda8d77ce8309141f83ab8edf0596834087c52467f6badf376a6a2a4c87cf67` |

离线/内网构建可用 `--build-arg` 覆盖为内网镜像地址与对应校验值。

同一处还**显式声明了 `PATH` 与 `JAVA_HOME`**：不依赖基础镜像是否自带这些变量。
这一条是被真实故障逼出来的——若基础镜像 `Env` 为空（例如用 `docker export` 快照
本地兜底构建时），缺少 JDK 目录会让容器以 **127（command not found）** 启动失败。

**验证结果**：

| 项 | 结果 |
|---|---|
| 镜像内 `ffprobe` / `ffmpeg` / `java` | 全部可用（7.0.2-static / OpenJDK 21.0.12.1） |
| 镜像体积 | **1.66 GB**（原 956 MB；+约 700 MB，主要是两个静态二进制共约 155 MB 及其它层） |
| 用该镜像启动实例（**仅挂载契约目录，无任何 ffmpeg/jar 挂载**） | 启动成功，契约加载 3 个 |
| 该实例上真实跑一次 T2V | **SUCCEEDED**，`1920×1080 / 24fps / 5083ms / truncated=true`，事件 `SUBMITTED→TRUNCATED→SUCCEEDED` |

因此**「镜像缺 ffmpeg」这个部署阻塞已解除**；镜像体积增大是需要知情的代价。

## 6.4 前端 ↔ 后端联调（已完成请求链路，UI 点击未覆盖）

前端 dev server 已真实运行并**经其自身代理**调通后端。覆盖范围与**未覆盖范围**都列明。

### 已完成

| 验证项 | 结果 |
|---|---|
| SPA 由 dev server 提供 | 200，`集团AI创作平台` 标题与 `#app` 存在 |
| Vue 入口编译 | `/src/main.ts` → 200 |
| 视频视图编译 | `/src/views/video/index.vue` → 200（161 KB，vite 转换成功） |
| **示例数据已彻底移除** | 编译产物中 `VIDEO-20260911-018` / `新品包装主图` 命中数 = **0** |
| 登录（经 `/dev-api` 代理） | 200，返回真实 `access_token` |
| 页面挂载的 3 个请求（经代理） | `/video/capabilities` 200；`/video/assets` 200；`/video/tasks` 200 |
| 经代理上传素材 | 200，返回 `assetId` |
| SPA 路由 `/video-creation` | 200（客户端路由接管） |

`/video/capabilities` 返回 `status=TESTING`、`submittable=false`、`testable=true`
—— 与前端逻辑一致：**提交按钮保持禁用并显示真实原因**，没有伪装成可提交。

### 本次修复的真实配置缺口

`frontend/vite.config.ts` 的代理**硬编码 `target: 'http://localhost:8080'` 且没有超时设置**：

- 视频任务执行是长轮询（H3 单次实测数分钟），默认代理超时会在任务完成前断开；
- 无法指向隔离联调实例。

已改为可由环境变量覆盖并显式放大超时，默认值保持原行为不变：

```ts
target: env.VITE_APP_PROXY_TARGET || 'http://localhost:8080',
timeout: Number(env.VITE_APP_PROXY_TIMEOUT || 3600000),
proxyTimeout: Number(env.VITE_APP_PROXY_TIMEOUT || 3600000),
```

联调时用 `VITE_APP_PORT=18085 VITE_APP_PROXY_TARGET=http://127.0.0.1:18084 pnpm dev`。

### 未覆盖（必须如实说明）

1. **没有真实浏览器点击**：本次执行环境没有浏览器工具，因此
   「登录表单提交、页面渲染出三个真实能力卡片、点击创建任务、看到任务状态流转」
   这些 **UI 层行为没有被真实点击验证过**。已验证的是浏览器会走的那条请求链路。
2. **登录加密路径**：**已验证通过**（见 §6.5），前后端 RSA 密钥配对正确，加密登录可用。

### 浏览器联调前的一个陷阱（重要）
3. **UI 仍不能真实提交任务**：后端三个工作流是 `TESTING`，`submittable=false`，
   前端按设计**禁用提交按钮**。要在页面上真实提交，需要把 `require-published` 相关逻辑
   与前端展示口径一起调整（或按 §8 走发布流程）。

### 浏览器联调前的一个陷阱（重要）

主机 `8080` 端口上**另有一个后端**在监听：
`/opt/ai-edge/ruoyi-platform/comfyui-platform-6.0.0-stage/backend`（已运行 3 天）。
它**也响应 `/video/capabilities`**，因此若前端代理仍指向默认的 `localhost:8080`，
请求会打到那台旧后端而不是本次实现，表现为莫名的 `Captcha invalid` / 404。
排查此类问题时要先用「唯一标记 + 查目标日志」确认请求到底落在哪台后端。

## 6.5 接口加密登录验证（已完成）

生产配置 `api-decrypt.enabled: true`（在 `application.yml`，prod profile 未覆盖），
前端 `VITE_APP_ENCRYPT=true` 且 `login()` 显式带 `isEncrypt: true`，因此**登录必须加密**。
本次精确复现前端方案做了端到端验证（脚本：`verify-encrypted-login.py`）：

| 用例 | 结果 |
|---|---|
| **明文登录**（不带 `encrypt-key` 头） | **被拒绝**：`{"code":403,"msg":"没有访问权限，请联系管理员授权"}` |
| **加密登录**（复现前端方案） | **成功**，返回真实 `access_token`（len=477） |
| 用该 token 调 `/video/capabilities`、`/video/assets`、`/video/tasks` | 全部 200 |

复现的前端加密方案（`frontend/src/utils/crypto.ts` + `request.ts`）：

1. `aesKey` = 32 个十六进制字符的随机字符串（UTF-8 视为 **32 字节** → **AES-256**）；
2. `encrypt-key` 头 = `RSA_PKCS1v15(base64(utf8(aesKey)))`，再 base64；
3. body = `AES-256-ECB + PKCS7(JSON)`，再 base64。

**密钥配对已确认**：后端 `api-decrypt.privateKey` 与前端 `.env` 的
`VITE_APP_RSA_PUBLIC_KEY` 是同一对密钥（`e8 10 3b 91…` / `ef 10 3b 91…`）。

结论：**浏览器登录这条路径已经打通**，剩下的只有「真实点击页面」这一层没有被机器验证。

## 6.6 真实浏览器 UI 验证（已完成，2026-09-16）

**用真实 Chromium（Playwright）驱动页面完成了整条点击流程**，不再是「只验证请求链路」。

方法：本机 Windows 上有 Playwright 1.58 + Chromium + Chrome；服务器 dev server 绑定 `0.0.0.0`，
因此通过 **SSH 端口转发** `本机18085 → 服务器18085`，让本机浏览器访问真实前端。

### 结果（全部真实点击/渲染）

| 步骤 | 结果 |
|---|---|
| 打开首页 | 自动跳转 `/login?redirect=/index`，标题 `集团AI创作平台`，表单为「用户名」「密码」「记住我」，**无验证码** |
| 填写并登录 | 走**真实加密登录**（AES-256-ECB + RSA `encrypt-key`）→ `code=200`，获得 token |
| 登录后进入应用 | `/index`，左侧菜单出现 **「视频创作」「我的任务」**（即迁移的菜单在真实界面可见） |
| 打开 `/video-creation` | 三个能力卡片齐全：图生视频 / 文生视频 / 首尾帧生视频 |
| 页面状态真实驱动 | **提交按钮 `disabled=true`**，文案 `暂不可提交`，`title="工作流处于 TESTING，仅隔离联调环境可提交"` |
| 切换「我的任务」 | 点击成功，显示空状态 |
| 切换「素材库」 | 点击成功，显示空状态 |
| 页面错误 | **0** |
| 失败请求（>=400） | **0** |
| 视频接口调用 | `/video/capabilities`、`/video/tasks`、`/video/assets` 全部 200 |

**视觉证据（截图）**：登录页、登录后首页、视频创作页、我的任务、素材库共 5 张，
存于本机 `C:\Users\Administrator\ubuntu\shots\`。

截图里可确认状态是**由后端真实驱动**的，而非硬编码：
右上角版本标签显示 `H3 · v0.1.0-draft · TESTING`；三个能力卡片显示
`模板已导入 · 待服务接入` / `待服务接入`；提交按钮禁用并给出 TESTING 原因。
这些文案只有在接口真的返回 `status` 时才会出现。

唯一的控制台告警是 `SSE connection error`（消息推送），仅在 **dev server + 端口转发**下出现，
属开发环境现象，与应用逻辑无关。

至此「前端 → 代理 → 鉴权 → 接口 → 数据库」以及**真实浏览器点击**均已验证。

## 6.7 严格 5.000 秒帧精确截断（已完成，2026-09-17）

产品要求**严格 ≤5.000 秒**，而原先的 `-t 5.0 -c copy` 只能切到最近关键帧，实测得
**122 帧 / 5.083 秒**，不满足要求。在真实成片上比较了方案
（源：124 帧 / 24fps / 5.167 秒 / 含 AAC 音轨）：

| 方案 | 帧数 | 时长 | 体积 | 结论 |
|---|---|---|---|---|
| `-t 5.0 -c copy`（原实现） | 122 | 5.083s | 3.68 MB | ✗ 超上限 |
| `-frames:v 120 -c copy` | 120 | 5.024s | 3.67 MB | ✗ 音频未截，容器时长仍超 |
| 整体重编码 | 120 | **5.000s** | 4.95 MB | ✓ 但体积 +35% |
| **视频流拷贝 + 音频精确截断** | 120 | **5.000s** | **3.70 MB** | ✅ 采用 |

**实现**（`MediaProbe.truncate(source, maxMillis, fps)`）：

- 用**实测帧率**把时长换算成帧数：`frameLimit = floor(maxMillis/1000 × fps)`，
  24fps 下 5 秒 = **120 帧**；
- 视频 `-frames:v 120 -c copy`（不重编码，质量无损、速度快）；
- 音频 `-af atrim=end=5.000,asetpts=PTS-STARTPTS -c:a aac`
  （AAC 帧长固定，必须重编码才能精确裁剪，否则容器时长仍被音频拖长）；
- 拿不到帧率时退回 `-t`，并在日志中说明结果可能不精确。

**实机验证**（任务 `VIDEO-20260917-31402`）：

```
成片实测：1920x1080 @24.0fps 5167ms           ← 原始 124 帧
成片已截断至 5.000s（上限 120 帧）：… -> …_capped.mp4 (5665228 bytes)
成片实测：1920x1080 @24.0fps 5000ms           ← 截断后
归档文件实测： nb_frames=120  r_frame_rate=24/1  duration=5.000000
数据库记录：   1920|1080|5000ms|truncation_applied=1
```

**恰好 120 帧 / 5.000 秒**，数据库与磁盘一致。

## 6.8 越权复验与并发验证（已完成，2026-09-17）

用**两个不同账号**（`usera` / `userb`，可回滚的临时账号）与并发压测完成验证，
并在修复后连续跑 3 轮。

### 越权复验（10 项，全部通过）

| 用例 | 结果 |
|---|---|
| B 用 A 的 `assetId` 建任务 | 拒绝 `素材不存在或无权访问` |
| B 删除 A 的素材 | 拒绝；且复核 A 的素材仍在 |
| B 查看 A 的任务详情 | 拒绝 `任务不存在或无权访问` |
| B 取消 A 的任务 | 拒绝 `任务不存在或无权访问` |
| B 的素材列表含 A 的素材 | 不含 |
| A 的素材列表含 B 的素材 | 不含 |
| B 的任务列表含 A 的任务 | 不含 |
| 对照：A 查看自己的任务 | 成功（证明拒绝不是功能故障） |
| 对照：B 用自己的素材建任务 | 成功 |

### 并发验证（8 项，全部通过）

| 用例 | 结果 |
|---|---|
| 并发创建 8 个任务 | 8/8 返回 200 |
| `taskId` / `taskNo` 唯一性 | 全唯一，无串号 |
| 任务总数增加量 | 恰好 +8 |
| 每个任务按 id 读回 | 8/8 正确 |
| **同一幂等键并发提交 5 次** | 只产生 **1** 个任务；5 个请求**全部返回 200** |
| 数据库不变量（复核） | `task_no` 无重复、幂等键重复数 **0** |

### 本轮又发现并修复的 3 个真实缺陷

1. **取消接口泄露任务存在性**：B 取消 A 的任务时返回「任务不在排队中，无法取消」，
   而非「任务不存在或无权访问」——泄露了该任务确实存在。已改为**先校验归属**，
   非本人任务的返回与「不存在」完全一致。
2. **并发重复提交返回 409 错误**：幂等预检查存在 TOCTOU 窗口，并发时 4/5 个请求
   被 `MybatisExceptionHandler` 以 `DuplicateKeyException` 拦成 409。
   任务确实只建了一个，但用户会看到失败。
   已在插入处捕获 `DuplicateKeyException` 并**返回已存在的任务（200 + idempotent）**。
   注：`MybatisExceptionHandler`（`ruoyi-common-mybatis`）与 `GlobalExceptionHandler`
   均为无 `@Order` 的 `@RestControllerAdvice`，同类异常按具体类型优先匹配，
   因此**不能靠控制器内 `catch` 之外的声明式处理器优先级来改**；
   修复点必须落在插入调用处。
3. **`task_no` 会碰撞**：原用 `System.nanoTime() % 100000` 生成序号，
   实测出现 `Duplicate entry 'VIDEO-20260917-77480' for key 'video_task.uk_task_no'`，
   导致建任务返回 409。已改为**由雪花主键派生**（`floorMod(taskId, 1_000_000)`），
   在主键唯一的前提下保证 `taskNo` 唯一。

### 另一个必须记住的流程坑

**隔离实例的镜像是构建时固化的，改代码后只 `docker run` 不会生效。**
本轮曾因此让容器继续跑旧 jar（容器内 `app.jar` 01:05 vs 工作区 01:54），
排查了很久。已在 `~/.mig/start-isolated.sh` 中加入**镜像与 jar 的时间戳比较**：
镜像不存在或落后于 jar 就自动重建，保证「启动隔离实例」永远等于「跑最新代码」。

## 6.9 契约 → 数据库 同步镜像（方案 A，已完成）

**决策**：`video_workflow_version` 表定位为**契约文件的同步镜像**（供查询/审计/运维），
**运行时权威仍是契约文件**（`WorkflowContractRegistry` 只读文件并校验 SHA-256）。
这样既让空表变得有用，又不动一条已实测通过的数据通路。

**实现**：

- `VideoWorkflowVersionRepository` / `JdbcVideoWorkflowVersionRepository`：
  单条 `INSERT ... ON DUPLICATE KEY UPDATE`，依赖唯一索引 `uk_workflow_version` 保证幂等；
  **UPDATE 子句刻意不含 `status` / `published_by` / `published_time`** ——
  一次同步不得把审核结果冲掉。
- `WorkflowContractDbSync`：实现 `ApplicationRunner`，容器就绪后同步一次；
  **任何异常只告警、绝不抛出**，避免旁路能力影响启动。
  额外字段（`billing`/`perf`/`outputRule`/`supportedOutputs`）直接从契约 JSON 取，
  避免为同步去扩张领域记录。
- 开关：`video.sync-contract-to-db`（默认 true）。

**实测结果**：

| 项 | 结果 |
|---|---|
| 首次启动 | `新增 24 条，更新 0 条，表内共 24 条`（含未交付的占位条目，便于运维看到缺口） |
| 三个 H3 模板 | 状态 `TESTING` + **真实 checksum**（`620e53c9…`/`fd6ce1f4…`/`ec9c817d…`）+ mapping/perf/outputRule 齐全 |
| 占位条目 | 如 `wf-vext-h3` → `DRAFT`，**`checksum = NULL`**（不伪装成已校验） |
| 重启幂等 | `新增 0 条，更新 24 条`，总数仍 24，**无重复行** |
| 离线测试 | 新增 5 个用例：只同步已校验的 checksum、不改可提交判定、带展示字段、幂等、异常不外抛 |

> 注意：同步**只影响这张表**。任务能否提交仍由契约文件的 status 与校验和决定，
> 已有测试专门断言「同步后 DRAFT 依然不可提交」。

## 7. 环境操作上的坑（避免重复踩）

1. **`/tmp` 在 `aiadmin` 下异常**：`sudo -S ... < /dev/null` 曾报 `/dev/null: Permission denied`、
   `/tmp/.spw: No such file or directory`，而 `/tmp` 实为 `drwxrwxrwt`。改用 `~/.mig/` 私有目录更稳。
2. **`sudo -S` 与 stdin 冲突**：`S() { echo "$PW" | sudo -S ...; }` 在 `mysql < file` 场景下
   会把**密码喂进 mysql 的 stdin**，报 `ERROR 1064 ... near '<口令明文>'`
   （此处刻意不回显真实口令）。
   正确做法：密码经独立文件描述符（如 `exec 9< /tmp/.spw; cat <&9`），SQL 用 `docker cp` 送进
   容器后以 `source` 执行，完全不占用 stdin。
3. **PowerShell 5.1 读文件**：`Get-Content -Raw` 按 ANSI 解码 UTF-8，会把中文压成乱码并造成
   「双层损坏」的 base64。必须用 `[IO.File]::ReadAllText($p, (New-Object Text.UTF8Encoding($false)))`。
4. **SSH stdin 管道会注入 `\r`**（表现为 `bash\r: command not found`、`base64: invalid input`）。
   本仓库提供 `sync-to-server.ps1`，用 **base64 分块 + 独立 ssh 参数**传输（每块 6000 字符，
   规避 Windows 命令行长度上限），不走 stdin，已验证可靠。
5. **`-i` 标志**：`docker exec -i` 的 stdin 易被父进程管道影响，纯查询应显式 `< /dev/null`。
6. **`LoginUser` 没有 `getTenantId()`**，且该仓库无 `TenantHelper`；控制器改为按
   `sys_user.tenant_id` 反查，查不到时回落默认租户 `000000` 并记 warn。
7. **本机 Windows 侧**：`pwsh` 在受限沙箱下启动即失败（`exit code 3221225794`，DLL 初始化失败），
   需要 `danger-full-access` 才能执行命令；`Get-FileHash` 亦不可用，改用
   `[Security.Cryptography.SHA256]::Create()`。本机 `bash` 走 WSL 但**无 Ubuntu 发行版**，
   无法本地跑 bash 脚本。

## 8. 建议的后续执行顺序

1. **用真实浏览器点一次页面**（当前唯一剩下的验证缺口）：
   登录加密链路已验证可用，直接启动即可：
   ```bash
   cd frontend
   VITE_APP_PORT=18085 VITE_APP_PROXY_TARGET=http://127.0.0.1:18084 pnpm dev
   ```
   浏览器打开 `http://192.168.2.134:18085`，用联调账号登录，确认视频创作页
   渲染出三个真实能力卡片、提交按钮禁用并提示 `DRAFT`/`TESTING` 原因、
   「我的任务」「素材库」显示真实（空）数据。
   **注意**：代理默认目标 `localhost:8080` 上是另一台旧后端，务必用覆盖变量指到 18084。
2. **确认截断精度要求**：当前 `-c copy` 截断得 5.083 秒；如需严格 ≤5.000 秒，
   改为重编码或按帧截断。
3. **跨用户越权复验**：用两个真实账号确认彼此看不到/下载不到对方的素材与成片。
4. **实现契约 → `video_workflow_version` 的种子导入**，让运行时以数据库为权威。
5. **并发验证**：同时提交多个任务，确认队列与状态流转不会互相污染。
6. **按需精简镜像**：当前 1.66 GB；若体积敏感，可只保留 ffprobe 的测量能力
   （但截断需要 ffmpeg），或改用带 ffmpeg 的发行版基础镜像。
7. **推镜像到内网仓库**：本次前后端镜像已按目标 SHA 构建成功
   （`hotter-ai-platform-backend:…` 1.66 GB / `hotter-ai-platform-frontend:…` 89.7 MB），
   但**未推送**到任何仓库。
8. 全部通过后才把工作流推进为 PUBLISHED 并开放正式环境提交；
   随后按 `script/deploy/README.md` 走受控发布入口。

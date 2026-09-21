# 图像创作模块实施交接（按视频创作模块同构搭建）

日期：2026-09-21 · 分支：`feature/image-creation-module` · 执行机：本机 Windows（构建/测试）+
ComfyUI 主机 `192.168.2.223`

本文记录**做了什么**、**哪些是真机实测过的**、**哪些没做**。未验证项一律标为「未验证」，
不得当成通过——这套写法沿用 `docs/video-module-implementation-handoff.md` 的规矩。

---

## 1. 结论摘要

| 层 | 状态 |
|---|---|
| ComfyUI 侧 4 个工作流改中文名 | ✅ 已改并核验（`/api/userdata` 可见） |
| 4 份 API Format 模板 | ✅ 已产出并**逐个真机跑通出图**（见 §2.3） |
| 契约 `script/image/workflows/` | ✅ 含 checksum、README、校验脚本与 node 测试（12/12 通过） |
| 后端 `org.dromara.ai.image` | ✅ 编译通过；单测 28/28 通过 |
| SQL（4 表 + 菜单，4 方言） | ✅ 脚本已交付；**未在目标库执行** |
| 前端 `api/image` + `views/image` | ✅ oxlint 0 问题；`pnpm build:prod` 通过 |
| 打包与 CI | ✅ 根 Dockerfile 新增 COPY；CI 新增契约 node 测试 + 镜像断言 |
| 端到端（后端 → ComfyUI → 落库 → 前端） | ❌ **未验证**（缺可运行的后端 + 数据库环境，见 §8） |

---

## 2. ComfyUI 侧

### 2.1 工作流改中文名

`/data/ai-stack/comfyui/user/default/workflows/` 下：

| 原名 | 现名 |
|---|---|
| `image_qwen_image_2_1_t2i.json` | `Qwen-Image-2.1-文生图.json` |
| `image_qwen_image_2_1_image_edit.json` | `Qwen-Image-2.1-指令改图.json` |
| `image_qwen_image_2_1_background_removal.json` | `Qwen-Image-2.1-抠图去背景.json` |
| `Qwen-Image-2.1-图生图-img2img.json`（本次新建） | `Qwen-Image-2.1-图生图.json` |

注意：该目录属 **root**，用登录用户 `mv` 会 `Permission denied`；必须
`docker exec comfyui mv ...`（容器内是 root）。

### 2.2 四份 API Format 模板（后端受控工件）

`script/image/workflows/api/`，均为规范格式（`JSON.stringify(obj,null,2) + "\n"`），
与后端 SHA-256 校验口径一致：

| workflowCode | 能力 | 节点数 | checksum（前 12 位） |
|---|---|---|---|
| `wf-t2i-qwen21` | 文生图 | 8 | `d8a773d86c93` |
| `wf-i2i-qwen21` | 图生图 | 9 | `bb4e6b12ee79` |
| `wf-edit-qwen21` | 指令改图 | 10 | `0cb740bd6897` |
| `wf-bgremove-qwen21` | 抠图去背景 | 8 | `671616e457c9` |

模型三件套固定：`qwen_image_2.1_int8_convrot` + `qwen3vl_8b_int8_convrot`（type=`qwen_image`）+
`qwen_image_2.1_vae_bf16`；采样固定 `steps=25 / cfg=1 / euler / simple`，唯一允许覆写的采样参数是
图生图的 `denoise`（前端字段 `strength`）。

### 2.3 真机实测（ComfyUI 0.37.0 @ 192.168.2.223:8188，A100）

`api/_validation-live.json` 是脚本产出的原始记录：

| 模板 | 结果 | 耗时 | 输出 |
|---|---|---|---|
| `wf-t2i-qwen21` | success | 4.0 s | 1024×1024 RGBA |
| `wf-i2i-qwen21` | success | 4.0 s | 896×1152（跟随输入图） |
| `wf-edit-qwen21`（3 张参考图） | success | 24.0 s | 896×1152（跟随 image1） |
| `wf-edit-qwen21`（裁剪为 2 张） | success | 20.0 s | 896×1152 |
| `wf-bgremove-qwen21` | success | 12.4 s 冷启 / 4.0 s 热 | 896×1152 **RGBA** |

`wf-edit-qwen21` 的输出经人工核对：人物、姿态、背景保持，绿色外套被换成
`<image2>` 的浅蓝牛仔衬衫——即官方编辑能力生效。抠图输出 IHDR `color_type=6`（真透明）。

### 2.4 两个必须知道的坑（都是本次排障实测出来的）

**坑 1：`images` 自动增长输入必须用「扁平点号键」**

`TextEncodeQwenImage21.images` 是 `COMFY_AUTOGROW_V3`。ComfyUI 用
`_io.build_nested_inputs()` 依据 schema 展开的 `dynamic_paths` 还原嵌套结构，而槽位
**只有在 prompt 的 inputs 里存在扁平键时才会被展开**：

```jsonc
"images.image_1": ["4", 0]          // ✅ 参考图生效，画布跟随 image1（实测 896×1152）
"images": {"image_1": ["4", 0]}     // ❌ 不报错，但参考图被丢弃，画布退回 1024×1024
```

判别方法：看输出画布尺寸。`TextEncodeQwenImage21.execute` 在 `images` 为空时
`latent_w = latent_h = resolution`，所以 1024×1024 就意味着参考图没生效。

**坑 2：空槽位必须裁剪**

模板里 `LoadImage.image` 为空串时，ComfyUI 会把 input 目录当文件打开：
`[Errno 21] Is a directory: '/workspace/ComfyUI/input'`。因此 EDIT 模板预置 3 个槽位
（节点 4/5/6 → `images.image_1..3`），后端填充时删除未使用槽位的节点与其槽位键。
已实测裁剪到 2 张可用。

---

## 3. 后端（`ruoyi-modules/ruoyi-ai` → `org.dromara.ai.image`）

| 文件 | 职责 |
|---|---|
| `domain/ImageCapability` | T2I / I2I / EDIT / BGREMOVE，含「是否需要输入图/尺寸档」等语义 |
| `domain/ImageTaskStatus` | 状态机（QUEUED→RUNNING→终态，终态不可流转） |
| `domain/ImageWorkflowMapping`、`ImageWorkflowVersion` | 契约记录（含能力字段白名单、size/strength 档位、alpha 要求、像素上限） |
| `exception/ImageTaskException` | 带 `errorCode` 的业务异常 |
| `service/ImageTemplatePreparer` | **深拷贝 + 白名单覆写 + 尺寸/seed 改写 + 参考图槽位裁剪 + 校验和守卫** |
| `service/ImageWorkflowContractRegistry` | 加载契约、逐份校验 SHA-256、`require()` 三级门禁 |
| `service/ImageTaskRepository` / `JdbcImageTaskRepository` | 任务/素材/事件持久化，**每条查询显式带 tenant_id + user_id** |
| `service/ImageAssetStore` | 素材存储门面（内部包 `AssetStorage`，见 §3.1） |
| `service/ImageAssetProbe` | **ImageIO 实测**宽高/格式/alpha，替代视频侧的 ffprobe |
| `service/ImageThumbnailService` | ImageIO 缩略图（透明图垫白底），与素材本体分开存放 |
| `service/ImageTaskOrchestrator` | 可达性预检 → 上传素材 → 填充 → 提交 → 轮询 → 实测归档 → 记事件 |
| `service/ImageTaskDispatchService` / `ImageTaskExecutionService` | 原子认领 + 入队；队列满回滚 |
| `service/ImageWorkflowVersionRepository` / `Jdbc...` / `ImageContractDbSync` | 契约 → 镜像表同步（UPDATE 子句刻意不含 status/published_*） |
| `config/ImageModuleConfiguration` | `image.*` 装配，默认 `image.enabled=false` |
| `controller/ImageCreationController` | `/image/**`，权限 `image:creation:view` / `image:creation:submit` |

### 3.1 一个必须知道的设计取舍：不注册 `ComfyClient` / `AssetStorage` Bean

视频模块用 `@ConditionalOnMissingBean(ComfyClient.class)` 与
`@ConditionalOnMissingBean(AssetStorage.class)` 装配这两个类型。**如果图像模块也注册同类型 Bean，
装配顺序一旦不利（组件扫描先扫到 image 包），视频模块就会跳过自己的 Bean、拿到图像模块的实例**
——存储根目录与 ComfyUI 客户端语义都会被串味，而视频链路是已实测通过的。

因此图像模块：
- 复用**接口与记录**（`org.dromara.ai.video.comfy.ComfyClient`、`ComfyOutput`、`AssetStorage`、
  `LocalFileAssetStorage`、`support.CamelCase`）——这些是真通用的；
- 但 `ImageComfyClient` 与 `ImageAssetStore` **在 `@Bean` 方法内部构造并显式传参**，不作为
  `ComfyClient` / `AssetStorage` 类型暴露。视频代码零改动。

另：`ImageComfyClient` 是**必须新写**的，不能复用 `HttpComfyClient`——后者的输出解析只认视频扩展名
（`looksLikeVideo`），图像模板产出的 PNG 会被过滤掉，一次成功执行会被记成「没有产出视频」。

### 3.2 图像不需要 ffmpeg

视频侧要 ffprobe/ffmpeg 是因为要量帧率/时长并做帧精确截断；图像只需要宽高、格式与 alpha，
JDK 自带 ImageIO 足够，因此镜像里不必再加任何二进制。输出实测（`ImageAssetProbe`）与
缩略图（`ImageThumbnailService`）都走 ImageIO。

---

## 4. 契约与校验

- 契约：`script/image/workflows/image-workflow-contracts.json`（4 能力 × 1 工作流，全部 DRAFT）。
- README：`script/image/workflows/README.md`（边界硬约束、实测数据、两个坑、启用配置）。
- 工具：`script/image/workflows/image-contracts.mjs`
  ```bash
  node script/image/workflows/image-contracts.mjs           # 校验
  node script/image/workflows/image-contracts.mjs --write   # 模板改动后重算 checksum
  ```
- 测试：`node --test script/image/workflows/image-contracts.test.mjs` → **12/12 通过**。
  断言包括：模板存在/规范格式/SHA-256 一致、mapping 与能力字段与模板节点三方对齐、
  输出节点是 SaveImage（保留 alpha）、模板不残留样例提示词与样例文件名、`images.image_N`
  必须连到存在的 LoadImage、状态必须与 `meta.status` 自洽、真机验证记录里四条均 success。

> **刻意与视频模块不同的一点**：`node --test` 已接入 CI（`.github/workflows/ci.yml`）。
> 视频模块的同类测试此前**没有接进 CI**，只在 README 里要求人工跑，结果契约状态与断言长期不一致
> （`import-h3.test.mjs` 目前是红的，见 §8）。

---

## 5. SQL

| 文件 | 内容 |
|---|---|
| `script/sql/ry_image_task.sql` | `image_task` / `image_asset` / `image_task_event`（MySQL，`CREATE TABLE IF NOT EXISTS`） |
| `script/sql/ry_image_workflow.sql` | `image_workflow_version`（契约镜像表） |
| `script/sql/ry_image_menu.sql` | 菜单 `920002 图像创作`（C，`image/index`，`image:creation:view`）+ `920003 提交图像任务`（F，`image:creation:submit`） |
| `script/sql/postgres/postgres_ry_image_menu.sql`、`oracle/oracle_...`、`sqlserver/sqlserver_...` | 菜单的三种方言变体 |

两处与视频模块的差异：
1. 列清单改为 `size_label` / `strength_label` / `output_width` / `output_height` / `output_has_alpha`
   / `output_size_bytes`（没有帧率与时长）；
2. **角色授权写进了脚本**：视频模块的 `sys_role_menu` 当年是在生产库手工补的，脚本里没有记录
   （见视频交接 §3），换环境要重新问一遍。这里用 `INSERT ... SELECT` 从「视频创作」已有授权推导，
   保证两个创作模块可见范围一致，且全部幂等。
3. 菜单 SQL 末尾附带一条**可选**的工作空间归组语句（把图像创作挂到「AI工具」分类，order 4），
   带 `EXISTS` 守卫：父分类不存在时影响 0 行，不会把菜单挂到不存在的父级上。

**未在目标库执行**——按约定只交付脚本，由运维执行（见 §8）。

---

## 6. 前端

| 文件 | 职责 |
|---|---|
| `frontend/src/api/image/types.ts` | 与控制器一一对应的类型；**只含能力编码/字段白名单/档位，不含节点 ID 与模板** |
| `frontend/src/api/image/index.ts` | 11 个接口：工作流视图、素材上传/列表/删除/原图/缩略图、任务创建/执行/列表/详情/取消 |
| `frontend/src/views/image/modules.ts` | 前端安全视图：4 个能力、字段、提示与灵感示例 |
| `frontend/src/views/image/index.vue` | 创建 / 我的任务 / 素材库三视图；提交按钮由服务端 `status` 驱动 |

- 菜单组件名 `image/index` 与 `store/modules/permission.ts` 的 `import.meta.glob` 自动匹配，无需改路由。
- 只有前端持 `image:creation:view` / `image:creation:submit`，与 SQL 一致。
- 上传即换素材 ID（`uploadAssetIds[field]`），浏览器本地文件名不进后端；
  EDIT 的三张图按 `image1..3` 槽位顺序提交，删除中间一张会自动重排，避免错位。

---

## 7. 打包与 CI

- 根 `Dockerfile` 新增：
  ```dockerfile
  COPY ./script/image/workflows /ruoyi/server/script/image/workflows
  ```
  （上下文本就是仓库根；`script/` 未被 `.dockerignore` 排除，但其中的 `*.md` 会被忽略，属既有现象。）
- `.github/workflows/ci.yml`：
  - 新增步骤 `Verify image workflow contracts`（`node --test ...`）；
  - 镜像冒烟新增断言：图像契约 JSON + 4 个模板文件存在。
- `.gitattributes` 新增两条（**这条不改就会被校验和反咬**）：
  ```
  script/image/workflows/api/*.json text eol=lf
  script/image/workflows/*.json      text eol=lf
  ```
  原因：模板的 SHA-256 是按**文件字节**算的，而本机 `core.autocrlf=true`。
  视频模块早就为自己的模板加过同样的规则（`script/video/workflows/api/*.json text eol=lf`）；
  如果不给图像模板加，Windows 检出会变成 CRLF、字节数 +4，后端会判定「校验值不匹配，禁止加载」，
  契约的 node 测试也会因规范化比对失败而变红。已实测确认提交后的 blob 与工作区都是 LF。

---

## 8. 验证结果与**未验证项**

### 8.1 已实测通过

| 项 | 命令/方式 | 结果 |
|---|---|---|
| 后端编译 | `mvnw -pl ruoyi-modules/ruoyi-ai -am -Dmaven.test.skip=true compile` | BUILD SUCCESS |
| 图像模块单测 | `mvnw ... "-Dtest=org.dromara.ai.image.*Test" test` | **28/28 通过**（preparer 11 / orchestrator 9 / probe 5 / dispatch 3） |
| 契约测试 | `node --test script/image/workflows/image-contracts.test.mjs` | **12/12 通过** |
| 契约 CLI | `node script/image/workflows/image-contracts.mjs` | 4 条全部 OK |
| 前端 lint | `pnpm lint`（oxlint src） | 0 问题（exit 0） |
| 前端构建 | `pnpm build:prod` | 成功；产出 `image-*.js` / `image-*.css` 分块 |
| 视图脚本自检 | `node script/ci/check-script-setup.mjs src/views/image/index.vue` | OK |
| ComfyUI 真机 | 4 个模板逐个提交 | 全部 success（见 §2.3） |

### 8.2 未验证（不得当作已通过）

1. **端到端链路**：后端 → ComfyUI → 落库 → 前端展示**没有跑过**。本机没有 MySQL/Redis，
   `192.168.2.134` 也没有可用凭据（`.ssh` 里只有 223 的密钥）。因此「页面点一下真的出图并入库」这一层
   **未验证**。
2. **SQL 未在目标库执行**：四张表与菜单行都还没建。执行前建议先按
   `script/sql/ry_video_menu.sql` 的先例做预检（`menu_id` 920002/920003 是否空闲、`sys_menu` 列清单是否一致）。
3. **工作流仍为 DRAFT**：即使部署完成，页面提交按钮也会**禁用并显示原因**——这是有意设计
   （模板导入 ≠ 服务已联通）。要真正可提交，需要业务批准后把契约里的 `status` 提升为 `TESTING`/`PUBLISHED`，
   并同步 `meta.status` 与 node 测试断言（视频模块正是在这里留下了不一致，见下条）。
4. **视频模块的 node 测试是红的（历史问题，本次未修）**：
   `node --test script/video/workflows/import-h3.test.mjs` → 3/3 失败，断言写 `status === 'DRAFT'`
   而契约里三个 H3 已是 `PUBLISHED`。契约/文档/测试三者不一致，属于既有状态（工作树干净）。
   我**没有**擅自改动视频模块；CI 里也只接入图像模块的 node 测试，避免把一个历史红灯变成阻断。
5. **`H3TemplatePreparerTest.exportGraph` 在 Windows 上必然失败**：它写 `/tmp/graph.json`，
   在 Windows 上路径变成 `\tmp\graph.json` 导致 `NoSuchFileException`。这是**环境差异，非缺陷**，
   Linux CI 正常。因此本机全量 `mvnw verify` 不会全绿（该用例是唯一失败项）。
6. **ComfyUI 侧的 cuDNN 补丁未固化**：`custom_nodes/disable_cudnn_sdpa.py`（解决 A100 + cuDNN 9.19 上
   `NoSuchFileException`… 准确说是 `cuDNN Frontend error: No valid execution plans built`）是**环境侧**
   改动，不在本仓库，也没有写进任何镜像。换 ComfyUI 主机需要另行处理。
7. **并发/多卡**：图像模块的执行器默认并发 1、无 worker pool、无 VRAM 闸门（视频模块有）。
   多卡调度与并发行为**未验证**。

---

## 9. 启用方式

```yaml
image:
  enabled: true
  contract-root: script                      # 契约与模板所在的后端受控目录
  storage-root: /opt/ai-video-poc/data/image-assets
  comfy-base-url: http://192.168.2.223:8188  # 不要填 127.0.0.1
  comfy-allow-loopback: false
  poll-budget-seconds: 300
  poll-interval-seconds: 3
  require-published: true                    # 正式环境必须 true
  concurrency: 1                             # 与 ComfyUI 实例/显卡数一致
  queue-capacity: 16
```

环境变量（relaxed binding）：`IMAGE_ENABLED` / `IMAGE_CONTRACT_ROOT` / `IMAGE_COMFY_BASE_URL` /
`IMAGE_STORAGE_ROOT` / `IMAGE_REQUIRE_PUBLISHED` / `IMAGE_TESTING_WORKFLOWS`。

隔离联调时用 `IMAGE_REQUIRE_PUBLISHED=false` + `IMAGE_TESTING_WORKFLOWS=wf-t2i-qwen21,...`，
与视频模块同款开关。

---

## 10. 后续执行顺序（建议）

1. 在目标库执行 `ry_image_task.sql` / `ry_image_workflow.sql` / `ry_image_menu.sql`（先预检菜单 ID）；
2. 部署带本模块的后端镜像，配置 `IMAGE_*`（尤其是 `comfy-base-url` 不要填回环地址）；
3. 用隔离实例把 `IMAGE_REQUIRE_PUBLISHED=false` + `IMAGE_TESTING_WORKFLOWS=wf-t2i-qwen21` 打开，
   跑一次真实的 `/image/capabilities` → 上传 → 建任务 → 执行 → 轮询 → 素材下载；
4. 四个能力逐个真机验收（含 alpha 与尺寸断言）→ 填实契约 `perf` → 业务批准后提升状态；
5. 前端与后端**同一提交**部署后再做真实浏览器点击验证。

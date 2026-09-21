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

### 3.2 「默认关闭」曾经等于「默认起不来」（已修，含视频模块）

**这是本次发现的部署级隐患，直接影响上线顺序，务必先看这一条。**

模块的装配类带 `@ConditionalOnProperty(prefix="image", name="enabled", havingValue="true")`，
但控制器原本是**无条件注册**的 `@RestController`，而它的构造参数（契约注册表、模板填充器、
编排器、素材门面…）全部来自那个被跳过的配置类。于是属性缺失——也就是本模块的**默认状态**——
容器会直接失败：

```
UnsatisfiedDependencyException: Error creating bean with name 'imageCreationController':
Unsatisfied dependency expressed through constructor parameter 0:
No qualifying bean of type 'org.dromara.ai.image.service.ImageWorkflowContractRegistry' available
```

后果不是「图像功能没启用」，而是**整个若依平台起不来**：部署新镜像时只要忘了设
`IMAGE_ENABLED=true`，整站不可用。

**修法**：给控制器补上与配置类同款的条件注解（`ImageCreationController`、以及同样有此问题的
`VideoCreationController`），并由测试守住：

| 测试 | 覆盖 |
|---|---|
| `ImageModuleWiringTest`（新增 4 条） | 默认/显式关闭时上下文正常启动且无控制器与注册表；`enabled=true` 但缺 `comfy-base-url` 时**快速失败并指明原因**；`enabled=true` 时控制器与全部协作 Bean 就位、默认值与契约一致（requirePublished=true、并发 1、队列 16、契约 4 条全部加载） |
| `VideoCreationControllerGatingTest`（新增 2 条） | 视频模块同样的两个「默认关闭必须能启动」场景 |

> 用 `ApplicationContextRunner` 离线验证，不需要数据库 / Redis / ComfyUI。
> **写这类测试的一个坑**：`ApplicationContextRunner.run()` **不会**把启动失败抛出来，
> 失败是交给消费方的（`context.getStartupFailure()`）。用 `assertThatThrownBy(() -> runner.run(...))`
> 去断言「启动失败」会永远通过不了预期——反过来写也会变成一条永远为真的空测试。

### 3.3 图像不需要 ffmpeg

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
| `script/sql/ry_image_menu_precheck.sql` | **执行前预检（只读）**：菜单 ID 是否占用、授权推导数据源、权限标识冲突、AI工具分类是否存在、审计字段是否存在、`sys_menu` 列清单、图像业务表是否已存在、菜单总数基线 |

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
  - 新增步骤 `Verify workflow contracts`：**同时**跑视频与图像两个契约测试。过去这些测试没接进 CI，
    于是视频侧三处断言与契约长期漂移却无人发现（见 §8.4）；
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
| `ruoyi-ai` 全模块单测 | `mvnw -pl ruoyi-modules/ruoyi-ai -am "-Dmaven.test.skip=false" "-Dtest.groups=!exclude" test` | **155/155 通过**（含视频模块；修掉 exportGraph 的 Windows 路径问题前是 141/142；加入装配守卫与联调暴露缺陷的回归测试后为 155） |
| 全量 reactor | `mvnw "-Dmaven.test.skip=false" "-Dtest.groups=!exclude" verify` | **BUILD SUCCESS**；`check-test-results.py` → **176 executed / 1 skipped / 0 failures / 0 errors** |
| 契约测试（视频） | `node --test script/video/workflows/import-h3.test.mjs` | **3/3 通过**（修复前 3/3 失败，见 §8.4） |
| 契约测试（图像） | `node --test script/image/workflows/image-contracts.test.mjs` | **12/12 通过** |
| 契约 CLI | `node script/image/workflows/image-contracts.mjs` | 4 条全部 OK |
| 前端 lint | `pnpm lint`（oxlint src） | 0 问题（exit 0） |
| 前端构建 | `pnpm build:prod` | 成功；产出 `image-*.js` / `image-*.css` 分块 |
| 视图脚本自检 | `node script/ci/check-script-setup.mjs src/views/image/index.vue` | OK |
| ComfyUI 真机 | 4 个模板逐个提交 | 全部 success（见 §2.3） |

### 8.2 未验证（不得当作已通过）

1. ~~**端到端链路**：~~ **已于 2026-09-21 在 192.168.2.134 的隔离实例上跑通**（4/4 能力，见 §11）。
   仍未做的是「真实浏览器点击页面」与「生产环境端到端」——生产尚未部署本模块。
2. ~~**SQL 未在目标库执行**：~~ **已于 2026-09-21 在生产库 `ai_video_poc` 执行**（预检 → 建表 → 菜单，见 §11）。
3. **工作流仍为 DRAFT**：即使部署完成，页面提交按钮也会**禁用并显示原因**——这是有意设计
   （模板导入 ≠ 服务已联通）。要真正可提交，需要业务批准后把契约里的 `status` 提升为 `TESTING`/`PUBLISHED`，
   并同步 `meta.status` 与 node 测试断言（视频模块正是在这里留下了不一致，见下条）。
4. **视频模块 node 测试与 Windows 路径问题**：已在本轮一并修掉，见 §8.4（不再是未验证项）。
5. **ComfyUI 侧的 cuDNN 补丁未固化**：`custom_nodes/disable_cudnn_sdpa.py`（解决 A100 + cuDNN 9.19 上
   `cuDNN Frontend error: No valid execution plans built`）是**环境侧**改动，不在本仓库、也没写进任何镜像。
   换 ComfyUI 主机需要另行处理。
6. **并发/多卡**：图像模块的执行器默认并发 1、无 worker pool、无 VRAM 闸门（视频模块有）。
   多卡调度与并发行为**未验证**。

---

## 8.3 遗留项（与视频模块相关，未改动）

**契约状态是业务决定，我没有替业务做**：视频契约里三个 H3 条目当前是 `PUBLISHED`，而
`meta.status` 是 `DRAFT`、`docs/video-module-implementation-handoff.md` §8.2 第 4 条把「提升为
PUBLISHED」列为**待业务批准**、§8.3 又说明生产端到端尚未验证。
也就是说这份契约内部就自相矛盾（`meta.status=DRAFT` vs 条目 `PUBLISHED`）。

因此我**没有**改动任何一方的状态，只把那条脆弱的硬编码断言换成了生命周期不变量
（合法取值 + 「宣称可用就必须带真实校验值与运行期字段」）。要落定这件事，需业务二选一：

- **维持 DRAFT**（更保守，符合文档现状）：把契约三个 H3 的 `status` 与 `meta.status` 一起改回 `DRAFT`，
  并同步 `frontend/src/views/video/modules.ts` 的说明——三处必须一致，否则提交按钮的可用性会与文档说法打架；
- **确认可以 PUBLISHED**：把 `meta.status` 改成 `PUBLISHED`，并补上业务批准依据（生产提交按钮将因此放开）。

---

## 8.4 本轮顺手修复的历史问题（视频模块，均为断言/环境问题，运行行为零改动）

| # | 问题 | 现象 | 修法 | 证据 |
|---|---|---|---|---|
| 1 | `import-h3.test.mjs` 状态断言漂移 | `assert.equal(binding.status, 'DRAFT')`，而契约已是 `PUBLISHED` → 3/3 红 | 改为断言生命周期不变量（合法取值 + 宣称可用必须有真实 checksum / maxDurationSeconds / perf） | 3/3 通过 |
| 2 | 同一文件 `fixedFieldValidation` 全等断言 | 契约后来补了 `supportedTiers`，`deepEqual` 失败 | 改为分别断言 `tier`/`dur`，并对 `supportedTiers` 断言「存在则必须包含 tier」 | 同上 |
| 3 | 同一文件截断关系断言 | `sourceDurationSeconds > maxDurationSeconds`，而后者已从 5 调成 20（API 上限）→ 恒假 | 改为断言真实截断语义：原生时长 > **交付档位时长**，且 `maxDurationSeconds ≥ 档位时长` | 同上 |
| 4 | `H3TemplatePreparerTest.exportGraph` 在 Windows 必失败 | 硬编码 `/tmp/graph.json`，Windows 解析成 `\tmp\graph.json` → `NoSuchFileException` | 改用 `System.getProperty("java.io.tmpdir")`（Linux 仍是 `/tmp`，行为不变） | `ruoyi-ai` 模块测试 **142/142 通过**（原 141/142） |

> 第 1~3 条是同一类问题：**契约演进了，测试没跟着走，而测试又没进 CI，于是没人发现**。
> 这也是为什么本次把两个模块的契约测试一起接进 CI。


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

> **部署须知**：不设 `IMAGE_ENABLED` 时，模块**完全不注册**（控制器与所有 Bean 都不创建，
> 接口不存在）——这是「默认关闭」的正确含义，由 `ImageModuleWiringTest` 守住。
> 反过来说，`IMAGE_ENABLED=false` 与「不配」都不会让平台启动失败（见 §3.2 修掉的那个隐患）。
> 要启用必须显式 `IMAGE_ENABLED=true`，并且必须同时给 `IMAGE_COMFY_BASE_URL`，
> 否则容器会在启动时快速失败并指明是哪个配置缺失。

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

---

## 11. 执行记录（2026-09-21）：SQL 已上生产 + 隔离实例端到端通过

### 11.1 生产库执行（已完成）

先跑只读预检 `script/sql/ry_image_menu_precheck.sql`，10 项全部符合预期（菜单 ID 920002/920003 空闲、
授权可推导、`image:creation%` 无冲突、AI工具分类存在、`sys_menu` 22 列一致、审计字段存在）。
随后按 `ry_image_task.sql` → `ry_image_workflow.sql` → `ry_image_menu.sql` 顺序执行：

| 项 | 变化 |
|---|---|
| 表总数 | 159 → **163**（`image_task` / `image_asset` / `image_task_event` / `image_workflow_version`） |
| `sys_menu` | 331 → **333**（`920002 图像创作` 挂在 AI工具 下 order 4；`920003 提交图像任务`） |
| `sys_role_menu` | +2 行（角色 `1761300000000000003` / test1，由「视频创作」已有授权推导） |
| 备份 | `/home/aiadmin/image-sql/sys_menu_backup_20260921-091907.sql`（97619 字节） |

另外补了一列：`image_workflow_version.supported_outputs_json`（见 §11.3 第 1 条）。

### 11.2 隔离实例端到端（已完成，4/4 通过）

做法与视频模块当年一致：**现成生产镜像 + 只读挂载本次构建的 jar 覆盖 `app.jar`**，
契约挂到 `/ruoyi/server/image-script`（不覆盖镜像原有的 `script/`），连生产的 MySQL/Redis，
监听 `127.0.0.1:18084`，`VIDEO_ENABLED` 保持不设。**未修改任何生产容器**（执行前后
`ai-video-poc-backend-1` 一直是 `Up ... (healthy)`）。

实测（模型已驻留显存，单次）：

| 能力 | 任务号 | 耗时 | 产出实测 | DB 记录 | 事件序列 |
|---|---|---|---|---|---|
| 文生图 T2I | `IMAGE-20260921-819010` | 12.1s | 1024×1024 RGBA | 1024×1024 / alpha | CREATED → SUCCEEDED |
| 图生图 I2I | `IMAGE-20260921-584130` | 12.1s | 896×1152 RGBA | 一致 | 同上 |
| 指令改图 EDIT | `IMAGE-20260921-662529` | 20.1s | 896×1152 RGBA | 一致 | 同上 |
| 抠图 BGREMOVE | `IMAGE-20260921-253313` | 16.1s | 896×1152 RGBA | 一致 | 同上 |

同时验证：登录鉴权（`videoit`）、素材上传返回 assetId、素材列表、`/content` 返回真 PNG、
`/thumbnail` 返回 JPEG、**非本人/不存在素材返回 400「素材不存在或无权访问」**（不泄露存在性）、
`/image/capabilities` 四条工作流状态与档位、以及**契约→`image_workflow_version` 镜像同步 4 条**
（DRAFT + 真实 checksum + 输出档位矩阵）。

### 11.3 本轮联调暴露并修掉的三个真实缺陷

> 这三个都不是「已经测过的东西坏了」，而是**离线单测/替身永远测不到**的类型：
> 真实数据库列类型、真实 SQL 占位符、真实 Spring 条件装配。

| # | 缺陷 | 症状 | 根因 | 守卫测试 |
|---|---|---|---|---|
| 1 | 契约镜像表同步参数错位 | 同步静默失败、`image_workflow_version` 恒 0 行（异常只告警不抛出） | SQL 14 个占位符却传了 15 个参数（表里没有 `supported_outputs_json` 列） | `JdbcImageWorkflowVersionRepositoryTest`（4 条，含占位符/参数一致性） |
| 2 | `TINYINT(1)` 强转 Number | **素材下载/缩略图/删除/建任务素材校验全部 500**（`ClassCastException: Boolean cannot be cast to Number`） | MySQL 驱动 `tinyInt1isBit=true` 把 `TINYINT(1)` 映射成 `Boolean` | `JdbcImageTaskRepositoryTest`（3 条） |
| 3 | 「默认关闭」导致应用起不来 | 不配 `IMAGE_ENABLED` 时整个平台启动失败 | 控制器无条件注册，依赖却来自 `@ConditionalOnProperty` 的配置类 | `ImageModuleWiringTest`（4 条）+ `VideoCreationControllerGatingTest`（2 条） |

其中第 2 条同时解释了「第一轮联调只有 T2I 建成任务」：建任务要校验输入素材归属，
而它走的就是同一条 `requireOwnedAsset`，所以只有不需要输入图的 T2I 能通过。

### 11.4 仍未完成（卡在权限/审批）

1. **推送分支与开 PR**：134 上 `GHCR_TOKEN` 的 scope 是 `read:packages`，**不能写代码仓库**，
   也没有 workflow dispatch 权限 → 需要用户提供具备 repo 写权限的凭据，或由用户自行推送。
2. **生产部署**：需要 `hotter-release`（root）以不可变 digest 放行；按现有流程由
   GitHub Actions 的 `deploy-poc.yml` / `deploy-frontend-poc.yml`（`[shenzhen, deploy]` runner）触发。
3. **业务批准**：四个工作流仍是 DRAFT；提升为 PUBLISHED 后才能在生产页面提交（见 §8.2 第 3 条）。
4. **真实浏览器点击**：本次只验证到接口层（用脚本打真实后端），未做浏览器点击。


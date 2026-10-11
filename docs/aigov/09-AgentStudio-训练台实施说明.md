# 岗位 Agent Studio（训练台）实施说明

> **依据**：《岗位 AI 工作台与 Agent 训练台集成详细设计 V1.1》**专题 C**（岗位 Agent Studio 训练台与对话式能力生成规范）。
> **用户 2026-10-10 拍板**：**先做专题 C**；Scenario 建轻量表、Execution/Artifact 复用既有、沙箱测试允许但默认关闭、门户新增 `/ai-workspace`。
> **对比与总设计**：见工作区 `docs/07-V2.0-岗位工作台与AgentStudio-对比与可执行设计.md`（含逐条事实盘点与待拍板清单）。
> **本文件**：记录 **Studio 这条线的每个增量做了什么、为什么、怎么验证**；随代码同批更新。
>
> **⚠️ 基线说明（2026-10-10）**：S1 的代码写在 `6503058` 上，推送前已 **rebase 到 `26663d4`**——
> 该基线上并行工作流已推入 ~100 个提交，新增 `aig_task_artifact`（制品账本，ADR-010）、
> `aig_sandbox_run` / `aig_sandbox_artifact`（沙箱运行与产物，ADR-015/016）、
> `aig_policy_decision_log`、`aig_service_token`（机器身份），并建立了 `docs/platform-v2/` 的
> **ADR-001~016 + Execution Contract v1 + 12 项冻结清单**。
> **Studio 后续增量必须对齐**：沙箱测试复用 ADR-015/016 的受限容器链路（**不自建沙箱**）、
> 门槛接既有 `release/advance`、场景/岗位**不新造审批表**（F-10）、
> 制品的"历史成果"读 `aig_task_artifact` 而不是新建 `aig_artifact*`（ADR-010）。
> 详见工作区 `docs/07-V2.0-…-对比与可执行设计.md` §1.2。

---

## 一、为什么先做这个（而不是主文档的岗位门户）

主文档（岗位包 + 门户 + Launch）依赖 `Role Package`，而 Studio 的最终验收"把 Agent 绑定到岗位卡片"也依赖它。
用户选择 **Studio 先行**，因此本线把**能力侧**闭环先做通：

```
创建草稿 → 人工调教 Prompt → 真实测试 → 提交 DRAFT 版本 → 走既有五道门槛 → STABLE
```

"绑定到岗位卡片 / 员工可见"属于 Role 线，做完 Role Package 后再补（对应附件 `STUDIO-006`）。

**一个必须先说清的前提**：现有 Registry **没有在线创建/编辑 Agent 的接口**
（`AigAgentRegistryController` 只有 list/get/version/release/advance/binding/package/skill 查询），
所以 Studio 必须自己带一条**草稿写通道**；这条通道**只写草稿**，正式版本仍只能经
`/aigov/agent/release/advance` 那台状态机产生。

---

## 二、增量 S1：契约与存储（✅ 已完成）

### 2.1 落地物

| 类别 | 文件 |
|---|---|
| 建表脚本（可重放） | `script/sql/aig_studio.sql` |
| 枚举 | `aigov/studio/enums/AigStudioDraftStatusEnum`、`AigStudioRevisionSourceEnum`、`AigStudioTestStatusEnum` |
| 内容模型 | `aigov/studio/domain/AigStudioDraftContent`（Prompt 八分节 + 角色定位/能力/IO 引用/工具与知识声明） |
| 规范化哈希 | `aigov/studio/helper/AigStudioContentHasher` |
| 实体 / VO / Mapper | `AigStudioDraft`、`AigStudioRevision`、`AigStudioExecutionLink` 各一套 |
| 测试 | `aigov/studio/helper/AigStudioContentHasherTest`（13 条） |

三张表：

| 表 | 用途 | 关键约束 |
|---|---|---|
| `aig_studio_draft` | 可编辑工作区，独立于 `aig_agent_version` | `latest_revision` 做显式 CAS；`content_hash` / `last_published_hash` 判"未发布改动" |
| `aig_studio_revision` | 每次有意义修改的**不可变快照** | `unique(draft_id, revision_no)` |
| `aig_studio_execution_link` | 测试证据链：把"测过"钉到精确内容 | 记 `revision_id` + `content_hash`（修订被删也能核对） |

### 2.2 四条关键取舍（都是"不做会出错"的那种）

1. **草稿与已发布版本分离**：正式版本不可变，而训练要反复改/回退。写成对已发布版本的 UPDATE，
   等于让线上能力随一次试改漂移且回不去。草稿 → 正式版本只经"提交"单向转换。
2. **哈希必须先规范化再算**（`AigStudioContentHasher`）：页面按
   `contentHash != lastPublishedHash` 显示「未发布改动」。若直接对原文算哈希，
   **序列化换了键顺序、多一个换行**都会让界面无端喊"你有未发布改动"；
   反过来（真改动被判成没改）就会漏发布。所以：键排序、去空白、数值归一
   （`1` 与 `1.0` 视为同一内容）。规范化**幂等**已被测试钉住。
3. **测试证据钉 `revision_id` 而不是 `draft_id`**：只记草稿的话，测完又改了草稿，
   这条证据就指向一段**从未被测过的内容**——而发布门槛要的正是"这份内容被真实跑通过"。
4. **`latest_revision` 不用 `@Version`**：注解式乐观锁在**任何**更新时自增，
   而"修订号"只应在**内容变化**时前进（改状态、记 `agentVersionId` 都不算新修订）。
   所以用普通列 + 服务层显式 CAS（`where latest_revision = expected`）。

### 2.3 刻意不做（S1 阶段）

- 不做 AI 培训助手（Copilot 提案）——P1，见附件 `STUDIO-007`；
- 不做 Skill / 子 Agent / 快捷指令的草案生成——P1，`STUDIO-008/009`；
- 不做 Tool / Knowledge 绑定面板——本仓没有对应绑定表，P0 只存声明并如实标注；
- 不给 VO 预置"标签字段"（如状态中文名）——**没人填的字段就是又造一个"永远为空"的列**，
  等 S3 服务层真正填充时再加。

### 2.4 验证

| 项 | 结果 |
|---|---|
| aigov 单测 | 本增量 **+13**（键顺序/空白/嵌套排序/数组顺序/数值归一/转义/非 ASCII/幂等/非法输入/null/真实内容模型/分节键固定）。**计数口径**：写出时为 `477 → 490`；rebase 到 `26663d4` 后本模块共 **673/673 全绿**（含本增量这 13 条） |
| Mapper↔VO 守卫 | 通过（`AigMapperVoConverterCoverageTest`：三个新 VO 的 `@AutoMapper` 都生成了转换器） |
| 真库探针（临时 MariaDB 3426） | 脚本 exit=0；三表列数 **18 / 14 / 17**；唯一键 = 三个 PRIMARY + `uk_aig_studio_revision(draft_id, revision_no)`；默认值 `MANUAL`/`PENDING` 生效；**重复修订号被 1062 拒绝**；**重放 exit=0 且既有行一字不动**；`content_hash` 为 `char(64)` |

### 2.5 部署（存量库可直接重放）

```bash
# 三张表都是 create table if not exists；不含 ALTER，因此存量库/全新库都可直接跑，可重复执行
script/sql/aig_studio.sql
```

⚠️ 主键由 MyBatis-Plus 雪花生成（**无 AUTO_INCREMENT**），手工插数据必须显式给 ID。

---

## 三、增量 S2：草稿读写（✅ 已完成）

> 用户 2026-10-10 拍定：**S2 只做草稿读写，把"测试"整体留到 S5**。
> 因此本层**不注入任何模型调用/任务/沙箱**——草稿服务只要"能顺手调一次模型"，
> "打开训练台"就会变成一次计费调用。

### 3.1 落地物

| 类别 | 文件 |
|---|---|
| 入参 | `studio/domain/bo/AigStudioDraftCreateBo`、`AigStudioDraftSaveBo` |
| 详情视图 | `studio/domain/vo/AigStudioDraftDetailVo` |
| 服务 | `studio/service/IAigStudioDraftService` + `service/impl/AigStudioDraftServiceImpl` |
| 测试 | `studio/service/impl/AigStudioDraftServiceImplTest`（19 条） |

能力：`createDraft`（内容为空时填八分节标准骨架）、`getDraft`、`saveDraft`、
`listRevisions`、`getRevision`、`rollback`、`archive`。

### 3.2 四条不变式（都有用例钉住）

1. **编辑必须带 CAS 版本**：`expectedRevision` 与库中不一致 → 报错，消息里同时给出
   "你手上的修订号"和"库中当前"；并且**一个字都不写**（连修订都不产生）。
   实现是两层的：读后先比一次（快速失败），写时再用
   `where latest_revision = 读到的值` 做一次真正的 CAS——**读之后写之前被别人改过**这种情况
   只有数据库那一层能拦住（用例 `saveReportsDbLevelConflict` 专门模拟 rows=0）。
2. **内容没变不产生新修订**：只比"键顺序/空白不同"的内容被哈希判为相同 →
   不写库、不动修订号。否则每次点保存都多一条历史，版本记录会被"其实什么都没改"的噪声淹掉。
3. **修订不可变、回滚不改历史**：回滚到修订 #n 不修改那条旧修订，而是
   **新增一条内容相同、来源为 `ROLLBACK` 的新修订**；若目标内容与当前相同则什么都不做。
4. **写操作认责任人**：非责任人改/归档 → 拒绝（读取不设此限，可见性由 S3 的权限点决定）。

### 3.3 其他取舍

- **入库的是规范化内容**（`AigStudioContentHasher.canonicalize` 的结果）而不是原文：
  这样库里那串字节与哈希永远可互相复算，Diff 也不会被空白差异污染。
- **`revisionCreated` 由服务端返回**：内容未变时前端需要知道"这次保存没有产生新修订"，
  否则页面会以为版本号+1了。
- **`unpublishedChanges` 由服务端算**（`contentHash != lastPublishedHash`，从未提交视为有改动）：
  放服务端算，刷新页面/换设备都不会丢或误报。
- **状态中文描述由服务端填**（`statusLabel`），避免每个页面各写一套映射。

### 3.4 验证

| 项 | 结果 |
|---|---|
| aigov 单测 | **+19**（创建写草稿+第 1 修订/空内容填骨架/坏输入不落库/陈旧版本拒绝且不写/内容未变不产生修订/内容变则 +1 且 CAS/DB 层 CAS 0 行报冲突/非责任人拒绝/归档拒绝/缺 expectedRevision 拒绝/回滚产生新修订且历史不动/回滚到当前内容不产生噪声/目标修订不存在/未发布改动三态/状态描述/修订列表倒序/修订不存在/归档终态且重复归档报错/非责任人不能归档）。**本模块 673 → 692 全绿** |

### 3.5 刻意不做（S2 边界，避免与 S5 混在一起）

- 不做 HTTP 接口与权限/菜单（S3）；
- 不做任何模型调用、沙箱执行、黄金用例（S5）；
- 不做"从现有 Agent 复制"的自动预填（需要读 `aig_agent_version` 的字段映射口径，随 S3 一起定）；
- 不做 `submit`（产生 `aig_agent_version` DRAFT）——那是 S3，且必须接既有发布状态机。

---

## 四、增量 S3：HTTP 面 + 权限/菜单 + 静态预检 + 提交（✅ 已完成）

### 4.1 落地物

| 类别 | 文件 |
|---|---|
| 接口 | `studio/controller/AigStudioDraftController`（`/aigov/studio/drafts`） |
| 入参/出参 | `AigStudioDraftQueryBo`、`AigStudioDraftRollbackBo`、`AigStudioValidateVo`；`AigStudioDraftVo` 增 `statusLabel`/`unpublishedChanges` |
| 操作者解析 | `studio/helper/AigStudioActorProvider` + `LoginStudioActorProvider` |
| 权限常量 | `AigConstants` 新增 `PERM_STUDIO_DRAFT_{LIST,QUERY,CREATE,EDIT,VALIDATE}` |
| 权限种子 | `script/sql/aig_studio_menu.sql` |
| 服务新增 | `queryPage`（分页 + 服务端填标签/未提交改动）、`validateDraft`（静态预检） |
| 测试 | `AigStudioDraftControllerTest`（4 条）+ 服务测试扩到 **24** 条（预检 4 + 清单 1） |

七个入口：`GET /list`、`GET /{id}`、`POST`（建）、`PUT /{id}`（存）、`GET /{id}/revisions`、
`GET /revision/{revisionId}`、`POST /{id}/validate`、`POST /{id}/rollback`、`POST /{id}/archive`。

### 4.2 三条刻意的取舍

1. **本控制器里没有任何推进发布状态的入口**：训练台的「部署」不是直改状态，
   正式发布只能经 `/aigov/agent/release/advance` 那台状态机（五道门槛）。
   第二条写通道一旦存在，门槛就形同虚设。
2. **保存以路径上的 `draftId` 为准**，请求体里的 ID 被覆盖：否则会出现"路径写着 A、实际改了 B"的越权面。
3. **操作者从登录态取、取不到就拒绝**：训练台是人工工作台，"系统自动改了别人的草稿"不是它的语义
   （与任务域刻意不同——那里无登录上下文是常态）。草稿责任人之外的写操作由服务层拒绝。

### 4.3 静态预检（`validateDraft`）的规则

一次列全，不抛第一个错（预检是给正在编辑的人看的）。规则都是**可判真假**的：
缺分节（八个标准分节是 Diff 与缺节校验的唯一口径）、全部分节为空、未声明 `providerCapability`、
`allowExternal` 非 Y/N、`inputSchema`/`outputSchema` 不是合法 JSON、
**页面定制里出现 `<script` / `javascript:`**（专题 C §C6：门户只接受安全组件白名单，不接受任意脚本）。
结论带 `revision` + `contentHash`，证明"检的是这一版"，且**只读**（不产生版本、不发布）。

### 4.4 为什么 S3 只种权限行、**不**种页面菜单行

页面组件（`aigov/studio/index`）要到 S4 才存在。先种菜单行会得到一个"点进去空白"的入口——
比"暂时没有入口"更糟（用户会以为坏了）。因此 S4 与前端页面一起再出 `aig_studio_pages.sql` 补页面行及其父菜单授权；
而权限行必须现在种：权限串只有进了 `sys_menu.perms` 才有意义，少一个就是**该接口对所有人 403**
（守卫 `AigPermissionSeedCoverageTest` 会在构建期抓住它）。
授权范围**只给 aig_admin**：治理审阅者（security/viewer）不默认获得草稿写权，
业务专家角色应由运维按需新建后单独授权。

### 4.5 验证

| 项 | 结果 |
|---|---|
| aigov 单测 | **692 → 701**（+9：接口层 4、预检 4、清单标签 1），且权限种子守卫通过 |
| 真库探针（临时 MariaDB 3427） | `ry_vue.sql` + `aig_ai_gov_menu.sql` + 本脚本 exit=0；**5 个权限行**、均授予 aig_admin、security/viewer **0**、父菜单已授权；**重放 exit=0 且无重复行**；当前**没有**指向 `aigov/studio` 的页面行（S4 补） |

### 4.6 `submit` 为什么留到下一步（需要你定一件事）

`submit` 要把草稿固化成一条 `aig_agent_version`（`release_status=DRAFT`），而该表要求
**`agent_id` 非空**。于是出现两条路，它们的语义差别很大、不该由我替你选：

- **A. 只支持"绑定到已存在的 Agent"**：`submit` 要求草稿已有 `agentId`（从现有 Agent 复制而来）；
  从零创建的草稿暂时不能提交。改动小、不新增对 `aig_agent` 的在线写通道。
- **B. 同时支持"从零创建 Agent 定义"**：`submit` 在 `agentId` 为空时先建一条 `aig_agent`
  （需要给 `category` 取值——当前只有 `PLANNING/VISUAL_DNA/GENERATION/QA` 四个枚举，
  且文档 C3.1 明确警示不要把岗位名塞进去），再建 DRAFT 版本。
  这正是附件「从零创建 Agent 草稿」的完整形态，但也意味着**平台首次出现在线创建 Agent 定义的写通道**。

另外 `version` 字符串的来处也要定：由调用方显式给（我倾向这个，唯一性校验即可），
还是按修订号自动生成。

> **2026-10-10 用户选择：B（同时支持从零创建 Agent 定义）**，因此下面 §4.7 按 B 实现；
> 版本号采取"调用方给就用给的、不给按 `0.1.<修订号>` 生成，被占用则报错要求显式指定"。

### 4.7 提交（`submitDraft`）：按 B 实现

**它只做到 DRAFT 为止**：不推进发布状态、不跑沙箱、不做审批。后续门槛仍走既有
`/aigov/agent/release/advance`——训练台提供第二条写状态通道的话，五道门槛就形同虚设。

流程与四条刻意的约束：

1. **预检必须先过**：内容不合格直接拒绝并列出问题，不允许"先提交、后面再补"
   （那会让一份不合格内容进入发布流程）。
2. **类别必须由草稿显式声明**（`agentCategory`）：本仓只有四个与创作工厂**具体实现**绑定的类别
   （`PLANNING`=详情页策划 / `VISUAL_DNA` / `GENERATION` / `QA`）。服务层替它挑会挑出一个与内容
   毫不相干的实现绑定；而新增类别会动枚举与契约，属独立变更。所以没写或写错 → **明确拒绝并列出可用值**，
   报错里同时说明"非创作类 Agent 需要的新类别不在本增量内"。
3. **从零创建时，编码已被占用就拒绝**，不"顺手绑上去"——那会把别人的 Agent 变成这份草稿的产物；
   正确做法是改为"基于它创建草稿"。
4. **版本号被占用报错要求显式指定**，不自动跳到下一个——"悄悄换一个版本号"会让调用方
   以为发布的还是它要的那个版本。

映射到 `aig_agent_version`：`release_status=DRAFT`、`release_channel=TESTING`（**显式给**，不依赖 DDL 默认值）、
`prompt_template` 由八个分节拼成（`## 分节名` + 正文）、`config_json` 存**提交时那一版内容原文**
（事后回答"这条版本当时是什么内容"不必靠拼凑）、`allowed/forbidden_tools`、`knowledge_scope_json`、
`provider_capability`、`allow_external` 均来自草稿内容。回写草稿：绑定 Agent（若本次新建）+
`agentVersionId` + `lastPublishedHash`（否则页面会一直显示"有未发布改动"）+ 状态 `SUBMITTED`，
且**仍带 CAS**（`where latest_revision = 读到的值`）——提交期间别人改了内容，就不能算"这版提交成功"。

新增权限点 `aig:studio:draft:submit`（**与编辑分开授权**：能改草稿 ≠ 能把草稿变成版本候选）。

**S3b 验证**：aigov **701 → 709** 全绿（+8：从零创建建 Agent+DRAFT 版本并回写草稿、已绑定则复用不新建、
预检不过拒绝、缺类别拒绝并列出可用值、版本号被占用拒绝、编码被占用拒绝不"顺手绑"、非责任人拒绝、已归档拒绝）；
权限种子守卫通过（第 6 个权限点已种子）。

---

## 五、增量 S4：训练台前端 + 页面菜单（✅ 已完成）

### 5.1 落地物

| 类别 | 文件 |
|---|---|
| 接口封装 | `frontend/src/api/aigov/studio/index.ts` + `types.ts` |
| 差异逻辑（纯函数） | `frontend/src/views/aigov/studio/diff.ts` + `diff.spec.ts`（9 条） |
| 页面 | `frontend/src/views/aigov/studio/index.vue`（列表 + 双栏编辑器） |
| 页面菜单 | `script/sql/aig_studio_pages.sql`（页面行 + 父菜单授权，**只给 aig_admin**） |

页面形态：上方草稿列表（编码/状态/修订号/**未发布改动**/已提交版本），
「打开」进入双栏编辑器——**左**：Agent 名称、类别、角色定位、责任、禁止事项、所需能力、允许外发、
八个 Prompt 分节、输入/输出 Schema；**右**：修订历史 + 逐节差异 + 回滚。
底部：预检 / 保存 / 提交（产出 DRAFT 版本）。

### 5.2 四条刻意的取舍

1. **差异逻辑抽成纯函数并单测**：差异算错不会报错，只会高亮错的节——而人正是靠它决定要不要回滚。
   标准八节永远出现在结果里（空的也让人看到"这一节是空的"），非标准键也参与比较（"多出来的一节"同样是改动）。
2. **「未保存改动」用载入标记 + 深度 watcher**：少了标记，watcher 会在载入后立刻把状态置成"有改动"，
   页面一打开就喊"未保存"并挡住预检/提交。载入本身不是用户的改动。
3. **预检/提交只针对「已保存的内容」**：界面明说并要求先保存——预检校验的是库里的内容，
   对未保存的编辑做预检会给出一个"看起来通过了"的假结论。
4. **页面上没有"一键发布"**：提交只产出 DRAFT 版本，文案里明确写出"之后仍走既有门槛"。

### 5.3 为什么页面菜单要单独一份脚本（晚于权限行）

权限行必须早种（权限串不进 `sys_menu.perms` 就是接口对所有人 403，构建期守卫会抓）；
但页面行的 `component` 指向 `aigov/studio/index`，此前**页面并不存在**——
先种菜单会得到一个"点进去空白"的入口，比"暂时没有入口"更糟。所以 S4 与前端页面一起补页面行。
页面行**只按 menu_id 判存在**（不能带 perms 条件，否则与权限行同名会永远插不进去），并显式补父菜单授权。

### 5.4 验证

| 项 | 结果 |
|---|---|
| 前端类型/静态检查 | `vue-tsc --noEmit` **0 error**；`oxlint src` **0 warning / 0 error**（478 文件） |
| 前端单测 | 本增量 **+9**（键顺序/空白/嵌套/数组/数值归一无关的分节比较、标准八节常在、增删节标记、非标准键排序、坏 JSON 不抛、空值/非对象、非字符串值、JSON.parse 未被吞）；全量 **31 文件 / 329 用例通过** |
| 真库探针（临时 MariaDB 3428） | `ry_vue` + `aig_ai_gov_menu` + `aig_studio_menu` + 本脚本 exit=0；页面行 1 条（`aigov/studio/index`、perms=`aig:studio:draft:list`）、授予 aig_admin、security/viewer **0**、父菜单已授权；**重放 exit=0 且无重复行**；页面 perms 能在权限行里对上（否则入口不可见/403） |

> ⚠️ **本机跑全量前端单测时有一条与本次无关的失败**：`views/creative/composables/uiCopy.spec.ts`
> 断言的是**源码里的 LF 子串**，而本机 `core.autocrlf=true` 让检出的 `.vue` 变成 CRLF（实测
> `CreativeWorkspace.vue` 有 592 处 CRLF），因此仅在本机失败；该文件未被本次改动触及
> （最近一次提交是上游 `a5e0411`），CI 的 Linux 检出为 LF 可通过。排除它后其余 **31/31 文件全绿**。

---

## 六、增量 S5：训练台测试调用（✅ 后端已完成；UI 入口待补）

### 6.1 它是什么、不是什么

**是**：把某一版草稿内容真的发一次出去、看它产出什么，并把这次调用钉进
`aig_studio_execution_link` 证据链。
**不是**：ADR-015/016 那个"宿主侧进程 + 一次性受限容器"的沙箱——那条链是给
**执行不可信代码/脚本**用的。本增量只发一次受治理的模型调用，**不自建任何执行沙箱**。
将来若草稿生成出脚本型 Skill，**它的执行必须走 ADR-015 那条链**，不能借这个接口。

### 6.2 落地物

| 类别 | 文件 |
|---|---|
| 开关 | `studio/config/AigStudioTestProperties`（`aigov.studio.test.*`；`enabled` **默认 false**、`max-input-chars=4000`、`output-preview-chars=2000`） |
| 服务 | `studio/service/IAigStudioTestService` + `impl/AigStudioTestServiceImpl` |
| 接口 | `studio/controller/AigStudioTestController`（`POST /aigov/studio/drafts/{id}/test-runs`、`GET /aigov/studio/test-runs/{id}`） |
| 入参/出参 | `AigStudioTestRunBo`（`dataLevel`/`input` 必填）、`AigStudioTestRunVo` |
| 共用拼装 | `studio/helper/AigStudioPromptBuilder`（**提交与测试用同一份**，否则"测的"和"提交的"会漂移） |
| 权限 | `aig:studio:test:run`、`aig:studio:test:view`（+ 菜单行，只给 aig_admin） |
| 测试 | `AigStudioTestServiceImplTest`（12 条） |

### 6.3 三条不可让步的约束

1. **默认关闭**：开着它就能花真钱。关着时直接拒绝并说清原因（`aigov.studio.test.enabled`）。
2. **必须走网关**（`IAigInvokeService`）：策略校验与配额在**网关执行点**上（ADR-006 要求落成代码），
   绕过它就等于给训练台开了一条"不经治理的模型通道"。本开关**不是**"绕过策略"的开关。
3. **不把测试调用算成灰度数据**：调用**不带 `agentVersionId`**。带了的话，训练台点几次测试
   就会把该版本的 CANARY 调用计数/失败率抬上去——那是**用测试伪造灰度证据**。
   版本号只记在我们自己的证据表里（`aig_studio_execution_link.agent_version_id`）。
   这条有专门用例钉住（`successWritesEvidenceAndReturnsPreview` 断言 `agentVersionId` 为 null）。

### 6.4 其他刻意的取舍

- **证据先落、调用后补**：进网关前先插一条 `RUNNING` 行，回来再更新为 `SUCCEEDED`/`FAILED`。
  崩在调用中间也留下"这次可能真的调用了"的痕迹——比"崩了就当没调过"诚实（费用可能已产生）。
- **网关抛异常也把证据收尾成 FAILED**：不留一条永远 `RUNNING` 的行。
- **`dataLevel` 必填**：若默认成 INTERNAL，就会出现"按 INTERNAL 测通、按 RESTRICTED 上线"——
  那样的测试恰好证明了**错误的那件事**。
- **成功判据与任务执行同一口径**：必须有输出且无错误码（只看一者都会误判）。
- **输出预览标明是否截断**：不标的话人会以为模型只输出了这么点。
- **操作者必须是草稿责任人**：测试会花钱，必须能追到人。
- **`@RepeatSubmit`**：防连点（每次点击都是一次真实调用）。

### 6.5 验证

- aigov **709 → 721** 全绿（+12：默认关闭拒绝、等级必填/非法拒绝、输入空/过长拒绝、
  非责任人拒绝、已归档拒绝、缺能力/缺修订拒绝、成功路径（证据先落 RUNNING→调网关→SUCCEEDED、
  **断言不带 agentVersionId**、摘要/traceId 正确、prompt 含分节与测试输入）、输出截断标记、
  有错误码→FAILED、网关抛异常也收尾 FAILED、读证据不再调用、读证据不存在报错）；
- 权限种子守卫通过（第 7/8 个权限点已种子）。

### 6.6 待补（本增量唯一剩下的）

**页面上的"测试"入口尚未加**：后端接口、权限、菜单行都已就绪，但训练台编辑器里还没有
那个按钮与结果面板。补它属于纯前端改动（数据等级选择 + 测试输入 + 结果/证据展示），
不涉及任何新语义。

---

## 七、后续与独立的变更

**已完成（2026-10-11）：`AigAgentCategoryEnum` 扩展了非创作类 `ANALYSIS`（行业分析）。**

此前四个取值都与创作工厂的具体实现绑定，"行业分析"这类不产出设计物料的 Agent 无处归类，
只能硬塞进 `PLANNING`——于是按类统计与筛选都会失真。本次：

- 枚举新增 `ANALYSIS("ANALYSIS", "行业分析", …)`（第五个取值）；它的 `implementation` 是
  **说明性占位**（仓库暂无对应实现），接入真实实现时再登记——不假装已经有一个实现。
- **前端两处下拉同步补齐**：`views/aigov/studio/index.vue` 与 `views/aigov/agent/index.vue`；
  少了它们，新类别就是"配不出来"，而那种失效只会表现为"没人用"。
- 新增封闭集合守卫 `AigAgentCategoryContractTest`：取值集合**双向**断言
  （`{PLANNING, VISUAL_DNA, GENERATION, QA, ANALYSIS}`）、编码必须是大写常量形态、
  描述非空、`find` 大小写/空白容错但认不出返回 null。要扩值必须先改这条用例。
- 与 `docs/platform-v2` 的关系：该类**不在** `AigContractEnumDriftTest` 的镜像清单里
  （契约没有 Agent 类别词表），所以本次不改 `docs/platform-v2`；漂移由上面这条守卫自己兜。

**已裁定（2026-10-11）：训练台测试证据不参与发布门槛判定，只作参考。**

`SANDBOX_RUN` 的判据来源只有 `aig_sandbox_run`（宿主侧 worker 的一次性受限容器：退出码 0、未超时、
无网，见 ADR-015）；训练台的测试证据落在 `aig_studio_execution_link`，它是**一次受治理的模型调用**，
不等于隔离执行。两者混淆的失效形态正是最难发现的那种：接进门槛后会**编译过、单测过、页面还是绿灯**，
但唯一能证明"不可信代码真的在隔离环境跑起来过"的证据被换成了"模型说这次没问题"，
于是从未在沙箱验证过的外部代码可以顺利发布。

因此这条口径被钉成构建期断言：守卫 `AigSandboxEvidenceSourceContractTest` **目录扫描**
发布门槛源码（`org/dromara/aigov/agent`），要求其中不得出现 `AigStudioExecutionLink` /
`aig_studio_execution_link`，并正向断言 `SANDBOX_RUN` 的发布断言确实走 `sandboxRunEvidence`
（另有扫描文件数下限，防守卫空转）。发布推进界面的「沙箱证据」处也直接写明"测试调用只作参考、
不参与门槛判定"。要改这条口径，必须先改这条用例——那一刻人必须回答"训练台的模型调用算不算沙箱执行"。

> 口径出处：`AigReleaseGateEnum`（`basis` 指向设计 §5.4/§6.3）与 ADR-015。
> 实测 `docs/platform-v2/02-Execution-Contract-v1.md` 文本里**没有** SANDBOX_RUN/沙箱条目，
> 所以那里不是这条口径的依据。

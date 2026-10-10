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

## 四、后续增量（待做）

| # | 增量 | 关键点 |
|---|---|---|
| **S3** | API + 权限/菜单 + `validate`/`submit` | `/aigov/studio/drafts*`；`submit` **只产 `aig_agent_version` DRAFT**；`aig:studio:*` 权限 + 菜单段 `1768400…` |
| **S4** | 训练台前端 | 双栏（左配置/右 Prompt 编辑）+ 版本记录 + Diff + 提交；`STABLE` 不可原地改 |
| **S5** | 沙箱测试 | 基于不可变 revision 执行一次真实调用（**默认关闭**），走既有模型路由 + 配额，证据落 `aig_studio_execution_link`；**复用 ADR-015/016 的受限容器链路，不自建沙箱**；如需非会话身份，复用 `aig_service_token` |

**S3 动手前的核对项**：`SANDBOX_RUN` 已是"要证据"（`aig_sandbox_run`），`HUMAN_APPROVAL` 也有
ADR-014 的"管理员人工评测"通道——落地时按 `docs/platform-v2/02-Execution-Contract-v1.md`
与 `AigReleaseGateEnum` 把证据来源逐条对上（避免把 `submit` 接到一个不校验的门槛上）。

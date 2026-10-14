# 24 - Agent 平台优化 v1.0 · 总体实施与评审说明

> 依据：附件《Agent 平台优化详细设计 v1.0》（2026-10-14）
> 基线提交：`fe305fc629e9990661097a7a3be86da1fc4714e3`（附件 §1.1 指定；即拉取时的远端 `main`）
> 工作分支：`feature/aigov-optimization-v1`
> 分阶段说明：`19`（A）、`20`（B）、`21`（C）、`22`（D）、`23`（E）

---

## 1 提交序列（基线之后）

| 提交 | 阶段 | 内容 |
| --- | --- | --- |
| `f78b648b` | A | D01 执行解析器/不可变计划；D02 有效外发；D03 绑定撤销与有效期；D04 失败收口；D08 短事务；D10 助手独立能力 |
| `58dbd0c2` | B | D07 分级预检；D09 统一编译器；`GET /aigov/studio/metadata` |
| `191838fc` | B | D06 未保存保护（三选一） |
| `cb8de099` | B | D11 启动任务模板（员工只填业务字段） |
| `2fc245f6` | C | 知识五表 + 处理管道 + `KnowledgePort` |
| `f2109509` | C | 知识接口与权限点 |
| `01388680` | C | 知识接入执行计划（检索冻结 + 资料节） |
| `8d5f99a2` | D | 工具注册/执行/人工确认（D05） |
| `44430c38` | D | 工具接口与权限 |
| `d33c2d69` | E | 反馈账本/队列规则 + 运营指标口径（D12） |
| `8ebde509` | E | 反馈接口与权限 |
| `6f403940` | — | 《24-总体实施与评审说明》（本文件） |
| `037f66ad` | E | 运营指标聚合与接口（含故障口径） |
| `41dfa1ab` | D | 子 Agent 顺序编排与校验 |
| `ea5c7fe9` | A/§7.2 | 有效权限逐层相交与有效数据等级 |
| `e748aac9` | E | 故障分类指标 |
| `04933736` | A/§7.1 | 执行计划冻结授权上下文（身份 + 岗位卡片） |
| `bee7494a` | D | 顺序流程节点校验（`AigSequentialFlow`） |
| `3a6b777c` | E/前端 | 反馈与运营页面（`aigov/feedback/index` + 纯函数与单测） |
| `4d8bc675` | C/前端 | 知识中心页面（`aigov/knowledge/index` + 纯函数与单测） |
| `32f22098` | D/前端 | 工具注册页面（`aigov/tool/index` + 纯函数与单测） |
| `d14c7eb4` | D | 任务步骤账本（`aig_task_step`，编排步骤可追溯） |
| `17123281` | B/前端 | 训练台步骤/辅助区口径 + 页头状态条（`studio/layout.ts`） |
| `120c74a5` | D | 任务步骤状态迁移与 CAS（`AigTaskStepTransition`） |
| `95317aba` | D/文档 | 子 Agent 派发规格（`AigSubAgentDispatch`）+ §7 评审指引 |
| `7040fa31` | D | 子 Agent 运行时驱动（创建子任务 + 对账收尾） |
| `5ca6b6ca` | D | 子 Agent 对账作业与定时触发 |
| `2dee4ce4` | C/§7.2 | 授权上下文进入知识检索（身份层组织收窄） |
| `19ebe830` | D/§7.5 | 工具许可命中有效绑定 |
| `2131ed54` | D/§7.5 | 工具许可命中**调用人权限**（`ToolPermissionChecker`，fail-closed） |
| `4a358069` | D | **流程节点运行时驱动**（`AigFlowRuntime` + `IAigFlowDriverService` + 执行器闸门接线） |
| `873cfb8d` | — | 文档：22/24 补记上述两项与状态收敛 |
| `44ed5e2f` | D12 | **资产索引同步被「短页」误判为到底**（每域只同步前 50 条）修复 + 工具页权限点口径 |
| `4c4f88dd` | D12/E | **资产索引水位线增量同步**（`syncNewForUser` + `?mode=incremental`） |
| `cc3420f2` | D | **人工确认的恢复入口**：节点身份落账本（`node_type`/`node_ref`）+ `GET /task/{id}/flow-nodes` + `POST /task/{id}/flow/confirm`；发布工作流的重放清单补入本分支新增的建表/补列脚本 |

---

## 2 D01–D12 对照

状态口径：✅ 已实现并有测试；🔶 部分实现（边界见各阶段文档）；⏳ 未开始。

| 编号 | 设计问题 | 状态 | 落点与证据 |
| --- | --- | --- | --- |
| D01 | 执行只透传版本 ID，Prompt 由调用方给 | ✅ | `execution/`（`AigExecutionPlan`/`AigAgentExecutionResolver`）；`AigAgentExecutionResolverTest`；存量版本不受影响（只认 `schemaVersion>=2`） |
| D02 | 草稿 `allowExternal` 不是网关硬约束 | ✅ | `AigInvokeBo`/`AigRouteHint`/`AigRouteServiceImpl`；`AigRouteServiceExternalConstraintTest` |
| D03 | 绑定只看 enabled、缺撤销入口 | ✅ | `countActiveBindings` 统一有效期口径；`assertExecutionAuthorized`；`POST /aigov/agent/binding/{id}/revoke`；`AigAgentBindingLifecycleTest` |
| D04 | 执行态内异常直接抛出、等超时清扫 | ✅ | `AigTaskExecutorImpl#settleFailure` 单一收口；`AigTaskExecutorImplTest`（缺能力编码、ServiceException） |
| D05 | 工具/知识可绑定但无执行引擎 | 🔶 | 工具注册+执行+确认、子 Agent 顺序编排 + 运行时派发/对账 + 对账定时作业、顺序流程节点校验、任务步骤账本（含状态迁移与 CAS）、**流程节点运行时驱动（闸门接线 + 人工确认的挂起与恢复入口：节点身份落账本 + 读/确认两个接口）**已完成；**一个任务只驱动第一个 STEP 节点（多节点需动调度循环）、条件结论的来源、WarmFlow/LiteFlow 适配器未做** |
| D06 | 采纳提案/关闭编辑器丢未保存内容 | ✅ | `dirtyGuard.ts` + `dirtyGuard.spec.ts`；`index.vue`/`ProposalPanel.vue` |
| D07 | 预检只查结构、类别在提交阶段查 | ✅ | `AigStudioPrecheck` 分级（blockers/warnings + fieldPath）；`AigStudioDraftServiceImplTest` |
| D08 | 网络调用包在长事务里 | ✅ | `runTest`/`chat` 去掉方法级 `@Transactional`；`AigStudioShortTransactionGuardTest` + 更新的 `AigStudioProposalTransactionBoundaryTest` |
| D09 | 存在两份分节拼装 | ✅ | 提交改调 `AigStudioPromptBuilder.build`，删除第二份实现 |
| D10 | Copilot 复用被训练 Agent 的能力 | ✅ | `aigov.studio.copilot.capability`；`AigStudioCopilotServiceImplTest` |
| D11 | 员工需填实现概念（任务类型/业务域/数据等级/原始 JSON） | ✅ | `aig_launch_template` + `AigLaunchTemplateServiceImpl`；`GET /aigov/portal/launch/template`；前端业务表单 |
| D12 | 缺业务反馈与有效成果口径 | ✅ | 反馈账本+队列规则+接口、运营指标（口径与聚合，含**故障分类**）、**资产索引（全量重建的截断缺陷已修 + 水位线增量同步）** 均完成（`feedback/`、`workspace/portal`）；增量口径明确"只补新增，删除/更新靠全量"（`AigAssetIndexServiceImplTest` 10） |

---

## 3 阶段完成情况

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| A 基线一致性 | ✅ | D01/D02/D03/D04/D08（+D10）全部落地并有正反例测试 |
| B 训练体验 | ✅（界面重构除外） | D06/D07/D09/D10/D11 + 元数据；**训练台三栏线框布局未做**（属界面重构） |
| C 知识试点 | 🔶 | 数据模型/处理管道/检索端口/接口/接入执行计划、**知识中心页面**完成；**解析器与嵌入未接入** |
| D 工具与流程 | 🔶 | 工具注册/执行/确认/接口、**工具许可（版本声明 + 有效绑定 + 调用人权限）**、子 Agent 编排/派发/对账、顺序流程节点的校验与**运行时驱动**完成；**多 STEP 节点的调度循环与 WarmFlow/LiteFlow 适配器未做** |
| E 持续运营 | ✅ | 反馈账本/队列/接口 + 指标口径与聚合（含故障分类）+ **资产索引全量/增量同步** |

---

## 4 验证汇总

- 后端 `ruoyi-ai-gov`：**1255 tests / 0 failures / 0 errors**（基线 1053 → 新增 202）。
- 前端 `frontend`：**49 files / 468 tests** 全过；`oxlint src` 543 files 0/0。
- 关键守卫全部通过：`AigPermissionSeedCoverageTest`（权限串已种子 + menu_id 不撞）、
  `AigRepeatSubmitCoverageTest`（写接口防重或显式豁免）、`AigWp3WiringAndRoutesTest`。

复现命令：

```bash
# 后端（根 pom 默认 maven.test.skip=true，必须显式关闭）
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false
# 前端
cd frontend && ./node_modules/.bin/vitest run && ./node_modules/.bin/oxlint src
```

---

## 5 部署清单（本轮新增）

**建表脚本**（全部幂等，`create table if not exists`）：

| 脚本 | 内容 |
| --- | --- |
| `script/sql/aig_knowledge.sql` | 知识集合/文档/版本/处理任务/分段（5 张） |
| `script/sql/aig_tool.sql` | 工具定义/版本/调用账本（3 张） |
| `script/sql/aig_result_feedback.sql` | 结果反馈（1 张） |
| `script/sql/aig_launch_template.sql` | 启动任务模板（1 张） |
| `script/sql/aig_task_step.sql` | 任务步骤账本（1 张；含流程节点身份 `node_type`/`node_ref`） |

**补列迁移（存量库用；`create table if not exists` 不会给已存在的表加列）**：

| 脚本 | 内容 |
| --- | --- |
| `script/sql/aig_task_step_node_identity.sql` | 给已有步骤账本补 `node_type`/`node_ref`（information_schema + PREPARE，幂等） |

> **这两类脚本已加进 `.github/workflows/deploy-poc.yml` 的 `Prepare additive runtime schema` 重放清单**，
> 由构建期守卫 `AigDeploySchemaReplayCoverageTest` 把关：**只写在文档里的"部署清单"没人会在切镜像前
> 照着执行**，漏掉的后果是"部署成功但接口 500"，而 RuoYi 把 SQL 异常掩成「发生未知异常」。
> 权限/页面种子（`aig_*_menu.sql` / `aig_*_pages.sql`）**不在自动重放之列**——它们插的是
> `sys_menu`/`sys_role_menu`，属授权变更，应由运维按本清单显式执行。

**权限种子**（幂等，只授 `aig_admin`）：

| 脚本 | 权限点 |
| --- | --- |
| `script/sql/aig_knowledge_menu.sql` | `aig:knowledge:*`（6） |
| `script/sql/aig_tool_menu.sql` | `aig:tool:list` / `aig:tool:manage` |
| `script/sql/aig_feedback_menu.sql` | `aig:feedback:queue` / `aig:feedback:handle` |
| `script/sql/aig_ops_menu.sql` | `aig:ops:metrics` |

> 设计 §4.3 的「业务训练负责人」「知识维护人」等业务角色应由运维按需新建并单独授权；
> 本仓当前只有 `aig_admin` / `aig_security` / `aig_viewer`，因此种子只授 `aig_admin`。

---

## 6 剩余工作（诚实的待办，不影响已交付部分可评审）

1. **D05 续**：**流程节点运行时驱动的剩余部分**——
   ①一个任务只驱动第一个 `STEP` 节点，多 `STEP` 节点的流程需把调度循环改成"按节点多次发起调用"；
   ②**条件结论的来源**——本模块不含表达式引擎，`advance(..., Map.of())` 意味着声明了条件判断的
   流程会停在 `NEED_HUMAN`（可见的"待接"，不是默认放行），接上业务判定后才真正分流；
   ③WarmFlow/LiteFlow 适配器（适配点已明确：`AigFlowRuntime` 的判定输入是
   "节点顺序 + 步骤状态 + 条件结论"）。
   **已闭环的部分**：人工确认的"挂起—恢复"（节点身份落账本 + `GET /task/{id}/flow-nodes` +
   `POST /task/{id}/flow/confirm`，见 `22` §1.16；操作序列"读 → 确认 → requeue"，确认刻意不自动重跑）；
   子 Agent 的编排校验、派发、对账与对账定时作业（`IAigSubAgentRunner` /
   `IAigSubAgentReconcileService`）。
2. **D12 已完成**：资产索引**增量同步**（水位线 `syncNewForUser`，`?mode=incremental`）已落地，
   口径边界写在 `23` 与 `10`：**只补新增**，删除/更新靠定期全量重建（三个业务域都没有变更事件）。
3. **界面**：反馈与运营页、知识中心页、工具注册页已交付；训练台已加**页头状态条 + 七步骤导航**
   （含 `studio/layout.ts` 口径与单测）；**完整三栏组件化重构仍待做**。
4. **执行入口收窄**：§7.2 已落地 **有效知识范围 = 版本声明 ∩ 启用的 KNOWLEDGE 绑定**、
   **有效数据等级 = 各来源最高值**、**有效外发 = 各方取与**（`AigEffectivePolicy`）；
   计划中**冻结授权上下文**并被**知识检索消费**（按 `orgScope` 身份层收窄）；
   工具侧已落地 **命中有效绑定 + 命中调用人权限**（`AigToolExecutionPolicy.bindingsPermit`
   / `permissionPermit` + `ToolPermissionChecker`）。
   **仍未做**：机器身份（服务令牌 scope）作为工具调用人的授权路径——当前无登录态一律按无权限处理。
5. **解析器与嵌入**：PDF/DOCX 解析引擎、嵌入模型与向量存储选型（设计明确需按试点资料评测）。

---

## 7 如何评审（约 30 分钟）

### 7.1 先看结论（5 分钟）

1. 本文 §2 的 D01–D12 对照表：**哪些 ✅、哪些 🔶、🔶 的边界是什么**；
2. §3 阶段完成情况：A/B 完整，C/D/E 各自只剩明确列出的子项；
3. §6 剩余工作：每条都写明了缺什么（D05 的三项剩余、三栏重构、解析器与嵌入）。

### 7.2 跑一遍（10 分钟）

```bash
# 后端（注意根 pom 默认 maven.test.skip=true，必须显式关闭）
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false
# 前端
cd frontend && ./node_modules/.bin/vitest run && ./node_modules/.bin/oxlint src
```

关键守卫（即使只跑这三条也能证明"接口不会对所有人 403 / 写接口有防重 / 路由装配正常"）：

```bash
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false \
  -Dtest=AigPermissionSeedCoverageTest,AigRepeatSubmitCoverageTest,AigWp3WiringAndRoutesTest
```

### 7.3 抽点看代码（10 分钟）

| 想验证什么 | 看哪里 |
| --- | --- |
| 执行是否真的按冻结版本而非调用方 Prompt | `execution/AigAgentExecutionResolver`、`task/.../AigTaskExecutorImpl#runOnceRunning` |
| 授权是否逐层相交、等级取最高 | `execution/AigEffectivePolicy`（+ `AigEffectivePolicyTest`） |
| 知识检索是否两道授权 | `knowledge/retrieval/AigKnowledgeRetrievalService`、`AigKnowledgeAccess` |
| 写工具是否必须先确认、幂等 | `tool/exec/AigToolExecutionPolicy`（含 `permissionPermit`）、`tool/exec/impl/SaTokenToolPermissionChecker`、`tool/.../AigToolRegistryServiceImpl#execute` |
| 条件判断/人工确认是否真拦得住 | `task/service/AigFlowRuntime`、`task/.../AigFlowDriverServiceImpl`、`AigTaskExecutorImpl#checkFlowGate` |
| 人工确认能否被"恢复"、批准会不会被覆盖 | `task/controller/AigTaskController#flowNodes/#confirmFlowNode`、`task/domain/vo/AigTaskFlowNodeVo#isAwaitingConfirmation` |
| 新增的库表/列会不会在部署时被漏掉 | `.github/workflows/deploy-poc.yml` 的重放清单 + `AigDeploySchemaReplayCoverageTest` |
| 反馈是否会直接改线上 | `feedback/.../AigResultFeedbackServiceImpl`（动作只进改进流程） |
| 训练台是否会静默丢内容 | `frontend/src/views/aigov/studio/dirtyGuard.ts` |
| 指标口径是否可复核 | `feedback/metrics/AigOperationsMetrics`（累计成果=经确认结果；分母 0 → 0） |

### 7.4 部署前必做（5 分钟）

按 §5 清单依次执行：**5 个建表脚本 + 1 个补列脚本 + 4 个权限种子 + 3 个页面行脚本**。
注意：**建表/补列脚本已经在发布工作流的重放清单里**（切镜像前自动跑，由
`AigDeploySchemaReplayCoverageTest` 把关）；权限种子只授 `aig_admin`，设计 §4.3 的业务角色
应由运维按需新建并单独授权；页面行脚本登记的是草稿/反馈/知识/工具页面菜单。

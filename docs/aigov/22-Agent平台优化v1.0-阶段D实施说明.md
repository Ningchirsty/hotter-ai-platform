# 22 - Agent 平台优化 v1.0 · 阶段 D 实施说明（工具注册与执行、人工确认）

> 依据：附件《Agent 平台优化详细设计 v1.0》§6.5（工具与流程）、§7.5（工具执行与确认）、
> §7.6（事务与故障恢复）、§8.2（aig_tool_definition 与 version）
> 分支：`feature/aigov-optimization-v1`

---

## 1 本轮落地（D05）

### 1.1 数据模型（`script/sql/aig_tool.sql`）

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| `aig_tool_definition` | 工具定义 | `tool_code` 唯一 |
| `aig_tool_definition_version` | 版本执行契约（executorRef/operationType/Schema/timeout/maxAttempts/permissionPoint） | `(tool_id, version)` 唯一；密钥只存引用 |
| `aig_tool_call` | 调用账本 | `(tool_code, tool_version, idempotency_key)` 唯一（幂等） |

### 1.2 登记与可执行**分开**

`executor_ref` 为空、或引用的执行器未注册 ⇒ **不可执行**，一律按"已登记、待接入"拒绝
（设计 §4.1：不得把未接入资源显示为可用）。"可执行"由服务端当前注册的执行器决定，
不由这一列非空决定。

### 1.3 操作类型决定要不要确认（`AigToolOperationTypeEnum`）

- `READ_ONLY_QUERY` / `GENERATE_DRAFT`：按策略可直接执行；
- `MUTATE_BUSINESS_DATA` / `EXTERNAL_SEND`：**必须人工确认**。

把操作类型做成枚举而不是调用方自报布尔：让"这是一次写操作"成为不可绕过的属性——
调用方声称"只是查询"而实际发起变更，在代码上不可能。

### 1.4 确认绑定输入摘要（`AigToolExecutionPolicy`）

批准校验=摘要一致 + 未过期（截止时刻仍有效）。**参数一变摘要就变，原批准自动失效**（设计 §7.5）。
判据是纯函数，可逐条单测。

### 1.5 执行编排（`AigToolRegistryServiceImpl#execute`）

- **未接入 / 非法操作类型** → 拒绝且**不落账本**（没有发生过调用）；
- **写操作批准无效** → 返回 `NEEDS_CONFIRMATION` 且**不执行、不落账本**（`TOOL_APPROVAL_REQUIRED`）；
- **幂等**：同一 `(tool, version, key)` 命中历史记录即返回历史结论，不再执行；
  历史记录仍是 `RUNNING` ⇒ 返回 `OUTCOME_UNKNOWN`（"结果未知待核查"）——
  写操作在这时重新执行正是"重复写入"的典型成因；
- 执行前落 `RUNNING`（崩溃也留痕），执行后收尾；**方法刻意不带 `@Transactional`**
  （与 D08 同一纪律：外部/长耗时调用包在数据库事务里会让 RUNNING 证据随崩溃回滚）；
- 执行异常 ⇒ `OUTCOME_UNKNOWN`，**不自动重试**（设计 §7.6：先查凭证）。

---

### 1.6 工具接口与权限（D-2）

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /aigov/tools` | `aig:tool:list` | 可绑定工具清单：版本 / 操作类型 / **是否已接入执行器** / 所需权限点 |
| `POST /aigov/tools/definitions` | `aig:tool:manage` | 新建工具定义（`@RepeatSubmit`） |
| `POST /aigov/tools/versions` | `aig:tool:manage` | 新增执行契约版本（`@RepeatSubmit`） |

种子脚本 `script/sql/aig_tool_menu.sql`（ID 段 `1769100000000000001..`，只授 `aig_admin`）。
清单里的 `executable` 由服务端按"执行器是否已注册"计算，**不由前端从 `executorRef` 是否为空去猜**
——空串或写错的引用都会让前端猜错，后果是把不可用工具显示成可用（设计 §4.1）。

---

### 1.7 子 Agent 顺序编排（D-3）

- `AigSubAgentOrchestration`（纯函数）：按声明顺序展开子 Agent，每一步带 `depth` 与
  `parentVersionId`（父任务据此记录子任务、输入摘要与结果引用）；
  **循环引用与超深在展开时拒绝**——循环会让编排在运行时无限展开，超深要拒绝而不是截断
  （截断会静默少跑一段，看起来像成功）；
- `ISubAgentGraphResolver` / `AigSubAgentGraphResolver`：从版本 `config_json` 的
  `subAgentRefs[].agentVersionId` 取直接子版本；
- `AigExecutionConfig` 解析 `subAgentRefs`，`AigExecutionPlan` 新增
  `subAgentVersionIds`（已展开、已校验、可空）；
- 解析器在声明了子 Agent 时执行展开与校验，被拒即抛错（不产出计划）。

---

### 1.8 顺序流程节点（D-5）

- `AigFlowNodeTypeEnum`：`STEP / CONDITION / HUMAN_CONFIRM / OUTPUT`（有业务后果的动作必须经
  `HUMAN_CONFIRM`）；
- `AigSequentialFlow.validate(nodes, maxSteps)`（纯函数）三条判据：
  ①节点类型必须可识别（拼错的类型只会让那一步被**静默跳过**）；
  ②**必须有输出节点且输出只能是最后一步**（没有输出 = 跑完没有交付物；输出后再挂步骤 =
  "交付了又继续改"，结果与交付物不一致）；③不超步数上限（**超限拒绝而不是截断**）；
- `AigExecutionConfig` 解析 `flowNodes` 与 `limits.maxFlowSteps`；
  `AigExecutionPlan` 新增 `flowNodes`；解析器校验后冻结，被拒即抛错。

---

### 1.9 工具注册页面（前端，D-6）

- `frontend/src/api/aigov/tool/`：清单 / 新建定义 / 新增版本；
- `frontend/src/views/aigov/tool/presentation.ts`（+ spec 6 条）：操作类型词表、码→文案、
  **未接入一律显示「已登记 待接入」（不能显示为可用）**、写操作提示、
  定义/版本校验与后端同口径；
- `frontend/src/views/aigov/tool/index.vue`：工具清单表（操作类型 / 需人工确认 / 接入状态 /
  所需权限点 / 等级上限）+ 新建定义 + 新增版本；
- 页面菜单行 `script/sql/aig_tool_pages.sql`（`component=aigov/tool/index`，只授 `aig_admin`）。

---

### 1.10 任务步骤账本（D-7，设计 §8.2 `aig_task_step`）

- 新增表 `aig_task_step`（`script/sql/aig_task_step.sql`）：`(task_id, sequence, attempt)` 唯一，
  **重试是新 attempt，不覆盖历史**；只记引用（`output_ref`/`approval_ref`），不落大结果与受限原文。
- `AigTaskStepKindEnum`（SUB_AGENT / FLOW_NODE / TOOL_CALL）+
  `AigTaskStepStatusEnum`（PLANNED / RUNNING / SUCCEEDED / FAILED / SKIPPED）：
  **步骤状态独立于任务状态**——任务 SUCCEEDED 而某步 FAILED 是可能的（部分成功），
  合成一个状态会掩盖"哪一步没成"。
- `IAigTaskStepService#recordPlannedSteps(taskId, plan)`：子 Agent 一步一版本、流程节点各占一步，
  按序号登记为 `PLANNED`；**幂等**（同一 `(task, sequence)` 已有记录则跳过）。
- 任务执行器在解析出计划后**尽力**登记（写失败记日志、不让执行失败）——
  它是可追溯性记录，不该反过来把一次执行变成失败。

**状态迁移（D-8）**：`AigTaskStepTransition`（纯函数）定义合法边——
`PLANNED → RUNNING`、`PLANNED → SKIPPED`、`RUNNING → SUCCEEDED/FAILED/SKIPPED`，
**终态不出边**（不能把失败改成成功，也不能把成功改回执行中）；
`startStep` / `finishStep` 走 CAS（影响 0 行即报冲突），且**幂等**
（重复"开始"不改、终态不再覆盖）。

---

## 2 本轮**不做**的事（明确的边界）

1. **HTTP 接口与权限点（已在 D-2 补齐）**：`GET /aigov/tools`（可绑定工具清单）、
   `POST /aigov/tools/definitions`、`POST /aigov/tools/versions`，权限点 `aig:tool:list` /
   `aig:tool:manage`，种子脚本 `script/sql/aig_tool_menu.sql`。
2. **子 Agent 编排与顺序流程（D-3/D-5 已落地）**：`AigSubAgentOrchestration`（纯函数）
   按声明顺序展开子 Agent，**展开时**拒绝循环引用与超深（不截断、不等运行时超时）；
   `ISubAgentGraphResolver` 从版本 `config_json` 的 `subAgentRefs` 取子版本；
   解析器把展开结果冻结进 `AigExecutionPlan.subAgentVersionIds`。
   **顺序流程节点**：`AigSequentialFlow` 校验 `flowNodes`（类型可识别、必须有输出且输出在最后、
   不超步数上限），冻结进 `AigExecutionPlan.flowNodes`。
   **尚未做**：节点在运行时真正驱动执行（条件求值、人工确认的挂起/恢复）、
   以及 WarmFlow/LiteFlow 适配器。
3. **工具接入执行计划**：`AigExecutionPlan.toolRefs` 已冻结，但执行路径尚未把
   `toolCall` 交给本服务（下一增量：解析 toolRefs → 权限与确认检查 → 调用 → 结果回填）。
4. **真实业务工具执行器**：本仓尚无业务域执行器实现；`ToolExecutor` 只是端口，
   接入时由所属业务域提供实现并注册为 Bean。

---

## 3 验证

- 新增测试：
  - `AigToolExecutionPolicyTest`（6）：未接入拒绝、写操作需确认、只读/草稿可执行、
    未知操作拒绝、摘要随参数变化、批准有效性（含闭区间）；
  - `AigToolRegistryServiceImplTest`（8）：只读执行并落账本、未接入不落账本、
    写操作批准缺失/过期不执行、有效批准执行、幂等回放、RUNNING→结果未知、
    执行异常→结果未知不重试、非法操作类型拒绝。
- 后端 `ruoyi-ai-gov` 全量：**1182 tests / 0 failures / 0 errors**。

---

## 4 部署注意

- 需执行 `script/sql/aig_tool.sql`（三张新表）。
- 本轮**不新增接口、不新增权限点**，对既有生产行为零影响。

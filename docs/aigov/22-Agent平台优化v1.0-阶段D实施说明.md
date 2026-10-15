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

#### 1.9.1 契约详情（设计 §6.5 工具卡片字段，本轮补齐）

设计 §6.5 要求工具卡片显示「用途、版本、输入输出、数据范围、操作类型、责任人、接入状态」。
此前列表只给得出"能绑定的版本"这一列事实（编码/版本/操作类型/是否可执行/权限点/等级上限），
**契约本身**（收什么、回什么、超时多久、重试几次、数据策略）没有读法——而注册工具的人
和申请绑定的人要核对的恰恰是它。

- `GET /aigov/tools/versions/{toolCode}/{version}`（`aig:tool:list`，**无新权限点**）→
  `AigToolDetailVo`：定义的用途/数据策略/责任人 + 版本的输入输出 Schema / 超时 / 重试 /
  权限点 / 等级上限 / 接入状态。
- **为什么与列表分开**：把 `inputSchema/outputSchema` 这类长文本塞进列表响应，
  "看一眼有哪些工具"就变成了把每个版本的契约全文都拉一遍。
- **刻意带上 `executorRef`**：它是"该注册哪个执行器实现"的唯一线索——
  只有 `executable=false` 而没有引用名，接线的人只能去翻代码找 Bean 名字。
- 详情复用 `getVersion(...)` 的拒绝口径（工具/版本不存在或停用），
  避免详情与执行路径对同一份数据给出不同结论。
- 界面：工具清单每行「详情」→ 对话框（用途 / 数据范围 / 责任人 / 输入输出 Schema /
  超时重试 / 权限点 / 接入状态），未声明的项**明说"未声明"**而不是留空白
  （空白会被读成"没有限制"或"取不到数据"）。


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

### 1.11 子 Agent 派发规格（D-9）

`AigSubAgentDispatch`（纯函数）：把"父任务 + 执行计划"翻译成一组**子任务创建规格**，
不只做转换、更把两条"错了不会报错"的规则固化下来：

- **数据等级继承父任务且不降低**（设计 §7.2「有效数据等级取最高值」）——
  子任务若悄悄降级，受限资料会经由子 Agent 流出；
- **幂等键稳定且按步骤唯一**（`sub:{parentTaskId}:{sequence}`）——父任务重试不得建出第二套子任务
  （重复执行 = 两次真实调用与两次计费）。

真正创建子任务与回写步骤状态由运行时驱动完成；本类保证"要建什么"的口径唯一。

---

### 1.12 子 Agent 运行时驱动（D-10）

`IAigSubAgentRunner` / `AigSubAgentRunner` 两步：

1. `dispatchChildren(...)`：按派发规格（§1.11）创建**子任务**（幂等键稳定，父任务重试不会建出第二套），
   并把对应父步骤置为执行中、记录引用 `task:{childId}`；
2. `reconcile(parentTaskId)`：把"仍在执行中且引用了子任务"的父步骤，**按子任务终态收尾**
   （`SUCCEEDED/APPROVED/REJECTED → 成功`；`FAILED/CANCELLED → 失败`；**未终态不动**——
   把"还在跑"当成"已失败"是会误报的）。

**为什么是"创建 + 对账"而不是同步等待**：父/子都是平台任务，同步等待会把父任务挂在一次长调用上
（不可恢复、无法重试）。创建后由平台调度执行，父步骤靠对账收尾——与设计 §6.5「由父任务记录子任务、
输入摘要与结果引用」、§7.6「结果未知先查凭证」同一纪律。

任务执行器在解析出计划后**尽力**派发（失败记日志、不影响本次执行）；
子 Agent 只在 v2 配置声明 `subAgentRefs` 时才会产生，因此对存量任务零影响。

---

### 1.13 对账作业与定时触发（D-11）

- `AigSubAgentReconcileProperties`（`aigov.subagent.reconcile.*`，**默认关闭**）+
  `IAigSubAgentReconcileService#sweep()`：取"执行中的子 Agent 步骤"（限流取前 N 条）→ **按父任务去重** →
  逐个对账；**单个父任务失败不影响其它父任务**（对账可重复，下一轮还会看到它）。
- `AigSubAgentReconcileJob`：`@ConditionalOnProperty(aigov.subagent.reconcile.enabled=true)` +
  `@Scheduled(fixedDelayString=…)`——与既有任务调度器**同一套部署口径**：
  单实例用内置定时，集群保持关闭改由 SnailJob/cron 调用同一服务入口；
  多实例同时触发不会重复收尾（步骤迁移走 CAS）。

---

### 1.14 工具许可命中有效绑定（D-12，设计 §7.5）

设计原文：「工具许可既要命中版本声明，也要命中**有效绑定**与调用人权限；禁止工具优先。」

- `AigToolCallRequest` 新增 `agentVersionId`（调用归属的 Agent 版本）；
- `AigToolExecutionPolicy.bindingsPermit(toolCode, boundToolCodes)`（纯函数）：
  **没有任何 TOOL 绑定时不额外收窄**（返回 true）；一旦有绑定，请求的工具编码必须在其中——
  否则就是"绕过绑定直接用版本声明之外的工具"；
- `AigToolRegistryServiceImpl#execute` 在操作类型判据之后、执行之前做该检查，
  不通过即 `TOOL_NOT_BOUND` 拒绝（不落账本，因为未发生调用）。

> 调用人权限那一层见 §1.15（已补齐）。

---

### 1.15 工具许可命中调用人权限（设计 §7.5 第三项）

设计原文的第二句：「工具许可既要命中版本声明，也要命中**有效绑定**与**调用人权限**；禁止工具优先。」
§1.14 只做了"有效绑定"，缺的正是"调用人权限"。

- `ToolPermissionChecker`（端口）+ `SaTokenToolPermissionChecker`（实现）：
  **授权事实必须来自服务端身份**，不能由调用方在请求里自报"我有这个权限"。
  抽成端口后，判据来自哪套身份系统可替换，工具服务只依赖结论。
- `AigToolExecutionPolicy.permissionPermit(permissionPoint, holds)`（纯函数）：
  版本**未声明**权限点 → 不额外要求；声明了 → 必须持有（fail-closed）。
- `AigToolRegistryServiceImpl#execute` 在**绑定判定之后、执行之前**加该检查，
  不通过即 `TOOL_PERMISSION_DENIED`（不落账本：没有发生过调用）。
- 三条刻意设计：
  1. **没有登录态一律无权限**（调度器/后台线程发起的调用没有"调用人"，放行等于把"没人授权"
     当成"已授权"）。机器身份应走服务令牌 scope 那条路（设计 §10.3），属后续接线；
     当下如实拒绝并留痕，而不是默认放行。
  2. **身份系统异常也按无权限处理**：宁拒绝不误放。
  3. **未声明权限点时不去问身份系统**：否则"这个工具不需要权限"会被实现成"调用人没权限被拒"。

---

### 1.16 流程节点的运行时驱动（D-5 续，设计 §6.5）

设计原文：「流程先提供顺序步骤、条件判断、人工确认和输出节点。」
在此之前节点只被"校验并冻结进执行计划"——**条件判断不会真的拦、人工确认不会真的挂起**，
声明了人工确认的流程照样一路跑完，而声明本身看起来是生效的。

- `AigFlowRuntime`（纯函数，`task/service`）：按 `(流程节点, 步骤状态, 条件结论)` 判定下一步，
  动作有 `START / WAIT_HUMAN / WAIT_CONDITION / WAIT_EXECUTION / SKIP_REST / DONE / BLOCKED`。
- `IAigFlowDriverService` / `AigFlowDriverServiceImpl`：把判定落到 `aig_task_step` 账本上
  （启动节点、挂起人工确认、条件不满足时跳过其后节点、收尾输出节点）。
- `AigTaskExecutorImpl` 接线：**先问流程能不能往下走，再派发子 Agent、再调模型**；
  闸门未通过时不调模型，任务经既有 `RUNNING → FAILED → NEED_HUMAN` 链路落 `NEED_HUMAN`；
  放行且首节点是 `STEP` 时，本次模型调用结束后按结果收尾该步骤。

**首期语义（刻意很小，且都是 fail-closed）**：

| 情况 | 结论 | 为什么 |
| --- | --- | --- |
| 节点与步骤数量不一致 | BLOCKED | 错位的状态会落在别的节点上，且不会有任何异常 |
| 上游步骤 FAILED | BLOCKED | 跳过失败继续跑，会把"某一步没成"藏进一次"整体成功" |
| 节点类型不可识别 | BLOCKED | 不做猜测式跳过 |
| 条件判断没有结论 | WAIT_CONDITION | 本模块**不含表达式引擎**，结论由业务域/人工给出；缺结论绝不默认真或假 |
| 条件为假 | SKIP_REST | 首期顺序流程只有"门"语义（本节点与其后节点全部跳过），不做多分支 DAG 选择 |
| 人工确认未批准 | WAIT_HUMAN | 节点挂起在 `RUNNING`，可见地等 |
| 人工确认已批准 | 收尾并继续 | 批准记录在步骤的 `approval_ref` 上 |
| 空流程 | DONE | 调用方不该为"没有流程"空等 |

**两条刻意设计**：

1. **批准不另立一张表**：挂起 = `RUNNING` + 空 `approval_ref`；批准 = `SUCCEEDED` + 非空
   `approval_ref`。进程重启不丢"已经批过"这件事，也不会出现"步骤说没批、批准表说批了"。
2. **"假闸门比没有闸门更坏"**：闸门判定必须在派发子 Agent 与调模型**之前**。若先调模型再记
   `NEED_HUMAN`，人工确认就只挂了个牌子、什么都没拦住；因此接线顺序是
   `登记步骤 → 闸门 → 派发子 Agent → 调模型 → 收尾 STEP 节点`。

**人工确认的"恢复"入口（本轮补齐，同一段 §1.16）**：

- **节点身份落账本**：`aig_task_step` 新增 `node_type` / `node_ref` 两列（建表脚本已含；
  存量库用 `script/sql/aig_task_step_node_identity.sql` 补齐，information_schema + PREPARE 幂等）。
  **为什么必须落库**：计划不落库，任务因"需要人工确认"停下之后就没有人能回答
  "这个步骤是人工确认还是普通步骤"——确认接口无法校验批准对象，只能盲批（等于没有闸门）。
  存量行刻意不回填：回填一个猜出来的类型，等于把"不知道"写成"知道"。
- **读**：`GET /aigov/task/{taskId}/flow-nodes`（`aig:task:query`）——下标 / 类型 / 引用 / 状态 /
  批准引用；`awaitingConfirmation` 由服务端判定，界面不自己拼"RUNNING 且 approvalRef 为空"
  （两处判据迟早不一致，且不一致的方向是"界面说已批、服务端说没有"）。
- **确认**：`POST /aigov/task/{taskId}/flow/confirm`（`aig:task:operate`，`nodeIndex` + `approvalRef`
  必填）——校验靠**账本里的身份**，不重新解析版本配置（重解析可能得到一份与当时不同的计划，
  把批准落到另一个节点上）；没有身份的存量步骤**拒绝盲批**并说明原因；重复确认返回既有凭据，
  **不覆盖"谁批的"**。
- **界面侧**：任务列表每行加「流程」按钮（`v-hasPermi=['aig:task:query']`），打开
  `TaskFlowPanel.vue`——节点清单（下标 / 类型 / 引用 / 状态 / 批准引用 / 失败原因）+
  「记录确认」表单；口径收在 `flow.ts` 纯函数里（`flowNodeStatusText` 用后端下发的
  `awaitingConfirmation` 判定"等人工确认"，`confirmValidation` 与后端同口径先拦一道），
  共 7 条单测。
- **刻意不自动重新入队**：确认只回答"这个节点批准了"，是否继续跑由人再点一次
  `POST /{taskId}/requeue` 决定——自动重跑会在一次"点确认"里夹带一次可能计费的执行。
  完整操作序列：读 flow-nodes（界面上的「流程」按钮）→ 确认 → requeue。
- 驱动侧同步收敛：节点对齐的过滤（只取 `FLOW_NODE`、只取首次 attempt）收进
  `IAigTaskStepService#listFlowNodes` 一处；`advance` 以**账本身份优先**于执行计划
  （不一致时 WARN 并写明以账本为准）。

**条件结论的来源（本轮补齐）**：

- `AigFlowConditions.fromSnapshotJson(...)`：条件结论取自**冻结输入快照**里与该节点 `ref`
  同名的**顶层布尔字段**。选择"读快照"而不是自造表达式语言的三个理由：①**可归因**——
  快照是冻结且带哈希的，"当时为什么走了这条路"能被复现；②不引入第二套语法（设计明确不叠加
  另一套 DAG/DSL，业务域的复杂判断留在业务域）；③fail-closed 的实现空间很小。
- **取不到结论就不猜**：字段缺失、值是字符串 `"true"`、是数字/null/对象、快照不是 JSON 对象、
  JSON 不可解析、节点 `ref` 为空 —— 一律**没有结论**（不是 `false`），流程停在 `NEED_HUMAN`
  并在拒绝原因里写明"结论取自冻结输入快照的同名布尔字段，请由建任务方提供"。
  **猜错的方向恰好是"静默跳过一个本该跑的节点"**，所以宁可停。
- **只读顶层**：不做 `a.b.c` 路径（那已经是表达式语言的雏形，属独立变更）。
- 界面侧：流程面板在有未决条件时显式说明这条规则（`conditionSourceHint`），
  否则操作者只会在面板里反复刷新而不知道该找谁。
- 接线：`AigTaskExecutorImpl#checkFlowGate` 从 `snapshot.getSnapshotJson()` 提取结论后
  传给 `advance`。

**尾部节点的本轮推进（同一段 §1.16 的补充）**：

- 问题：执行器此前一次执行只调一次 `advance`，于是 `{STEP, OUTPUT}` 这类流程里**输出节点永远停在
  "待执行"**——账本看起来"没跑完"，而该做的其实都做完了。
- 做法：新增 `IAigFlowDriverService#peek`（**只看不写**的判定），执行器在收尾本次调用的
  `STEP` 节点后，用 `peek` 探路、只推"不需要人、也不需要再执行"的节点
  （条件判断已有结论 → 通过/跳过其后；输出节点 → 直接收尾），循环上界为节点数。
- **为什么必须先 peek 再 advance**：`advance` 是**会写状态**的——遇到人工确认节点会把它挂起成
  `RUNNING`。若在"只想看看"的场合直接调它，就会留下一个**没有任务态支撑的假挂起**：
  任务紧接着被记成成功，而那个节点读起来像"有东西在跑"，其实什么都没跑。
  因此尾部遇到人工确认/缺结论/需执行的 `STEP`/受阻时一律停下并留痕，留给下一次推进。
- 边界：**一次任务执行仍只发起一次模型调用**——**该边界已在 §1.17 放开（用户 2026-10-14 授权）**：
  顺序流程现在**每步一次调用**；上面这条保留在这里，是为了说明"本次收尾推进"当时的前提。

### 1.17 顺序流程的多步驱动（用户 2026-10-14 授权，设计 §6.5）

**在此之前**：一次任务执行 = 一次模型调用，于是声明了多个 `STEP` 节点的流程**只有第一个会跑**，
其余永远停在 `PLANNED`（"流程跑不完"）。用户明确授权改成"**每步一次调用**"后：

- `AigFlowCallPolicy`（纯函数）把随之而来的三件事固定下来：
  1. **调用次数上限** = 「声明的 `STEP` 节点数」∩「配置 `limits.maxFlowSteps`」∩「平台默认 10」。
     **没有这条，一个写错的流程就能发出几十次计费调用**；`STEP` 节点数为 0 时返回 0，
     调用方据此保持"无流程节点时仍调用一次"的既有语义。
  2. **第 k 步（k>1）的业务输入 = 上一步的输出**（否则"顺序执行"只是把同一句话问多遍）；
     上一步输出为空时退回调用方给的原始输入。
  3. **累计成本不可控这件事如实写清**：调用结果里**没有成本字段**，`maxCost` 只能管"每一次"，
     任务级累计上限要等成本回执接入（与"模型成本指标无聚合数据源"同一处缺口）。
- `AigTaskExecutorImpl` 改成按顺序循环：每步 `invokeOnce`（只替换业务输入，其余入参模板复用）
  → `settleFlowStep` 收尾该步骤节点 → 尾部推进 → 再问闸门要不要继续。
  三个停留点都写明了语义：
  - **某步失败即停**（后面每步都要花一次调用，且失败原因通常对后续同样成立）；
  - **中途需要人工确认/缺条件/受阻**：闸门已把任务落 `NEED_HUMAN`，这里再把**最后一次成功调用的产出与身份**
    补进返回体——前面那几步是真花钱做出来的，只回一句"待人工处理"会让人以为什么都没发生；
  - **达到次数上限即停**并留痕（其余步骤如实留在 `PLANNED`）。
- **审计口径**：任务行上的 `traceId` 是**最后一次**调用；**每一次**调用都写进
  `route_snapshot.executedCalls`（步序 / traceId / 模型 / 是否外发 / 耗时 / 错误码），
  单次调用时不写这一段（保持既有快照形状）。调用级审计本身是一步一行，不受影响。
- 测试：`AigFlowCallPolicyTest`（5）与执行器 4 条（顺序两步与链式输入、首步失败即停、
  中途人工确认仍带回产出、`maxFlowSteps` 收紧到 1 只发一次）。

**仍未做（D05 剩余部分，边界写清）**：

- **条件结论的复杂来源**：现在是"读快照顶层布尔字段"，`a.b.c` 路径与业务侧实时判断
  （需要表达式引擎或外部求值回调）属独立变更；本期口径已足以支撑"要不要走这条分支"这类配置。
- **WarmFlow / LiteFlow 适配器**：本模块不依赖这两种工作流引擎；适配点是
  `AigFlowRuntime` 的判定输入（节点顺序 + 步骤状态 + 条件结论），
  接哪套引擎都只需把它的执行状态映射成"步骤状态"。
- 没有流程节点的界面（节点配置目前来自版本 `config_json` 的 `flowNodes`；
  两个接口已可供界面接入）。

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
   **尚未做**：WarmFlow/LiteFlow 适配器（顺序流程的**多步驱动**已在 §1.17 落地：每步一次调用）。
3. **工具接入执行计划**：`AigExecutionPlan.toolRefs` 已冻结，但执行路径尚未把
   `toolCall` 交给本服务（下一增量：解析 toolRefs → 权限与确认检查 → 调用 → 结果回填）。
4. **真实业务工具执行器**：本仓尚无业务域执行器实现；`ToolExecutor` 只是端口，
   接入时由所属业务域提供实现并注册为 Bean。

---

## 3 验证

- 新增测试：
  - `AigToolExecutionPolicyTest`（8）：未接入拒绝、写操作需确认、只读/草稿可执行、
    未知操作拒绝、有效绑定收窄、调用人权限（未声明不要求/声明了必须持有）、
    摘要随参数变化、批准有效性（含闭区间）；
  - `AigToolRegistryServiceImplTest`（19）：只读执行并落账本、未接入不落账本、
    写操作批准缺失/过期不执行、有效批准执行、幂等回放、RUNNING→结果未知、
    执行异常→结果未知不重试、非法操作类型拒绝、绑定的拒绝与放行、
    **调用人权限的拒绝/放行/未声明不查身份/绑定判定优先**、
    **契约详情装配（定义 + 版本两处来源）/未接入仍显示引用名/未知工具或版本拒绝**；
  - `AigFlowRuntimeTest`（15）：空流程、数量错位、上游失败、状态不可识别、人工确认
    （等待/已批准）、条件（无结论/为真/为假跳过其后）、已跳过视为已过、执行中等待、
    输出节点收尾、全部终态、类型不可识别、条件按 ref 取；
  - `AigFlowDriverServiceImplTest`（19）：无节点不写、`STEP` 交调用方执行、条件真/假、
    人工确认挂起与批准收尾、`confirm` 的恢复路径与四条拒绝（缺批准引用 / 非人工确认节点 /
    没有登记身份 / 下标越界或没有节点）、`confirm` 幂等（不覆盖已有批准引用）、
    只按 `FLOW_NODE` 对齐（过滤在步骤服务那层）、账本身份优先于执行计划、
    `settle` 的成功/失败映射、**`peek` 不写任何状态且与 `advance` 判定一致**；
  - `AigTaskExecutorImplTest`（32）：人工确认闸门拦在模型调用之前、
    条件无结论同样拦下、流程受阻转人工、放行的 `STEP` 节点按成功/失败收尾、
    条件结论取自冻结快照、**尾部可自行收尾的节点本轮推到完成**、
    **尾部是人工确认节点时只探不写**；
  - `AigTaskFlowControllerTest`（6）：流程节点清单的下标与"等确认"判定、已批准不再显示为待批、
    确认走驱动且作答重读账本、行消失时明确报错、两个入口的权限点、确认不自己迁移状态；
  - `AigTaskStepServiceImplTest`（12）：节点身份落库、`listFlowNodes` 的 kind/attempt 过滤，
    以及账本本身的迁移、CAS、幂等；
  - `AigDeploySchemaReplayCoverageTest`（4，既有守卫）：新增补列脚本必须进发布工作流的重放清单。
  - `AigFlowConditionsTest`（9）：布尔给结论、缺失/字符串/数字/对象不给结论、快照不可解析或
    不是对象、只查条件节点、ref 为空或两端空白、部分取到结论。
- 后端 `ruoyi-ai-gov` 全量：阶段 D 落地时为 1280 tests / 0 failures；
  **全阶段累计见 `24` §4**（阶段文档只留本轮新增的数，避免每轮都要回来改）。
- 前端：工具页 `presentation.spec.ts` 8 → 10 条（契约详情文案：未声明项明说、执行器引用名）。

---

## 4 部署注意

- 需执行 `script/sql/aig_tool.sql`（三张新表）、`script/sql/aig_task_step.sql`（步骤账本）。
- **存量库**还要执行 `script/sql/aig_task_step_node_identity.sql`（给已有的步骤账本补
  `node_type` / `node_ref` 两列）；它已加进 `.github/workflows/deploy-poc.yml` 的重放清单，
  由 `AigDeploySchemaReplayCoverageTest` 把关——**漏了会"部署成功但确认接口 500"**。
- 本轮**不新增权限点**（流程节点两个接口复用 `aig:task:query`/`aig:task:operate`，
  工具契约详情复用 `aig:tool:list`，权限行早已存在，无需种子脚本）；
  新增 3 个接口，对既有生产行为零影响
  （无流程节点的任务，驱动直接返回"没有流程节点"，不写任何状态、不改任何既有路径；
  契约详情是纯读，不碰执行路径）。

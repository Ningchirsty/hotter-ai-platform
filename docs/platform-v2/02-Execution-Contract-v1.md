# 0-3 Execution Contract v1

> 状态：**完成**（待团队评审后冻结）
> 可执行文件：本目录 [`contract/`](contract/)（JSON Schema 2020-12 + 错误码表 + 样例）
> 本文档是契约的**人读版**；以 `contract/*.schema.json` 为准。

---

## 一、本契约的三条设计红线

### 红线 1：**不发明第二套词表**

设计文档 §6.4/§6.5 用了 `STEP_SUCCEEDED`、`INVALID_INPUT`、`RUNTIME_TIMEOUT` 等词，而平台**已经有**两套权威枚举：

| 现有权威来源 | 内容 |
|---|---|
| `AigTaskStatusEnum` | 14 个任务状态 |
| `AigTaskEventTypeEnum` | 6 个事件类型（`AI_TASK_CREATED` / `_STATUS_CHANGED` / `_PROGRESSED` / `_RESULT_RECORDED` / `_CALLBACK_RECEIVED` / `_REVIEWED`） |
| `AigErrorClassEnum` | 10 个错误分类 + **4 个处置开关**（`retryable` / `needsHuman` / `circuitBreak` / `worthFallback`） |

**本契约一律复用上述三套枚举的 code，不再另立一套**。V2 真正需要的新增项，作为**同枚举的追加值**（见各节"V2 追加"）。

### 红线 2：**状态与原因分离**

设计文档 §4.3 提议的 `TIMED_OUT` 状态**不采纳**：平台既有设计里"**超时是原因，不是状态**"——
用 `FAILED` + `errorCode=TIMEOUT` 表达。新增一个终态会与 `AigErrorClassEnum.classify()` 的既有语义重复。

### 红线 3：**`principal` 不进客户端契约**

设计文档 §6.2 的示例把 `principal`（`userId`/`orgId`/`brandId`/`projectId`）放在请求体里，但同一节文字明确要求"**必须由 RuoYi 会话/服务令牌填充并在内部签名，不得接受未经校验的浏览器自报**"。
→ 二者矛盾，**以文字为准**：`execution-request.schema.json` **不含 `principal`**，它由服务端补全并落库（落库形态另见 §五）。

---

## 二、状态词表映射（设计文档 → 本契约）

| 设计文档 §4.3 | 本契约（= `AigTaskStatusEnum`） | 说明 |
|---|---|---|
| `PENDING` | `DRAFT` | 草稿/待提交 |
| （无） | `POLICY_CHECKING` | **既有**：策略预检阶段 |
| `QUEUED` | `QUEUED` | 同名 |
| （无） | `DISPATCHED` | **既有**：已派发到执行方 |
| `RUNNING` | `RUNNING` | 同名 |
| `WAITING_APPROVAL` | `REVIEW_PENDING` | 既有语义：待人工复核 |
| `WAITING_INPUT` | `NEED_HUMAN` | 既有语义：待人工处理/补料 |
| `RETRYING` | `RETRY_WAIT` | 既有语义：等待重试 |
| `SUCCEEDED` | `SUCCEEDED` | 同名 |
| `FAILED` | `FAILED` | 同名 |
| `CANCEL_REQUESTED` | `CANCEL_REQUESTED` | 同名 |
| `CANCELED` | `CANCELLED` | 拼写以既有为准（双 L） |
| `TIMED_OUT` | **不新增** → `FAILED` + `errorCode=TIMEOUT` | 见红线 2 |
| （无） | `APPROVED` / `REJECTED` | **既有**：审批终态。**审批驳回不是错误码，是状态** |

> **为什么审批驳回不当错误码**：驳回是业务流程的正常分支（会走返工边），不是"调用失败"。
> 把它塞进 `AigErrorClassEnum` 会让灰度失败率统计把"业务退回"算成"系统错误"。

## 三、事件类型词表（设计文档 → 本契约）

| 设计文档 §6.4 | 本契约（= `AigTaskEventTypeEnum`） |
|---|---|
| `STEP_SUCCEEDED` / `STEP_STARTED` / `STEP_FAILED` | **V2 追加**：`AI_TASK_STEP_STARTED` / `AI_TASK_STEP_SUCCEEDED` / `AI_TASK_STEP_FAILED`（逐节点粒度，配合新增表 `aig_task_step`） |
| 进度事件 | `AI_TASK_PROGRESSED`（既有） |
| 状态变更 | `AI_TASK_STATUS_CHANGED`（既有） |
| 任务创建 | `AI_TASK_CREATED`（既有） |
| 结果回写 | `AI_TASK_RESULT_RECORDED`（既有） |
| 外部回调 | `AI_TASK_CALLBACK_RECEIVED`（既有） |
| 人工复核 | `AI_TASK_REVIEWED`（既有） |
| — | **V2 追加**：`AI_TASK_ARTIFACT_ADDED`、`AI_TASK_POLICY_DECIDED` |

**事件投递语义**（沿用既有 `aig_task_event`）：序号 `sequence` 在**单个执行内单调递增**；
消费方去重键为 **`(executionId, sequence)`**；账本在库里，SSE/WebSocket 只做实时推送，断线可按 `last sequence` 补读。

## 四、错误码：统一到 `AigErrorClassEnum`（+ V2 追加）

**完整表见 [`contract/error-codes.json`](contract/error-codes.json)**，含每条的四个处置开关与 HTTP 映射。

设计文档 §6.5 的词表 → 既有枚举的映射（**不新增枚举值**）：

| 设计文档写法 | 本契约 | 理由 |
|---|---|---|
| `INVALID_INPUT` / `SCHEMA_MISMATCH` | `INVALID_REQUEST` | 既有已含"参数或输出 Schema 错误" |
| `POLICY_DENIED` | `POLICY_DENIED` | 同名；细因用 `reasonCode` 区分 |
| `ORG_FORBIDDEN` | `POLICY_DENIED` + `reasonCode=ORG_FORBIDDEN` | 组织越权属策略拒绝；**不为它单开一个码** |
| `DEPENDENCY_UNAVAILABLE` | `UNAVAILABLE` | 语义相同 |
| `RUNTIME_TIMEOUT` | `TIMEOUT` | 语义相同 |
| `RATE_LIMITED` | `RATE_LIMITED` | 同名 |
| `APPROVAL_REJECTED` | **不是错误码** → 状态 `REJECTED` | 见红线 2 |
| `ARTIFACT_INVALID` | **V2 追加** `ARTIFACT_INVALID` | 制品级校验失败，与模型调用无关 |
| `TOOL_FAILED_RETRYABLE` | **V2 追加** `TOOL_FAILED`（`retryable=true`） | 工具不是模型 Provider，单列一类 |
| `SECURITY_QUARANTINE` | **V2 追加** `SECURITY_QUARANTINE` | 必须立即隔离并告警 |

**细因用 `reasonCode`，不扩错误码**：`errorCode` 是**可编程的粗分类**（决定重试/熔断/转人工），
`reasonCode` 是**给人看的细因**（如 `EXTERNAL_DISALLOWED`、`DATA_LEVEL_TOO_HIGH`、`QUOTA_EXHAUSTED`）。
把细因做成错误码会让枚举无限膨胀，且每次新增都要改客户端的 switch。

---

## 五、本契约对设计文档 §6.2/§6.3 示例的具体修改

| 位置 | 设计文档 | 本契约 | 原因 |
|---|---|---|---|
| 请求体 | 含 `principal` | **移除**（服务端补全） | 红线 3 |
| 请求体币种 | `"currency": "CNY"` | **`"currency": "USD"`（`const`）** | 平台在 R46 已统一币种为 USD（`cost_amount`/`budget_amount`/`cost_limit_amount` 的 COMMENT 均为 USD）。不统一就是一本对不上的账 |
| 请求体 `target.type` | `AGENT` | 枚举扩为 `AGENT`/`SKILL`/`TOOL`/`WORKFLOW`/`SCENARIO` | 设计文档 §2.1 的六个一等对象里，除 Model 外都应可作为执行目标 |
| `maxCost` 语义 | 未说清 | **本次执行的外层预算上限**；模型实际计费口径另在计费模块配置 | 沿用设计文档 §6.2 的说明，但写成契约字段注释 |
| 所有 ID 类型 | 示例用字符串 | **契约一律 `string`** | 平台把 Long 序列化为 JSON 字符串（防 JS 精度丢失，已实测）。契约必须与之对齐，否则客户端会踩到 `"123" != 123` |
| 结果 `usage.currency` | 无 | 加 `costAmount` + `currency: USD` | 便于计费对齐 |
| 错误响应 | `data.errorCode` 等 | 改为 `error` 对象（见 `execution-result.schema.json#/$defs/error`） | 与 RuoYi `code/msg/data` 外壳共存：外壳不变，细节在 `data.error` |

---

## 六、契约测试（本阶段已执行）

本目录的样例已用 **JSON Schema Draft 2020-12** 校验器逐条验证通过（`jsonschema 4.26.0`）。
校验脚本与结果见提交说明。**阶段 1 需把该校验搬进 CI**（对应设计文档 §20.1 的"契约层：阻断发布候选"）。

样例：

| 文件 | 覆盖 |
|---|---|
| `contract/examples/request-detail-page.json` | 正常请求（含场景、预算 USD、制品引用） |
| `contract/examples/result-succeeded.json` | 终态成功（版本快照 + 多产物 + usage） |
| `contract/examples/event-status-changed.json` | 事件（`AI_TASK_STATUS_CHANGED`） |
| `contract/examples/error-policy-denied.json` | 策略拒绝（`POLICY_DENIED` + `reasonCode=EXTERNAL_DISALLOWED`） |

---

## 七、尚未冻结、需评审确认的三点

1. **`SKILL` / `WORKFLOW` / `SCENARIO` 作为 `target.type` 的语义**：本契约按设计文档 §2.1 列为可选，但**阶段 1 只会用到 `AGENT`**。是否需要现在就冻结（会限制后续）由评审定；
2. **`maxWallSeconds` 与既有 `sweep()` 周期的关系**：超时判定目前由 `sweepTimeout()` 在清扫时执行，实际超时精度 = sweep 周期。契约里的 `maxWallSeconds` 是**声明**，实际精度需在评审时对齐（否则又是一个"声明与生效不一致"）；
3. **服务身份**：契约假定调用方有稳定的机器身份（`principal` 的来源之一）。**该能力目前不存在**（见基线 §3.3），需在阶段 1 之前补——否则契约里的 `principal` 只能由"人"的会话填充。

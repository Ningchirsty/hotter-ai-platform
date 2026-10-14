# 23 - Agent 平台优化 v1.0 · 阶段 E 实施说明（反馈队列与运营指标）

> 依据：附件《Agent 平台优化详细设计 v1.0》§6.9（反馈与运营）、§8.2（aig_result_feedback）、
> §12.1 E（反馈队列 指标 资产索引增量同步）
> 分支：`feature/aigov-optimization-v1`

---

## 1 本轮落地（D12）

### 1.1 反馈账本（`script/sql/aig_result_feedback.sql`）

一条反馈必须关联 `taskId/resultId` 至少其一，并记录 `agentVersionId`/`knowledgeVersionId`：
**缺一条就无法回答"这是哪次运行、哪个版本产出的哪条结果"**——反馈的价值正在于能定位到具体运行，
否则只是一条无法处理的感受。

- 反馈原因（`AigFeedbackReasonEnum`）：`USEFUL / FACT_ERROR / CITATION_MISMATCH /
  MISSING_SOURCE / FORMAT_MISMATCH / FLOW_INTERRUPTED`。用封闭枚举而不是自由文本：
  自由文本会让问题队列退化成收件箱。
- 处理结论（`AigFeedbackVerdictEnum`）：`PENDING / CONFIRMED / REJECTED`。
- 处理动作（`AigFeedbackActionEnum`）：`ADD_KNOWLEDGE / CHANGE_RULE / ADD_CASE / CLOSE`
  ——**都是"进入改进流程"，没有一个是"直接改线上版本"**（设计 §6.9：采纳反馈不直接改线上版本）。

### 1.2 问题队列规则

- 提交即进入 `PENDING`；
- **确认问题必须给出动作**：否则"问题被确认了却没人接着处理"——这正是反馈闭环最常断的一环；
- 驳回清空动作；
- `PENDING` 不是可提交的处理结论（它是未处理态）。

### 1.3 运营指标口径（`AigOperationsMetrics`，纯函数）

设计 §6.9 要求以完成任务数、结果采纳率、首次通过率、返工率、引用有效率、策略阻断、模型成本
为主。两条口径纪律写在代码里：

- **累计成果 = 经确认的结果记录**，不是"模型输出条数"——把所有输出都算成果，指标好看但无业务含义；
- **分母为 0 一律返回 0**（空周期是常态，不能让报表出现 NaN 或异常）；
- 比率保留 4 位小数。

---

### 1.4 反馈接口与权限（E-2）

| 方法与路径 | 门 | 说明 |
| --- | --- | --- |
| `POST /aigov/feedback` | `@SaCheckLogin` | 员工提交反馈（`@RepeatSubmit`） |
| `GET /aigov/feedback/queue` | `aig:feedback:queue` | 问题队列（可空=默认 PENDING） |
| `POST /aigov/feedback/{id}/handle` | `aig:feedback:handle` | 处理反馈（`@RepeatSubmit`） |

种子脚本 `script/sql/aig_feedback_menu.sql`（ID 段 `1769200000000000001..`，只授 `aig_admin`）。
提交用登录门而非权限点：反馈是员工动作，抬高入口只会让反馈收集失效。

---

### 1.5 运营指标聚合与接口（E-3）

`IAigOperationsMetricsService` / `AigOperationsMetricsServiceImpl`：从 `aig_task` / `aig_task_result`
取数，口径交 `AigOperationsMetrics`（纯函数）计算。

- 完成 = 状态 ∈ {`SUCCEEDED`,`APPROVED`,`REJECTED`}（失败不计入"完成"）；
- 首次通过 = `APPROVED` 且 `attempt_no ≤ 1`；返工 = `attempt_no > 1`；
- 策略阻断 = `policy_result = 'REJECT'`；累计成果 = `aig_task_result.candidate_status = 'APPROVED'`；
- **引用有效率、模型成本**暂无可聚合数据源，如实置 0（不用别的数字顶替）。

接口：`GET /aigov/operations/metrics`（`aig:ops:metrics`），种子 `script/sql/aig_ops_menu.sql`
（ID 段 `1769300000000000001..`，只授 `aig_admin`）。

---

### 1.6 故障分类指标（E-4）

`AigOperationsMetrics.faultClassCounts(List<String>)`（纯函数）按任务的错误分类编码计数：
次数降序、编码升序（**输出稳定才谈得上比对**）；空/null 编码统一记为 `UNKNOWN`。
接口 `GET /aigov/operations/fault-classes`（复用 `aig:ops:metrics`）。

---

### 1.7 反馈与运营页面（前端，E-5）

- `frontend/src/api/aigov/feedback/`：`submitFeedback` / `listFeedbackQueue` / `handleFeedback` /
  `getOperationsMetrics` / `listFaultClasses`；
- `frontend/src/views/aigov/feedback/presentation.ts`（纯函数 + `presentation.spec.ts`）：
  原因/结论/动作词表、码→文案（**认不出的码原样展示，不显示空白**）、结论标签色、
  **提交前校验与后端同口径**（确认问题必须给动作；`PENDING` 不是可提交结论）、比率百分比、
  故障分类行；
- `frontend/src/views/aigov/feedback/index.vue`：运营指标卡片 + 问题队列表格 + 处理弹窗；
- 页面菜单行 `script/sql/aig_feedback_pages.sql`（`component=aigov/feedback/index`，只授 `aig_admin`）。

> 页面行与权限行**分开种**：权限行必须与接口同时上线（少了就是 403）；页面行只在组件交付后才种——
> 先种会得到"点进去空白"的入口。

---

## 2 本轮**不做**的事（明确的边界）

1. **反馈接口与权限点（已在 E-2 补齐）**：`POST /aigov/feedback`（员工提交，`@SaCheckLogin`）、
   `GET /aigov/feedback/queue`（`aig:feedback:queue`）、
   `POST /aigov/feedback/{id}/handle`（`aig:feedback:handle`），
   种子脚本 `script/sql/aig_feedback_menu.sql`。
   提交刻意用登录门而不是权限点：反馈是员工动作，加权限点只会让"漏勾了就不能提反馈"——
   而反馈收集最怕入口太高。
2. **运营指标的聚合查询（已在 E-3 补齐）**：`GET /aigov/operations/metrics`（`aig:ops:metrics`）
   ——完成任务数 / 累计成果（= 经确认结果）/ 首次通过率 / 返工率 / 策略阻断。
   **引用有效率**与**模型成本**暂无可聚合数据源（引用明细在 `route_snapshot` JSON、成本需审计金额聚合），
   如实置 0 而不是用别的数字顶替。
3. **资产索引增量同步（已在 D12 续补齐）**：原先是全量刷新（`AigAssetIndexService#rebuildForUser`）。
   现在新增 **`syncNewForUser(userId)`（水位线增量）**：取索引里该域最大的 `asset_id` 作水位线，
   只补比它大的资产；**该域没有水位线时退回全量**；翻到"不大于水位线"的资产即停（各域端口都
   `ORDER BY id DESC`，三个实现均已核实）。入口是既有的
   `POST /aigov/portal/my-assets/refresh?mode=incremental`（不传 mode 仍是全量）。
   **两条明确的口径边界**：①**不删**——各域删掉的资产仍留在索引里，回收要走全量重建；
   ②**不更新**——源头改名/换大小不会被发现。因此完整用法是"定时增量 + 定期全量"。
   没有变更订阅（三个业务域都不对外发变更事件，加订阅要动它们的写路径，属独立变更）。
4. **故障分类指标（已在 E-4 补齐）**：`GET /aigov/operations/fault-classes` 按任务错误分类编码计数，
   次数降序、编码升序（输出稳定才谈得上比对）；空/null 编码统一记 `UNKNOWN`
   （把"没分类"混进具体分类会掩盖分类本身的问题）。

---

## 3 验证

- 新增测试：
  - `AigOperationsMetricsTest`（4）：采纳率以确认为分子、零分母返回 0、null 安全、四舍五入；
  - `AigResultFeedbackServiceImplTest`（6）：非法原因/无关联拒绝、进入队列、
    确认必须给动作、确认记录动作与处理人、驳回清空动作且 PENDING 不可提交、反馈不存在可读拒绝。
- 后端 `ruoyi-ai-gov` 全量：E-4 时为 **1156 tests / 0 failures / 0 errors**；
  加入资产索引同步（12 条新增测试）与流程/工具接线后，**当前 1271 tests / 0 failures / 0 errors**
  （数字随迭代前移，以 `24-总体说明` §4 为准）。
- 前端 `frontend`：**46 files / 448 tests** 全过（新增 `feedback/presentation.spec.ts` 6 条）；
  `oxlint src` 531 files 0/0；`check-script-setup` 对新增 `.vue` OK。

---

## 4 部署注意

- 需执行 `script/sql/aig_result_feedback.sql`（一张新表）。
- 本轮**不新增接口、不新增权限点**，对既有生产行为零影响。

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

## 2 本轮**不做**的事（明确的边界）

1. **HTTP 接口与权限点**：本轮不新增控制器（与 C-1 同一原因：接口须与菜单种子一起交付，
   且有 `AigPermissionSeedCoverageTest` 守卫）。`GET /aigov/tools`（可绑定工具清单）与
   工具定义/版本的登记接口随下一增量交付。
2. **子 Agent 编排与顺序流程**：设计 §6.5 的顺序步骤/条件/人工节点、子 Agent 最大深度与
   超时预算尚未实现。
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
- 后端 `ruoyi-ai-gov` 全量：**1124 tests / 0 failures / 0 errors**（阶段 C 后 1109 + 本轮 15）。

---

## 4 部署注意

- 需执行 `script/sql/aig_tool.sql`（三张新表）。
- 本轮**不新增接口、不新增权限点**，对既有生产行为零影响。

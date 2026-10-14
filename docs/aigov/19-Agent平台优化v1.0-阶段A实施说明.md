# 19 - Agent 平台优化 v1.0 · 阶段 A 实施说明（D01–D04、D08）

> 依据：附件《Agent 平台优化详细设计 v1.0》（2026-10-14）
> 基线提交：`fe305fc629e9990661097a7a3be86da1fc4714e3`（远端 `main`）
> 工作分支：`feature/aigov-optimization-v1`
> 本文只覆盖**阶段 A（基线一致性，P0）**。阶段 B–E 见后续文档。

---

## 1 本轮范围与结论

设计 §12.1 把「版本和权限正反例全部通过」定为阶段 A 的完成门槛，并要求在扩大员工试用前先做完它。
本轮落地 5 项 P0 问题：**D01 / D02 / D03 / D04 / D08**（D02 顺带把 D10 的助手能力独立一并做了）。

| 编号 | 问题（设计原文摘要） | 本轮处理 | 主要位置 |
| --- | --- | --- | --- |
| D01 | 通用任务执行只透传版本 ID，Prompt 由调用方提供 | 新增执行解析器 + 不可变执行计划，Agent 模式下系统 Prompt 取自冻结版本 | `execution/`、`task/service/impl/AigTaskExecutorImpl` |
| D02 | 测试与 Copilot 未把草稿 `allowExternal` 作为网关硬约束 | 引入业务侧外发许可（取与），贯通到路由引擎 | `AigInvokeBo`、`AigRouteHint`、`AigRouteServiceImpl`、两个 studio 服务 |
| D03 | 门槛查询只看 `enabled`、不看有效期；绑定缺少撤销入口 | 统一「启用中且在有效期内」口径 + 撤销入口 + 执行期复查授权 | `AigAgentRegistryServiceImpl`、`AigAgentRegistryController` |
| D04 | 进入 RUNNING 后缺能力编码 / ServiceException 可直接抛出 | 单一失败收口：执行态内任何异常都落成可解释失败 | `AigTaskExecutorImpl` |
| D08 | 网络调用被包在长事务里，崩溃会让 RUNNING 证据回滚 | 短事务登记 / 独立执行 / 短事务收尾（去掉外层 `@Transactional`） | `AigStudioTestServiceImpl`、`AigStudioCopilotServiceImpl` |
| D10 | Copilot 用被训练 Agent 的 `providerCapability` 路由文本提案 | 助手能力独立配置（默认 `text_generation`） | `AigStudioCopilotProperties`、`AigStudioCopilotServiceImpl` |

**向后兼容是硬约束**：D01 只在「目标版本带 `schemaVersion >= 2` 的结构化配置」时生效；
存量版本（Studio 旧格式配置）仍走原适配器，行为不变。D08 去掉的是「外层长事务」，
`create` 等落库方法各自的事务边界保留。

---

## 2 D01 执行解析器与不可变执行计划

### 2.1 新增对象

- `org.dromara.aigov.execution.AigExecutionPurposeEnum`
  —— `TRAINING / EVALUATION / CANARY / PRODUCTION`。`countsAsCanary()` 是「只有灰度真实使用
  计入灰度统计」的唯一实现点（设计 §6.6）。
- `org.dromara.aigov.execution.AigExecutionConfig`
  —— 版本配置 v2 的形状（`schemaVersion / providerCapability / dataPolicy / knowledgeRefs /
  toolRefs / limits / executorType / businessCategory / outputPresentation`）。
  **只认 `schemaVersion >= 2`**；解析失败、非对象、低版本一律返回 `null`（= 按旧适配器运行）。
- `org.dromara.aigov.execution.AigExecutionPlan`
  —— **不可变**计划：全字段 `final`、无 setter、集合用 `List.copyOf`。
  冻结 `agentVersionId / purpose / configHash / systemPrompt / inputHash / capabilityCode /
  dataLevel / allowExternal / maxCost / maxToolCalls / maxDepth / knowledgeRefs / toolRefs`。
- `IAigAgentExecutionResolver` / `AigAgentExecutionResolver`
  —— `resolve(...)`：读冻结版本 → 解析 v2 配置 → **复查版本授权**（D03）→ 计算 `configHash`
  与 `inputHash` → 产出计划。非 v2 返回 `null`。

### 2.2 与任务执行器的接线

`AigTaskExecutorImpl#runOnceRunning` 在组装 `AigInvokeBo` 后调用解析器：

- 计划非空时：
  - `invokeBo.prompt = plan.composePrompt(bo.prompt)` —— **系统指令来自版本冻结内容**，
    客户端提交的 `prompt` 只作为「## 业务输入」分节（外部资料不能覆盖系统指令，设计 §7.3）；
  - `invokeBo.capabilityCode = plan.capabilityCode`；
  - `invokeBo.allowExternal = plan.allowExternal`（D02 硬约束）；
  - 预算取计划的（若有）。
- 计划为空时：完全沿用旧语义。

`configHash / planId / inputHash / purpose` 一并写入任务的 `route_snapshot.executionPlan`
——它是「这次执行依据哪份配置」的持久证据，回答的问题比单个 `agentVersionId` 精确得多。
（§8.2 建议的独立 `aig_execution_plan` 表列入后续增量，理由见 §6。）

### 2.3 外发许可是「取与」

`AigAgentExecutionResolver#effectiveAllowExternal` 现在直接走 `AigEffectivePolicy.externalAllowed`：
版本 `allow_external` 与 v2 `dataPolicy.allowExternal` **任一为否即为否**；空白一律按否处理。
落地了设计 §7.2「有效外发许可要求企业策略、Agent 配置、资源策略、模型路由全部允许」。

### 2.4 有效权限逐层相交与有效数据等级（§7.2，后续增量补齐）

`AigEffectivePolicy`（纯函数，`AigEffectivePolicyTest`）：

- `intersect(declared, narrowed)`：**范围相交**——绑定只做收窄；收窄为空表示"不额外收窄"
  （空值语义是「不限」，不是「都不匹配」）。解析器据此把有效知识范围算成
  **版本 `knowledgeRefs` ∩ 启用的 KNOWLEDGE 绑定**，检索只带交集；
- `strictestDataLevel(...)`：**有效数据等级取各来源最高值**（任务登记等级 × 版本最低等级），
  不可识别项忽略；全不可识别返回 null（调用方不得据此放宽）；
- `externalAllowed(...)`：任意一方 `FALSE` 即为否，`null` 表示该方不额外限制。

> 身份层与岗位卡片层的相交（§7.2 的「登录身份 ∩ 岗位卡片」）仍待执行入口接线时补齐；
> 当前已落地"版本范围 ∩ 资源绑定"这一层。

### 2.5 授权上下文冻结（§7.1「身份授权 + 版本 + 输入 + 有效策略」）

`AigExecutionPlan` 新增 `authorizationContext`（`AigAuthorizationContext`：callerId / orgId /
roleCode / roleVersionId / actionCode），由解析器从**岗位启动记录**（`aig_launch_record`，
按 `taskId` 反查）读取并冻结。

- 它回答"这次运行以谁的身份、在哪个岗位卡片下发生"；身份与卡片来自**服务端记录**，
  不由请求体自报；
- 取不到时为 null，表示"本次没有岗位上下文"，**不是"身份未知可以放宽"**；
- 下游（知识检索、工具执行）可据此把身份/卡片范围纳入相交，而不再只按版本范围放行。

---

## 3 D02 业务侧外发许可是硬约束

- `AigInvokeBo` 新增 `Boolean allowExternal`（`null`=不额外限制）。
- `AigRouteHint` 新增同名字段与三参工厂；**类注释明确标注它是硬约束的唯一例外**
  （其余字段「只能更严、不能更松」的性质不变）。
- `AigRouteServiceImpl#doDecide`：在 STRICT 硬约束之后、记录策略命中之前，
  若业务侧声明 `false` 则强制 `allowExternal=false` 并写可见原因
  —— 策略行写错也必须拦得住。
- `AigStudioDataPolicy.externalAllowed(...)`：草稿 `allowExternal` 的解析（`Y`→不禁止，其余→禁止），
  训练台测试与 Copilot 都据此下发硬约束。

> 说明：路由策略的 `allowExternal='N'` 仍是否决项，业务侧的 `true` **不能**让外部候选复活
> （`AigRouteServiceExternalConstraintTest` 钉住）。

---

## 4 D03 绑定有效期、撤销与执行期授权

- **统一有效期口径** `countActiveBindings(agentVersionId)`：
  `enabled='Y'` 且 `effectiveFrom <= now <= effectiveTo`（两端为空表示该侧不限；闭区间，设计 §8.4）。
- **发布门槛** `assertBindingExistsIfLimitedChannel` 改用它 —— 修复「启用但已到期」的绑定
  让受限版本发布出去、但按有效期收窄时谁都看不见的矛盾。
- **执行期复查** `assertExecutionAuthorized(agentVersionId)`：
  受限通道必须有有效绑定；非受限通道不看绑定；裸能力调用（无版本）直接放行。
  解析器在产出计划前调用它（D01）。
- **撤销入口** `POST /aigov/agent/binding/{bindingId}/revoke`（权限沿用 `aig:agent:binding`）
  —— 逻辑删除（`@TableLogic`），撤销后门槛计数与执行授权立刻按新状态判定。
  **纯代码部署，无需新菜单/权限/建表。**

---

## 5 D04 / D08 故障收口与事务边界

### D04 单一失败收口

`AigTaskExecutorImpl#execute` 把置为 `RUNNING` 之后的工作收进 `runOnceRunning(...)`，
外层只有一个 catch：任何 `RuntimeException`（含 `ServiceException`）都经 `settleFailure(...)`
记一条 `UNKNOWN` 失败并抛出带「任务落点」的可读异常。补齐了两个旧缺口：
「未登记能力编码」直接抛、「调用入口 ServiceException」原样 rethrow。

### D08 短事务

`runTest` 与 `chat` 移除方法级 `@Transactional`：登记 RUNNING、网络调用、收尾现在分属不同事务
（每次 mapper 调用各自提交），崩溃后 RUNNING 行仍在。这同时消除了 2026-10-12 那个
`UnexpectedRollbackException` 事故的前提（内层 `create` 不再与外层共享事务）。
`AigStudioProposalTransactionBoundaryTest` 的守卫已按 D08 更新为「chat 绝不能带 `@Transactional`」，
并新增 `AigStudioShortTransactionGuardTest` 双重钉住。

---

## 6 本轮**不做**的事（避免被误读成已完成）

1. **`aig_execution_plan` 独立表**（设计 §8.2）：本轮把计划身份写进既有 `route_snapshot`，
   已能回答「依据哪份配置」。独立表涉及新表、Mapper、留存与清理策略，留到后续增量，
   避免现在就引入一张没有清理方的表。
2. **前端撤销绑定入口**：后端接口已就绪，界面按钮与表单属阶段 B/E（§12.2 的前端位置）。
3. **阶段 B–E**：训练台三栏与预检（D06/D07/D09/D11）、知识中心、工具执行、反馈运营均未开始。

---

## 7 验证

- 新增/更新测试：
  `AigAgentExecutionResolverTest`、`AigRouteServiceExternalConstraintTest`、
  `AigAgentBindingLifecycleTest`、`AigStudioShortTransactionGuardTest`，
  并在 `AigTaskExecutorImplTest`、`AigStudioCopilotServiceImplTest`、
  `AigStudioProposalTransactionBoundaryTest`、`AigRepeatSubmitCoverageTest` 中就地更新。
- 后端 `ruoyi-ai-gov` 全量测试命令：
  `mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false`
  （注意：根 `pom.xml` 默认 `maven.test.skip=true`，必须显式关闭才会跑测试。）
- 结果：**1077 tests / 0 failures / 0 errors**（基线 1053 + 本轮新增 24）。

---

## 8 落地到生产的注意点

- 本轮**未新增表、未新增菜单/权限**，属纯代码变更；部署后端镜像即可。
- 生产复验建议：
  1. `POST /aigov/agent/binding/{id}/revoke` 撤销一条测试绑定 → 绑定清单少一行、发布门槛计数下降；
  2. 撤销后直接执行该版本的受限通道任务 → 因「当前范围未授权」被拒绝（不是静默执行）；
  3. 训练台测试一个 `allowExternal=N` 的草稿 → 路由排除外部候选，`policyHits` 出现「业务侧声明不允许外发」；
  4. 拆掉网络制造一次缺能力编码 → 任务应落到 `NEED_HUMAN` 而不是停在 `RUNNING`。

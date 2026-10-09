# 0-5 F-08 接口草案：平台 ↔ VibePoster（**待对方确认，不是已定结论**）

> 状态：**草案**（2026-10-10）。F-08 在 `03-冻结清单-12项.md` 里是 ⛔ 未冻，Owner 待指定。
> 本文的作用：把"要向 VibePoster 负责方问什么、每个答案会改变什么、验收怎么算完成"写成一份**可直接发过去**的东西，
> 使 Owner 一旦指定就能开工，而不是从零对话。
> **凡标注「待确认」的都是我无法单方面定的**——我没有对方的 `/openapi.json`，也没有对端可联调。
> 本文不发明对端的接口；它把**平台侧已经确定的事实**与**需要对方回答的问题**分开写。

---

## 一、平台侧已经是事实的部分（不用谈，直接用）

这些都在仓库里、有测试、今天在生产上可验证，属于"给对方的既有条件"：

| 项 | 事实 | 依据 |
|---|---|---|
| 执行契约 | `Execution Contract v1`：`contractVersion / requestId / idempotencyKey / target / inputs / policy` 必填（`scenario`、`trace` 可选）；请求/结果/事件/回调四份 JSON Schema 都进了 CI 校验 | `docs/platform-v2/contract/*.schema.json`、`validate-examples.py`（CI 步骤「Verify execution contract examples」） |
| 回调入口 | `POST /aigov/task/callback`。**签名与 Provider 走请求头**、签名覆盖**原始请求体字节**；必填 `eventId / providerJobId / toStatus`（`errorCode/detail/progress` 可选）；按处理结论返回**真实 HTTP 状态码**；**IP 限流 120/分钟**；缺 Provider 头或坏 JSON **不进服务层、不写账本** | ADR-012 + `provider-callback.schema.json` |
| 回调语义 | `(providerCode, eventId)` 幂等；**没有时间窗**（重放靠幂等挡）；错误码由**平台**归类（`classifyCallbackError`）；**回调失败不自动重试**（落 FAILED） | ADR-012 的「必须同时接受的限制」 |
| 任务边界 | `aig_task.execution_mode`：`PLATFORM`=平台执行（走统一调用入口）；**`EXTERNAL`=业务域执行，平台只登记与展示、不执行也不扫描** | 基线补遗 §2.1、`AigTaskExecutionModeEnum` |
| 制品账本 | `POST /aigov/task/artifact` 登记制品：平台铸造 `artifact_id`、`storage_ref` 指字节、`sha256`、被拒也留 `FAIL` 证据行；`hash_verified` 标出「哈希是声明值还是被平台回读重算过」（**v1 一律 `N`**：平台不回读对象存储） | ADR-010 |
| 机器身份 | 服务令牌表/过滤器/管理面都在（`X-Service-Token` → Sa-Token 身份），**开关仍关**；这是外部调用方进入治理层的唯一合规身份 | ADR-002 追加前置、契约 §7.3 |
| 取消（平台侧） | `POST /aigov/task/{taskId}/cancel` 把任务置 `CANCEL_REQUESTED`；**平台没有向外部执行方推送的通道**（无 MQ、无出站 webhook） | `AigTaskController`、ADR 里的"无 MQ"实测 |

### 1.1 两条"看起来没事、其实会静默卡住"的地方（**这是本草案最该被拍板的部分**）

1. **平台侧的清扫只碰 `PLATFORM` 任务**（`AigTaskSchedulerImpl` 第 97/175/216 行都带
   `.eq(AigTask::getExecutionMode, PLATFORM)`）。因此 `EXTERNAL` 任务**永远不会被平台重试、也永远不会被判定超时**。
   后果：VibePoster 挂了/丢了任务 ⇒ 任务**永久停在 RUNNING**，而且**不会有任何报错**。
   → 需要定：这个"卡住"由谁发现？（对方定期回报？平台加一条只读的"EXTERNAL 停滞"报表？）
2. **取消只是平台侧的一次状态迁移**：`CANCEL_REQUESTED` 之后，把它推到 `CANCELLED` 的人是谁？
   对 `PLATFORM` 是执行器；对 `EXTERNAL` **没有任何通道告诉 VibePoster"别做了"**，
   也没有要求它回执。→ 需要定：取消是"平台标记 + 对方轮询"还是"平台直调对方的取消接口"。

---

## 二、需要 VibePoster 负责方回答的问题（每条都带"答案会改变什么"）

| # | 问题 | 为什么问 / 答案会改变什么 |
|---|---|---|
| **Q1** | 请提供 `/openapi.json`（或等价的实际接口清单）：路径、方法、鉴权方式、请求/响应体、错误码 | F-08 的字面未知项。没有它，任何对接设计都是猜 |
| **Q2** | 它的核心能力能否**一次调用完成**？还是必须由客户端串起三阶段（例如 生成素材 → 排版 → 导出）？ | **这是"要不要 Adapter"的决定点**（见 §三）。若必须三段串，则"事务性"落到客户端：中途失败留下半成品，谁负责清理 |
| **Q3** | 有没有**任务/作业 ID**概念？同一作业能否查询、能否取消、能否重放？ | 决定 `providerJobId` 怎么填、取消怎么做、以及回调能否对得上号 |
| **Q4** | 完成通知是**回调**还是**轮询**？若回调：能否带 HMAC 签名（与我方 `X-Hotter-Provider` + 签名头一致）；若轮询：最小间隔与速率限制 | 决定用 `POST /aigov/task/callback` 还是加一个轮询器；也决定限流口径 |
| **Q5** | 产物怎么取？返回 URL / 对象存储键 / 二进制流？**字节是否可重复获取**（同一链接二次下载是否一致）？ | 决定 `storage_ref` 与 `sha256` 的来源；也决定 `hash_verified` 能不能从 `N` 变成 `Y`（平台回读重算的前提是"能再取一次且应一致"） |
| **Q6** | 鉴权：接受 **服务令牌**（`X-Service-Token`）还是必须 OAuth/静态密钥？密钥如何轮换？ | 决定是否要打开服务令牌开关，以及要不要给对方发密钥 |
| **Q7** | 出站与数据等级：调用是否会出公网？会不会把产品图/品牌素材发到外部？ | 与 ADR-006 的 `OPAQUE` 审批、`data_level` 外发硬规则直接相关；`STRICT` 一律禁止外发是平台硬规则 |
| **Q8** | 速率与配额：并发上限、单作业最长耗时、失败重试策略（对方是否自带重试） | 决定平台侧超时阈值与是否需要退避；也避免"两边都重试"造成重复出图（**会真实计费**） |
| **Q9** | 幂等：同一 `idempotencyKey` 重复提交，对方是**返回同一作业**还是**再跑一次**？ | 平台已按 `(project_type, create_by, idempotency_key)` 幂等；若对方不幂等，重复提交就会重复花钱，必须在契约里写死"平台保证不重复提交"或"对方保证幂等" |

---

## 三、"要不要 VibePoster 侧 Adapter"怎么判（**判据先写下来，别到时候拍脑袋**）

| 若 Q2 的答案是… | 则 | 理由 |
|---|---|---|
| **一次调用完成**（单作业语义） | **不需要 Adapter**：平台按 `execution_mode=EXTERNAL` 派发一个作业，对方跑完回调一次 | 平台只需要一个 `providerJobId` 与一次 `toStatus`，没有任何跨阶段事务问题 |
| **必须客户端串三段** | **需要 Adapter**（对方侧暴露统一 Job API），或**把三段搬到平台侧编排** | 否则"事务性"落在平台：平台要自己维护中间态、清理半成品、处理"第二段失败但第一段已计费"。这是设计文档 §19.5 说"18 周是排布示意"的那类不确定工作量 |
| **有作业概念但无取消** | 可先接、**取消只做到 `CANCEL_REQUESTED` 并如实显示"对方不可取消"** | 不假装支持：页面显示"已请求取消，等待对方结束"，而不是显示"已取消" |

---

## 四、验收怎么算完成（A2-e 的骨架）

顺序即断言，**每一步都要有可查的证据**，不接受"看起来成了"：

1. **提交**：用 Execution Contract v1 的请求体提交一次（`contractVersion/requestId/idempotencyKey/target/inputs/policy` 齐全），
   记录返回的 `taskId`；
   - 证据：`aig_task` 一行、`aig_task_event` 有 `AI_TASK_CREATED`；
   - **幂等验证**：同 `idempotencyKey` 再提交一次 → **必须返回同一个 taskId**，且不新增任务行。
2. **派发**：任务进入 `DISPATCHED/RUNNING`，且 `provider_job_id` 被记下（来自对方或平台生成）；
   - 证据：`aig_task.provider_job_id` 非空。
3. **回调**：对方回调 `POST /aigov/task/callback`（签名头齐全）→ 返回 200；
   - 证据：`aig_callback` 一行，`payload_hash` **等于实际发送的字节**（证明 XSS 过滤器没改写请求体——这条已有测试，见 ADR-012）；
   - **反面验证**：把 `eventId` 重复发一次 → 幂等，不产生第二次状态迁移。
4. **终态**：任务到 `SUCCEEDED`；事件账本里能看到 `→ SUCCEEDED` 的那一条。
5. **制品**：产物以 `POST /aigov/task/artifact` 登记；
   - 证据：`aig_task_artifact` 一行，`sha256` 与**实际字节**一致（本地重算比对），`hash_verified` 如实标 `N`
     （平台不回读对象存储——**不许把它读成"验过"**）。
6. **审计**：`aig_invocation_audit` 有归属该 Agent 版本的行（`agent_version_id` 非空）——
   这是 `CANDIDATE→STABLE` 灰度证据的唯一来源。
7. **取消**（若 Q3 支持）：对第二个作业发 `POST /aigov/task/{id}/cancel`，断言
   **要么**对方收到并回执 `CANCELLED`，**要么**平台如实显示"已请求取消、对方不支持取消"。
   **不允许**出现"平台显示已取消、对方还在跑还没计费"这种账实不符。
8. **停滞**（对应 §1.1 第 1 条）：故意让一次作业不回执，断言**有人能发现它**——
   若选"平台报表"，则报表能列出停滞超过 N 分钟的 `EXTERNAL` 任务；若选"对方回报"，则契约里写明回报频率。

**今天是跑不了的**：`_local/acceptance-vibeposter.py` 只检查**平台侧前置条件**（路由存在、契约样例通过校验），
把需要对方在场的步骤标成 `TODO(counterparty)`，并在无法继续时**明确失败**而不是假装通过。

---

## 五、本草案**没有**做的事（如实列）

1. **没有发明对端接口**：全文没有一处断言 VibePoster 有哪些路径/字段——那正是 Q1 要问的。
2. **没有联调**：没有对端可用，任何"接口已通"的说法在今天都是假的。
3. **没有改动任何生产数据或开关**：服务令牌开关、限流阈值、清扫范围都没动。
4. **没有定 Owner、没有排期**：F-08 仍是未冻项；本草案只是把"要问什么、答案改变什么"准备好。
5. **Q7（出站/数据等级）与 Q6（密钥）涉及安全与法务**，我给了判据但没有替它们做决定。

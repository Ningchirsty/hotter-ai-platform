# P2 实施规范（招聘主线：需求 → 月度计划 → 计划任务 → 月度结转 → 岗位）

> 设计依据：`C:\Users\hehuo\.dsh\attachments\v1\files\5d\5d15789ebaea7cb46a181473a252355e27766faa851281a1a2415c66f569853a\招聘与人才管理一体化系统详细设计方案_RuoYi-Vue-Plus-v6.0.0.md`
> （下称「设计文档」；**该文件在 git 仓库之外，只读参考**）
>
> 前置：`docs/hr-talent/SPEC-P1-地基.md`（模块坐标、36 张表、菜单角色权限、通用约定全部沿用，不得重复定义）
>
> 仓库 `D:\DeepseekHarness\hotter-ai-platform`，分支 `feature/hr-talent`

---

## 0. P2 范围

对应设计文档 **§18 阶段 1 的业务部分** 与 **§21.16 第 5 步**。P1 已完成骨架、DDL、菜单字典。

**P2 交付**：

| # | 交付物 | 涉及表 |
|---|---|---|
| A | 招聘需求域（含变更历史、状态流转） | `hr_recruit_demand`、`hr_recruit_demand_change` |
| B | 公司月度计划 + 计划任务 + 月度结转（含结转领域服务与单测） | `hr_recruit_plan`、`hr_recruit_plan_item`、`hr_recruit_plan_rollover`、`hr_recruit_plan_application_rel` |
| C | 岗位执行项域 | `hr_recruit_job` |
| D | 前端四个页面与 API 封装 | — |

**不在 P2 范围**（属 P3/P4）：应聘记录/阶段流转/面试/背调（阶段 2）、人才主数据全部（阶段 3）、
驾驶舱与统计（阶段 4）、Excel 导入框架与数据迁移、简历解析、附件与敏感审计的完整实现。

> `hr_recruit_application` 尚不存在实体，P2 的 `hr_recruit_plan_application_rel` 只做表与
> Mapper 占位，**不实现**与应聘记录的联动逻辑；`credited_arrival_qty` 在 P2 由人工/结转写入。

---

## 1. 不可改动的既有资产（P1 产出）

- 包根 `org.dromara.hrtalent`，模块 `ruoyi-modules/ruoyi-hr-talent`。
- **权限串与角色标识一律取 `constant/HrTalentConstants` 的常量**，禁止手写字符串字面量。
- **错误码一律取 `support/HrTalentErrorCode` 的常量**，禁止新增错误码除非确有必要（新增须登记到该类）。
- 表结构以 `script/sql/hr_recruit.sql` 为**唯一事实来源**：实体字段名、类型、可空性必须与 DDL 逐字一致。
  写代码前**必须先读** `script/sql/hr_recruit.sql` 对应建表语句。

  > **DDL 补充说明**：设计文档 §9.2 的「核心字段」列是**核心字段摘要、不是全集**，§8.x 才是各功能页的字段清单。
  > 已经发现并修复的偏差（D1 修订）：
  > - `hr_recruit_job` 依据 §8.3 补充 `urgency`、`headhunter_flag`、`assistant_ids`、
  >   `first_interviewer_id`、`second_interviewer_id`、`expect_arrival_date` 六列
  >   （`hr_recruit.sql` 与 `hr_talent_migration.sql` 已同步）。
  > - 招聘需求的暂停/关闭/复开原因**记录在 `hr_recruit_demand_change.reason`**，
  >   需求表本身不设原因列——动作接口必须校验 `reason` 必填并写变更历史。
  >
  > 若实现中发现 §8.x 需要而 DDL 没有的字段：**不要自行加列**，上报主控统一补 DDL。

- `support/HrTalentOssHelper`、`domainservice/TalentScopeDomainService` 已存在，直接复用，不要重写。

---

## 2. 通用编码约定（设计文档 §21.2，必须遵守）

```text
org.dromara.hrtalent
├─ controller/recruitment/      ← P2 新增
├─ domain/entity/               ← 实体（P1 已建包，含 package-info）
├─ domain/bo/recruitment/       ← 入参 BO
├─ domain/vo/recruitment/       ← 出参 VO
├─ mapper/                      ← Mapper（含 package-info）
├─ service/recruitment/         ← 服务接口
├─ service/impl/                ← 服务实现
└─ domainservice/               ← 领域服务
```

- **Entity**：继承 `org.dromara.common.mybatis.core.domain.BaseEntity`；`@TableName("hr_recruit_xxx")`、
  `@TableLogic`（`del_flag`）、关键聚合根加 `@Version`（需求、岗位）。

  > **主键列名不是 `id`**，而是每张表各自的主键列，必须逐字使用 `@TableId(value = "xxx")`：
  >
  > | 表 | 主键列 | 表 | 主键列 |
  > |---|---|---|---|
  > | `hr_recruit_demand` | `demand_id` | `hr_recruit_plan` | `plan_id` |
  > | `hr_recruit_demand_change` | `change_id` | `hr_recruit_plan_item` | `item_id` |
  > | `hr_recruit_plan_rollover` | `rollover_id` | `hr_recruit_plan_application_rel` | `rel_id` |
  > | `hr_recruit_job` | `job_id` | | |
  >
  > 主键类型统一为 `Long`。**不要**写 `@TableId(value = "id")`。

- **BO**：用 `@AutoMapper(target = Xxx.class, reverseConvertGenerate = false)`；分组校验用
  `AddGroup`/`EditGroup`（`org.dromara.common.core.validate`）。
- **VO**：用 `@AutoMapper(target = Xxx.class)`；敏感字段（电话、身份证明细、背调明细）**不得**出现在列表 VO。
- **Mapper**：`extends BaseMapperPlus<Xxx, XxxVo>`。
- **Controller**：返回 `R<T>`；方法上 `@SaCheckPermission(HrTalentConstants.PERM_xxx)`；
  写操作加 `@Log(title = "...", businessType = BusinessType.XXX)`；
  要求幂等的提交类接口加 `@RepeatSubmit`。类上 `@Validated @RequiredArgsConstructor @RestController`，
  路径以 `/recruit/...` 开头（与前端菜单 `path` 一致）。
- **注释与 JavaDoc 一律中文**，类头 `@author hr-talent`。
- 分页查询用 `PageQuery` + `PageResult`，Controller 返回 `R<PageResult<T>>`
  （**本基线 v6.0.0 不存在 `TableDataInfo` 类**，全仓仅 1 处误引用；以 `ruoyi-ai-gov` 的写法为准）。
- VO 的**字典标签与用户名**用 `@Translation` 回填（`TransConstant.DICT_TYPE_TO_LABEL` /
  `USER_ID_TO_NAME`），与 `ruoyi-ai-gov` 同口径；依赖 `ruoyi-common-translation`（已加入模块 pom）。
  例：`@Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "status", other = "recruit_demand_status") private String statusLabel;`
- **日志禁止**记录电话明文、简历正文、背调明细、对象存储长期地址。
- **不引入任何新第三方依赖**（Excel 用 `ruoyi-common-excel`，OSS 用 `ruoyi-common-oss`）。
- **不要 `git commit` / `git push`**，由主控统一提交。

---

## 3. 接口契约（前端按此调用，不得改动路径）

来自设计文档 §11 与 §5.2 权限表。

### 3.1 招聘需求（A）

| 方法 | 路径 | 权限常量 | 说明 |
|---|---|---|---|
| GET | `/recruit/demands` | `PERM_DEMAND_LIST` | 分页查询，自动应用数据权限 |
| GET | `/recruit/demands/{id}` | `PERM_DEMAND_QUERY` | 详情 |
| POST | `/recruit/demands` | `PERM_DEMAND_ADD` | 新增草稿 |
| PUT | `/recruit/demands/{id}` | `PERM_DEMAND_EDIT` | 更新（带 `version` 乐观锁） |
| DELETE | `/recruit/demands/{ids}` | `PERM_DEMAND_EDIT` | 逻辑删除，仅草稿可删 |
| POST | `/recruit/demands/{id}/actions/{action}` | 见下 | 动作：`submit`/`pause`/`resume`/`complete`/`close` |
| GET | `/recruit/demands/{id}/changes` | `PERM_DEMAND_QUERY` | 变更历史 |

动作权限映射：`submit`→`PERM_DEMAND_SUBMIT`、`pause`/`resume`→`PERM_DEMAND_PAUSE`、`close`→`PERM_DEMAND_CLOSE`。
（`complete` 复用 `PERM_DEMAND_CLOSE`。）

### 3.2 月度计划与任务（B）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| GET | `/recruit/plans` | `PERM_PLAN_LIST` |
| GET | `/recruit/plans/{id}` | `PERM_PLAN_QUERY` |
| POST | `/recruit/plans` | `PERM_PLAN_ADD` |
| PUT | `/recruit/plans/{id}` | `PERM_PLAN_EDIT` |
| POST | `/recruit/plans/{id}/actions/{action}` | `confirm`/`close` → `PERM_PLAN_CONFIRM` / `PERM_PLAN_CLOSE` |
| GET | `/recruit/plans/{planId}/items` | `PERM_PLAN_QUERY` | 表头下任务分页 |
| GET | `/recruit/plan-items` | `PERM_PLAN_LIST` | 任务分页（跨计划查询） |
| POST | `/recruit/plans/{planId}/items` | `PERM_PLAN_ADD` | 新增独立任务 |
| PUT | `/recruit/plan-items/{id}` | `PERM_PLAN_EDIT` | 编辑（仅可改允许字段） |
| POST | `/recruit/plan-items/{id}/actions/{action}` | `pause`/`resume`/`cancel` → `PERM_PLAN_EDIT` |
| GET | `/recruit/plan-items/{id}/rollover-chain` | `PERM_PLAN_QUERY` | 跨月结转链 |
| POST | `/recruit/plan-items/{id}/refresh-status` | `PERM_PLAN_EDIT` | 触发单任务状态重算 |
| GET | `/recruit/plan-items/similar` | `PERM_PLAN_LIST` | 相似计划提示（同公司+部门+岗位） |

### 3.3 月度结转（B）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| POST | `/recruit/plan-rollovers/preview` | `PERM_ROLLOVER_PREVIEW` |
| POST | `/recruit/plan-rollovers/execute` | `PERM_ROLLOVER_EXECUTE` |
| POST | `/recruit/plan-rollovers/{batchNo}/retry` | `PERM_ROLLOVER_RETRY` |
| GET | `/recruit/plan-rollovers/{batchNo}` | `PERM_ROLLOVER_DETAIL` |
| GET | `/recruit/plan-rollovers` | `PERM_ROLLOVER_DETAIL` | 批次分页 |

### 3.4 岗位（C）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| GET | `/recruit/jobs` | `PERM_JOB_LIST` |
| GET | `/recruit/jobs/{id}` | `PERM_JOB_QUERY` |
| POST | `/recruit/jobs` | `PERM_JOB_ADD` |
| PUT | `/recruit/jobs/{id}` | `PERM_JOB_EDIT` |
| DELETE | `/recruit/jobs/{ids}` | `PERM_JOB_EDIT` |
| POST | `/recruit/jobs/{id}/actions/{action}` | 全部 → `PERM_JOB_CLOSE`（`close`/`reopen`） |
| POST | `/recruit/jobs/{id}/assign` | `PERM_JOB_ASSIGN` | 分配负责人/协助人/面试官 |

---

## 4. 业务规则（必须实现，来自设计文档 §7.1 / §8.2 / §8.3）

### 4.1 招聘需求

- 需求人数必须 **> 0**；计划到岗日期不得早于申请日期。
- 待招聘人数 = `max(需求人数 - 实际到岗人数, 0)`（派生字段，不落库或读取时计算，二者择一但需一致）。
- **关闭、暂停、复开必须填写原因**（`reason`），为空则报错。
- 状态机：`draft → submitted → recruiting → (paused ⇄ recruiting) → completed / closed`。
  非法流转必须拒绝（用 `HR_*` 错误码或 `ServiceException` 中文提示）。
- 进入 `recruiting` 之后，关键字段（人数、岗位、部门、负责人、到岗日期）修改**必须写 `hr_recruit_demand_change`**，
  记录 `change_type`、`before_json`、`after_json`、`reason`、`operator_id`。
- 状态编码一律用 `enums/DemandStatusEnum`（`draft`/`submitted`/`recruiting`/`paused`/`completed`/`closed`）。

### 4.2 月度计划与任务

- 一个公司 + 一个自然月 **只有一张表头**（`company_dept_id + plan_month` 唯一）。重复创建须复用或报错，由服务层判定并给出中文提示。
- `plan_month` 为 `yyyy-MM`；表头状态 `draft`/`executing`/`closed`（`PlanStatusEnum`）。
- **每次新增招聘任务都创建新记录**；即使公司、部门、岗位、人数相同也**绝不合并、覆盖或复用**。
  只能通过 `/recruit/plan-items/similar` **提示**存在相似计划。
- 新增任务 `source_type = new`；结转生成 `source_type = carryover`（`PlanSourceTypeEnum`）。
- **禁止**通过修改 `plan_month` 把原月任务挪到下月。
- 三个状态维度（`PlanControlStatusEnum` / `PlanExecutionStatusEnum` / `PlanCompletionStatusEnum`）：
  - `control_status`：人工控制（`normal`/`paused`/`cancelled`）。
  - `execution_status`：自动计算（`pending`/`recruiting`/`interviewing`/`offer`/`pending_arrival`）。
  - `completion_status`：按人数自动计算（`unfinished`/`partial_completed`/`completed`/`rolled_over`）。
- **主展示状态优先级**（§7.1.5，实现为领域服务方法）：
  ```text
  已取消 > 暂停 > 已完成(remaining_qty=0) > 已结转 > 待报到 > 待录用 > 面试中 > 招聘中 > 待启动
  ```
- `credited_arrival_qty > 0 && remaining_qty > 0` 时另显示「部分完成」标签，**不覆盖**执行阶段。
- `remaining_qty = max(plan_qty - credited_arrival_qty, 0)`，由服务维护，不得为负。

### 4.3 月度结转（§7.1.2，幂等是硬要求）

满足**全部**条件才生成下月任务：未取消、未完成、`remaining_qty > 0`、`carryover_enabled = true`、
且该来源任务对目标月份**尚未**生成过结转任务。

1. 目标月表头不存在时**自动创建**。
2. 每条符合条件的原任务**各建一条**下月任务，即使下月已有同岗位新计划也不合并。
3. 新任务复制公司、部门、岗位、负责人、期限、紧急程度等执行信息；
   `plan_qty = 原任务 remaining_qty`；`source_type = carryover`；
   `previous_plan_item_id = 原任务 id`；`root_plan_item_id = 原任务的 root_plan_item_id ?? 原任务 id`。
4. 原任务更新为 `rolled_over`，但**不修改**原 `plan_qty` 与原月份。
5. 写入 `hr_recruit_plan_rollover`（批次号、来源任务、目标任务、结转人数、执行时间、结果）。
6. **幂等**：靠唯一索引 `(source_item_id, target_month)`；重试时不得重复生成。
   单条失败要记录原因（`result`/`remark`）并**允许安全重试**，不得整批回滚已成功的条目。
7. 批次号用 `batch_no`，状态见 §13.4；先建批次再逐条执行（§9.6 事务边界）。

---

## 5. 前端契约（D）

新增目录（P1 已定）：

```text
frontend/src/api/hrtalent/demand/{index.ts,types.ts}
frontend/src/api/hrtalent/plan/{index.ts,types.ts}
frontend/src/api/hrtalent/rollover/{index.ts,types.ts}
frontend/src/api/hrtalent/job/{index.ts,types.ts}
frontend/src/views/hrtalent/demand/index.vue
frontend/src/views/hrtalent/plan/index.vue
frontend/src/views/hrtalent/rollover/index.vue
frontend/src/views/hrtalent/job/index.vue
```

- 组件路径必须与 `script/sql/hr_talent_menu.sql` 的 `component` 值**逐字一致**（`hrtalent/demand/index` 等）。
- 页面顶部用 `@/components/PageHeading`，传 `module="hrtalent"`。
- 技术栈：Vue 3 `<script setup lang="ts">` + Element Plus + `@/utils/request`。
- 按钮按权限串用 `v-hasPermi` 控制（权限串同 §3）。
- 列表用 `@/components/Pagination` 或既有列表模式，参照 `frontend/src/views/aigov/model/index.vue` 的写法。
- `pnpm lint` 必须 0 警告 0 错误；`pnpm build` 必须成功。
- 前端**不要**臆造后端字段：以 §3 契约与对应 `types.ts` 为准。

---

## 6. 验收与自检（每个 agent 交付前必须自行执行）

1. 编译通过：
   ```
   cd D:\DeepseekHarness\hotter-ai-platform
   cmd /c "D:\DeepseekHarness\.tools\mvn.cmd -o -B -pl ruoyi-modules/ruoyi-hr-talent -am -DskipTests compile"
   ```
2. 实体字段与 `script/sql/hr_recruit.sql` 的建表语句逐字段核对（数量、名称、类型、可空）。
3. 权限串全部来自 `HrTalentConstants`（用 grep 自查有无裸字符串 `"recruit:`）。
4. B 组必须为**结转幂等**与**主状态优先级**写单元测试（JUnit 5，放
   `src/test/java/org/dromara/hrtalent/...`），并保证 `mvn test` 能跑起来
   （若仓库未配置 surefire 可用 `-DskipTests=false` 验证；确实无法运行时须在汇报中说明，不得假装通过）。
5. 汇报中必须给出：新增文件清单、编译输出结论、以及与既有约定不一致之处的显式声明。

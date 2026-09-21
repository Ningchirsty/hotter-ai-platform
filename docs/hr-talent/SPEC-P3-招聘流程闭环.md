# P3 实施规范（招聘流程闭环 + 人才主档地基）

> 设计依据：`C:\Users\hehuo\.dsh\attachments\v1\files\5d\5d15789ebaea7cb46a181473a252355e27766faa851281a1a2415c66f569853a\招聘与人才管理一体化系统详细设计方案_RuoYi-Vue-Plus-v6.0.0.md`
> （下称「设计文档」；**该文件在 git 仓库之外，只读参考**）
>
> 前置：`SPEC-P1-地基.md`（模块坐标/36 表/菜单角色权限/通用约定）、`SPEC-P2-招聘主线.md`（需求·计划·结转·岗位）
> 仓库 `D:\DeepseekHarness\hotter-ai-platform`，分支 `feature/hr-talent`

---

## 0. P3 范围

对应设计文档 **§18 阶段 2「招聘流程闭环」** 与 **§21.16 第 6、7 步**。

> **为什么包含人才主档**：§7.6.3 规定「人才入库前必须执行重复预检」，且 `hr_recruit_application.talent_id`
> 必须指向 `hr_talent_profile`；§21.16 第 6 步原文即「实现**人才主档、查重**、应聘记录和阶段流转」。
> 因此 P3 交付**人才主档的最小可用闭环**，而**人才池/标签/跟进/共享授权/合并/简历版本/教育与工作经历
> 仍属 P4**（阶段 3 剩余部分）。

| 线 | 交付物 | 表 |
|---|---|---|
| A | 人才主档地基：创建/查重预检/检索/详情/更新/归档 + 关键字段变更历史 | `hr_talent_profile`、`hr_talent_profile_change` |
| B | 应聘记录 + 阶段流转 + 报到/Offer | `hr_recruit_application`、`hr_recruit_stage_log` |
| C | 面试安排/改期/取消/反馈（多人分别记录） | `hr_recruit_interview`、`hr_recruit_interviewer` |
| D | 背调（授权/核查项/结论/敏感权限） | `hr_recruit_background` |
| E | 业务附件（上传/受控预览下载/版本）+ 敏感审计查询 | `hr_recruit_attachment`、`hr_recruit_sensitive_audit` |
| F | 计划任务状态自动刷新接入（**第二波**，依赖 B/C/D 的事件） | — |
| G | 前端页面：候选人跟进 / 面试管理 / 背调与报到（**第二波**） | — |

**不在 P3 范围**：人才池与分组、标签、跟进记录、共享授权、重复合并、简历版本与解析、教育与工作经历、
Excel 导入与迁移、驾驶舱与统计、导出任务。

---

## 1. 不可改动的既有资产

- 包根 `org.dromara.hrtalent`，模块 `ruoyi-modules/ruoyi-hr-talent`。
- **权限串取 `constant/HrTalentConstants` 的常量；错误码取 `support/HrTalentErrorCode`**，禁止裸字符串。
- 表结构以 `script/sql/hr_recruit.sql` 与 `hr_talent.sql` 为**唯一事实来源**，写代码前必须先读建表语句。
- **主键列名是表级名，不是 `id`**：`hr_talent_profile`→`talent_id`、`hr_talent_profile_change`→`change_id`、
  `hr_recruit_application`→`application_id`、`hr_recruit_stage_log`→`log_id`、`hr_recruit_interview`→`interview_id`、
  `hr_recruit_interviewer`→`interviewer_id`、`hr_recruit_background`→`background_id`、
  `hr_recruit_attachment`→`attachment_id`、`hr_recruit_sensitive_audit`→`audit_id`。
- 分页统一 `PageQuery + PageResult`，Controller 返回 `R<PageResult<T>>`（**本基线无 `TableDataInfo`**）。
- VO 的字典标签与人员昵称用 `@Translation` 回填（`TransConstant.DICT_TYPE_TO_LABEL` / `USER_ID_TO_NICKNAME`）；
  **多值字段必须另提供拼成逗号串的只读 getter 作为 `mapper` 源**，否则翻译静默失败
  （参见 P2 岗位域 `RecruitJobVo.getAssistantIdText()` 的写法）。`@Translation` 只加在 VO 上。
- 复用 P1 的 `support/HrTalentOssHelper`、`domainservice/TalentScopeDomainService`；
  复用 P2 的 `support/RecruitBusinessNoGenerator`（`nextDemandNo`/`nextJobNo` 等，人才编号请沿用其风格另加方法）。
- **不要引入任何新第三方依赖**；**不要 `git commit`/`git push`**；注释与 JavaDoc 全中文，类头 `@author hr-talent`。

---

## 2. 接口契约（§11，路径不得改动）

### 2.1 人才主档与候选人（A）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| GET | `/talent/profiles` | `PERM_PROFILE_LIST` | 组合检索，**必须套人才可见范围** |
| GET | `/talent/profiles/{id}` | `PERM_PROFILE_QUERY` | 详情 |
| POST | `/talent/profiles/precheck` | `PERM_CANDIDATE_ADD` | 重复预检 |
| POST | `/talent/profiles` | `PERM_PROFILE_ADD` | 创建主档 |
| PUT | `/talent/profiles/{id}` | `PERM_PROFILE_EDIT` | 更新（关键字段写变更历史） |
| DELETE | `/talent/profiles/{ids}` | `PERM_PROFILE_EDIT` | 逻辑删除（仅无应聘记录可删） |
| POST | `/talent/profiles/{id}/archive` | `PERM_PROFILE_ARCHIVE` | 归档 |
| GET | `/talent/profiles/{id}/changes` | `PERM_PROFILE_QUERY` | 关键字段变更历史 |
| GET | `/recruit/candidates` | `PERM_CANDIDATE_LIST` | 候选人列表（= 有应聘记录的人才视图） |
| POST | `/recruit/candidates` | `PERM_CANDIDATE_ADD` | 新增候选人（**查重后**创建主档 + 可选应聘记录） |
| POST | `/recruit/candidates/precheck` | `PERM_CANDIDATE_ADD` | 候选人重复预检 |
| POST | `/recruit/candidates/{id}/phone-view` | `PERM_CANDIDATE_PHONE_VIEW` | **记录用途后**返回电话明文（写审计） |

### 2.2 应聘记录与阶段流转（B）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| GET | `/recruit/applications` | `PERM_CANDIDATE_LIST` |
| GET | `/recruit/applications/{id}` | `PERM_CANDIDATE_QUERY` |
| POST | `/recruit/applications` | `PERM_CANDIDATE_ADD` | 为已有主档创建应聘记录 |
| PUT | `/recruit/applications/{id}` | `PERM_CANDIDATE_EDIT` |
| POST | `/recruit/applications/{id}/transition` | `PERM_CANDIDATE_STAGE` | **阶段流转（核心）** |
| POST | `/recruit/applications/{id}/transfer` | `PERM_CANDIDATE_TRANSFER` | 转移招聘负责人/岗位 |
| GET | `/recruit/applications/{id}/stage-logs` | `PERM_CANDIDATE_QUERY` | 阶段历史（只追加） |
| POST | `/recruit/applications/{id}/offer` | `PERM_CANDIDATE_STAGE` | 登记邀约结果 + 计划报到日期 |
| POST | `/recruit/applications/{id}/arrival` | `PERM_CANDIDATE_STAGE` | 登记实际报到 |
| POST | `/recruit/applications/{id}/no-arrival` | `PERM_CANDIDATE_STAGE` | 登记未报到及原因 |
| POST | `/talent/profiles/{id}/applications` | `PERM_CANDIDATE_ADD` | 复用主档发起新应聘 |
| ↑ **实现偏差说明** | **实际未实现该路径**，复用主档发起新应聘走 `POST /recruit/candidates`（`CandidateCreateBo.talentId` 非空即表示复用已有主档）。 | 设计文档 §11 开篇明确「以下路径为**领域建议**，实施时可按现有工程路由规范调整」，故以已实现的路径为准；前端已按此对接 |

### 2.3 面试（C）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| GET | `/recruit/interviews` | `PERM_INTERVIEW_LIST` |
| GET | `/recruit/interviews/{id}` | `PERM_INTERVIEW_LIST` |
| POST | `/recruit/interviews` | `PERM_INTERVIEW_SCHEDULE` | 安排（指定轮次/时间/方式/地点/面试官） |
| PUT | `/recruit/interviews/{id}` | `PERM_INTERVIEW_SCHEDULE` | 改期（保留操作记录） |
| POST | `/recruit/interviews/{id}/cancel` | `PERM_INTERVIEW_CANCEL` |
| POST | `/recruit/interviews/{id}/feedback` | `PERM_INTERVIEW_FEEDBACK` | 面试官提交个人评分/结论/意见 |
| GET | `/recruit/interviews/my-todos` | `PERM_INTERVIEW_LIST` | 面试官待办 |

### 2.4 背调（D）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| GET | `/recruit/background-checks` | `PERM_BACKGROUND_LIST` |
| GET | `/recruit/background-checks/{id}` | `PERM_BACKGROUND_LIST` |
| POST | `/recruit/background-checks` | `PERM_BACKGROUND_ADD` |
| PUT | `/recruit/background-checks/{id}` | `PERM_BACKGROUND_EDIT` |
| GET | `/recruit/background-checks/{id}/detail` | `PERM_BACKGROUND_VIEW_SENSITIVE` | **敏感明细，独立权限 + 写审计** |

### 2.5 附件与审计（E）

| 方法 | 路径 | 权限常量 |
|---|---|---|
| POST | `/recruit/attachments` | `PERM_ATTACHMENT_UPLOAD` |
| GET | `/recruit/attachments` | `PERM_ATTACHMENT_PREVIEW`（列表） |
| GET | `/recruit/attachments/{id}/preview` | `PERM_ATTACHMENT_PREVIEW` | 鉴权后预览 + 写审计 |
| GET | `/recruit/attachments/{id}/download` | `PERM_ATTACHMENT_DOWNLOAD` | 鉴权并审计后下载 |
| DELETE | `/recruit/attachments/{ids}` | `PERM_ATTACHMENT_DELETE` | 逻辑删除 |
| GET | `/recruit/audits` | `PERM_AUDIT_LIST` | 敏感操作审计查询 |
| GET | `/recruit/audits/export` | `PERM_AUDIT_EXPORT` |

---

## 3. 业务规则（必须实现）

### 3.1 人才主档与查重（A）—— 依据 §7.6.1/§7.6.3/§8.4/§9.4/§21.14/§21.15

- **一人一档**：`hr_talent_profile` 是唯一主档；**不另建候选人主表**；候选人列表 = 存在应聘记录的人才视图。
- **入库前必须查重**（§7.6.3）：`POST /talent/profiles/precheck` 与 `POST /recruit/candidates` 都要先查重。
  匹配分级按 §21.15：
  - 强匹配：`phone_hash` 或 `email_hash` 命中；
  - 中匹配：姓名 +（公司 或 学校 或 简历哈希）；
  - 弱匹配：姓名 + 期望岗位。
  返回匹配项与原因，**只回可展示的摘要字段**。发现疑似重复时允许三种处置：复用已有主档 / 提交人工合并 /
  确认不是同一人；**不得静默创建重复人员**（合并本身属 P4，本阶段只做"复用"与"确认非同一人"）。
- **电话号码**：`phone_cipher` 存密文（`ruoyi-common-encrypt`），`phone_hash` 存**标准化后的不可逆哈希**（char(64)）
  用于查重；**列表默认只返回脱敏电话**，明文只能经 `phone-view` 接口获取且**必须写审计**。
  邮箱同理（标准化为小写后哈希）。`phone_hash`/`email_hash` **不加唯一约束**（§9.5）。
- **不得以姓名作为唯一判断条件**（§8.4）。
- 人才可见范围统一走 `TalentScopeDomainService`（§21.14），**不得在各 Controller 内各自实现授权规则**（§11.1）。
- 关键字段变更（姓名/电话/邮箱/身份证类/状态/负责人等）写 `hr_talent_profile_change`（`before_json`/`after_json`）。
- 人才编号用 `RecruitBusinessNoGenerator` 生成（如 `TAL…`，需在该类中**追加**方法，不要另造生成器）。
- 主档创建与首次联系方式写入必须**同一事务**（§21.7）。
- 创建成功后发布 `TalentProfileChangedEvent`（§21.6，消费者在 P4：重复检测/索引/审计）。

### 3.2 应聘记录与阶段流转（B）—— 依据 §7.2/§7.5/§7.6.2/§7.6.4/§8.4/§8.5/§9.6

- 阶段顺序（§7.2）：`新建 → 简历评审 → 待邀约 → 一面 → 二面 → 待背调 → 待录用 → 待报到 → 已报到`；
  任一阶段可转入 `淘汰 / 候选人放弃 / 暂缓 / 人才保留`。编码取 `enums/CandidateStageEnum`（已有）。
- **每次阶段变化必须写一条 `hr_recruit_stage_log`**（追加，不覆盖、不物理删除）。
  **阶段流转与历史写入必须在同一事务内**（§21.7）。
- **跳转前置校验（§7.2，必须逐条实现，违者抛中文提示）**：
  1. 进入**一面**前必须存在面试安排；
  2. 进入**二面**前必须有一面结论；
  3. 进入**待背调**前必须有最终面试结论；
  4. 进入**待录用**前必须有背调结论**或经授权的免背调原因**；
  5. 进入**待报到**前必须记录**邀约结果**和**计划报到日期**；
  6. 管理员例外跳转必须**填写原因**并写入审计。
- 乐观锁：`hr_recruit_application` 有 `version`，流转时校验版本，冲突给中文提示。
- 应聘记录保存**业务快照**（岗位、薪资期望等），**不复制人才基础信息**（§7.6.1）。
- 报到（§7.4/§21.7）：登记实际报到时，**同一事务内**完成「更新应聘记录 → 累加关联计划任务的
  `credited_arrival_qty` → 调用 `IPlanItemStatusService.refreshItemStatus(itemId)` 刷新状态」。
  **不得自行拼接状态值**（§11.1）。一个到岗结果只能计入一条有效月度任务（§9.4、§9.5）。
- 需求「实际到岗人数」由有效报到记录汇总（§7.4）。`hr_recruit_plan_application_rel` 用于表达计入关系
  （P2 已建表与 Mapper 占位，P3 需要真正写入）。
- 阶段变化后发布 `ApplicationStageChangedEvent`；报到后发布 `CandidateArrivedEvent`（§21.6）。
  事件只负责**后续派生刷新**，必须原子完成的写操作不交给事件（§21.6）。
- 招聘结束后的自动动作按 §7.6.4：已报到→人才状态置 `hired`；本次未通过但可保留→结束应聘、人才保持 `active`；
  候选人放弃→记录原因；明确不再联系→置 `do_not_contact` 并**记录原因**（`status_reason`，§7.6.2 强制）。
  **人才状态更新通过事件或调用人才服务完成，不要直接改对方表**。

### 3.3 面试（C）—— 依据 §7.3/§8.6

- 支持一面/二面/扩展轮次（`round_no`）；设置时间、方式、地点、面试官（一人或多人在 `hr_recruit_interviewer`）。
- 改期与取消**均保留操作记录**（§7.3）；缺席按 §8.6 处理。
- **多人面试分别保存个人意见**（`hr_recruit_interviewer.feedback`），汇总结论保存在 `hr_recruit_interview`。
- 提交结果后发布 `InterviewResultChangedEvent`（消费者：应聘阶段服务、计划状态服务）。
- 面试官待办：`GET /recruit/interviews/my-todos` 返回当前用户为面试官且未反馈的面试。
- `hr_recruit_interviewer.feedback_status`/`feedback_time` 要与反馈写入保持一致。

### 3.4 背调（D）—— 依据 §7.4/§8.7/§15.3

- 记录：是否取得授权（`authorized_flag`）、背调负责人、背调时间、**核查项**（`check_items`）、结论、
  未通过原因分类（`failure_reason_code`，字典编码不存中文）、敏感说明（`detail_cipher`，**密文**）、状态。
- **背调明细、附件、失败原因必须独立权限控制**（§8.7）：明细只在 `PERM_BACKGROUND_VIEW_SENSITIVE` 下返回，
  且**必须写审计**。列表/普通详情**不得**返回 `detail_cipher`。
- 免背调必须记录 `waive_reason`（§7.2 第 4 条依赖它）。
- 背调结论变化后发布事件（可由 B 的阶段校验与 F 的状态刷新消费）。

### 3.5 附件（E）—— 依据 §8.8/§9.4/§15.3

- 用 `HrTalentOssHelper`（P1）对接 OSS；**数据库只存 OSS 对象标识，不存永久公共下载地址**。
- 支持 `version_no` + `current_flag` + 上传人/时间；下载前**校验业务记录权限**；下载与预览**写审计**。
- 上传限制扩展名、MIME、大小、数量；删除默认逻辑删除（`PERM_ATTACHMENT_DELETE` 亦不得物理删除，§6）。
- 简历走独立的 `hr_talent_resume` 版本表（**属 P4**），通用附件表**不重复保存简历业务记录**（§8.8）。
- 审计查询接口 `GET /recruit/audits` 分页返回 `hr_recruit_sensitive_audit`，支持按 `event_type`/`biz_type`/
  `operator_id`/时间范围过滤。

### 3.6 敏感操作审计（跨域，统一入口）

**由主控已建** `support/SensitiveAuditRecorder`（`@Component`），所有需要留痕的地方**必须调用它**，不要各自写表：

```java
void record(String eventType, String bizType, Long bizId, String purpose, String result);
```

`eventType`/`bizType` 用稳定英文编码（如 `phone_view` / `attachment_download` / `background_view` / `export`；
`talent` / `application` / `interview` / `background` / `attachment`）。`result` 用 `success`/`denied`/`failed`。
记录器会自动取当前登录人、IP 与 User-Agent，**禁止把电话明文、背调明细写进 `detail_json`**。

**必须写审计的动作**：电话明文查看、背调明细查看、附件预览/下载、导出、管理员例外跳转。

---

## 4. 日志与安全硬约束（§11.1、§15、§21.9）

- 日志**禁止**输出电话明文、简历正文、背调明细、Token、签名下载地址。
- 接口只返回页面需要的字段，避免返回完整敏感对象。
- 列表默认脱敏（电话、邮箱）。
- 所有写接口启用参数校验与 `@RepeatSubmit`；写操作加 `@Log`。

---

## 5. 验收与自检（每个 agent 交付前必须自行执行）

1. 编译：
   ```
   cd D:\DeepseekHarness\hotter-ai-platform
   cmd /c "D:\DeepseekHarness\.tools\mvn.cmd -o -B -pl ruoyi-modules/ruoyi-hr-talent -am -DskipTests compile"
   ```
2. 实体字段与 DDL 逐列核对（列名/类型/可空），**主键必须是表级名**。
3. 自查无裸权限串（`grep '"recruit:'` / `'"talent:'` 只应命中 `HrTalentConstants`）。
4. 单测（**强烈建议，尤其是阶段流转校验与查重分级**）：本仓默认 `maven.test.skip=true` 且 surefire 按
   `<groups>${profiles.active}</groups>` 过滤，**测试类必须标 `@Tag("dev")`**，否则会被静默跳过；运行：
   ```
   cmd /c "D:\DeepseekHarness\.tools\mvn.cmd -o -B -pl ruoyi-modules/ruoyi-hr-talent -am -Dmaven.test.skip=false -DskipTests=false -Dtest=你的测试类 -Dsurefire.failIfNoSpecifiedTests=false test"
   ```
   汇报必须贴出 `Tests run: N`（**N=0 视为未运行，不得当作通过**）。不要用 Mockito（本机 JVM 禁止其自附加
   Agent），参照 P2 已跑通的 `RecruitJobServiceImplTest` 用 JDK 动态代理写替身。
5. **并行提醒**：同一 Maven 模块有多个 agent 同时改代码。若编译报错指向**你不负责的文件**，那是并行写入的
   瞬时状态，重试一次即可；**不要改别人的文件**，也不要跑 `mvn clean`。
6. 汇报须含：新增文件清单、编译真实输出、单测真实输出（或如实说明未跑）、与 SPEC 不一致之处（**不得隐瞒**）、
   发现的 DDL 缺口（**不要自行加列**，上报主控统一补）。

# P4 实施规范（人才管理剩余部分：简历/经历/人才池与分组/标签/跟进/共享授权/重复合并/导出）

> 设计依据：`C:\Users\hehuo\.dsh\attachments\v1\files\5d\5d15789ebaea7cb46a181473a252355e27766faa851281a1a2415c66f569853a\招聘与人才管理一体化系统详细设计方案_RuoYi-Vue-Plus-v6.0.0.md`
> （下称「设计文档」；**该文件在 git 仓库之外，只读参考**）
>
> 前置：`SPEC-P1-地基.md`、`SPEC-P2-招聘主线.md`、`SPEC-P3-招聘流程闭环.md`
> 仓库 `D:\DeepseekHarness\hotter-ai-platform`，分支 `feature/hr-talent`

---

## 0. P4 范围

对应设计文档 **§18 阶段 3「人才管理」的剩余部分**（人才主档与查重已在 P3 交付）。
**阶段 4（驾驶舱统计、Excel 导入与迁移）不在 P4**，留待下一阶段。

| 线 | 交付物 | 表 |
|---|---|---|
| A | 简历版本 + 异步解析任务 + 人工逐字段复核 | `hr_talent_resume`、`hr_talent_parse_task`、`hr_talent_parse_result` |
| B | 教育与工作/项目经历 | `hr_talent_education`、`hr_talent_work`、`hr_talent_project` |
| C | 人才池、分组与标签 | `hr_talent_pool`、`hr_talent_pool_member`、`hr_talent_tag`、`hr_talent_profile_tag` |
| D | 人才跟进 + 共享授权（含 P1 留下的 `TalentScopeGrantProvider` 落地实现） | `hr_talent_follow_up`、`hr_talent_scope_grant` |
| E | 重复治理与合并 | `hr_talent_duplicate_case`、`hr_talent_merge_log` |
| F | 人才检索增强 + 人才导出任务（**含 1 张新表**） | 新增 `hr_talent_export_task` |
| G | 前端页面：人才档案 / 人才池与分组 / 简历中心 / 重复人才治理 / 人才共享授权 | — |

**新增表（3 张，业务表 37 → 40）**——这是 `P2-决策与缺口台账.md §4` 已列明、并标注"开工到相应阶段必须先补"的结构性缺口：
1. `hr_talent_export_task`（§8.20、§11、§21.10）
2. `hr_talent_group`（§8.15「公共分组 / 个人收藏」、§5.1 菜单「人才池与分组」）
3. `hr_talent_group_member`

**不在 P4 范围**：Excel 导入与数据迁移（含 `hr_recruit_import_batch/import_error` 的落地使用）、
驾驶舱与统计指标、每日状态校准定时任务、简历解析引擎的真实接入（见下文 A 线边界）。

---

## 1. 不可改动的既有资产

- 包根 `org.dromara.hrtalent`；**权限串取 `HrTalentConstants`、错误码取 `HrTalentErrorCode`**，禁止裸字符串。
- `PageQuery + PageResult`、Controller 返回 `R<T>`/`R<PageResult<T>>`（**本基线无 `TableDataInfo`**）。
- **主键是表级名**：`hr_talent_resume`→`resume_id`、`hr_talent_education`→`education_id`、`hr_talent_work`→`work_id`、
  `hr_talent_project`→`project_id`、`hr_talent_pool`→`pool_id`、`hr_talent_pool_member`→`member_id`、
  `hr_talent_tag`→`tag_id`、`hr_talent_profile_tag`→`rel_id`、`hr_talent_follow_up`→`follow_id`（**不是 `follow_up_id`**）、
  `hr_talent_scope_grant`→`grant_id`、`hr_talent_duplicate_case`→`case_id`、`hr_talent_merge_log`→`merge_id`、
  `hr_talent_parse_task`→`task_id`、`hr_talent_parse_result`→`result_id`。**写代码前必须读 DDL 确认。**
- **复用不要重写**：`TalentScopeDomainService`（可见范围与资源级鉴权，唯一权威）、
  `SensitiveAuditRecorder`（敏感操作审计统一入口，永不抛异常）、`HrTalentOssHelper`（OSS）、
  `RecruitBusinessNoGenerator`（业务编号）、`TalentContactCodec`（电话/邮箱规范化+哈希+脱敏，P3 已建）、
  `event/` 下既有领域事件、`Domainservice/TalentDuplicateDomainService`（P3 已建查重分级）。
- **VO 用 `@Translation`** 回填字典标签与用户昵称；**多值字段必须另给拼逗号串的只读 getter 作 mapper 源**
  （参见 `RecruitJobVo.getAssistantIdText()`），否则翻译静默失效。
- 不要引入新依赖；不要 `git commit`/`git push`；注释与 JavaDoc 全中文，类头 `@author hr-talent`。

---

## 2. 各线接口契约与业务规则（路径不得自创）

### 2.1 A 线：简历版本与解析（§8.13、§8.21、§11）

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/talent/profiles/{id}/resumes` | `PERM_RESUME_LIST` |
| POST | `/talent/profiles/{id}/resumes` | `PERM_RESUME_UPLOAD` | 上传**新版本**（不覆盖旧文件） |
| POST | `/talent/resumes/{id}/current` | `PERM_RESUME_VERSION` | 指定为当前简历 |
| GET | `/talent/resumes/{id}/download` | `PERM_RESUME_DOWNLOAD` | 受控下载（**必填 purpose**，写审计） |
| GET | `/talent/resumes/{id}/versions` | `PERM_RESUME_LIST` | 版本列表 |
| POST | `/talent/resumes/{id}/parse` | `PERM_RESUME_PARSE` | 创建**异步**解析任务 |
| GET | `/talent/parse-tasks/{id}` | `PERM_RESUME_REVIEW` | 任务与候选结果 |
| POST | `/talent/parse-tasks/{id}/confirm` | `PERM_RESUME_REVIEW` | 人工确认选定字段并更新主档/经历 |

规则：
- **上传新简历默认创建新版本，绝不覆盖旧文件**；版本号 `version_no` 单调递增，`current_flag` 唯一有效。
- 简历走 `hr_talent_resume`，**不重复写入通用附件表**（§8.8）。
- **解析只创建任务，不在 HTTP 请求内同步执行 OCR/大模型调用**（§11.1）。
  一期**只实现任务与人工复核**，解析引擎未接入时任务停在 `pending`/`failed` 并给出明确提示 ——
  **不得把简历或个人数据发送给未批准的第三方服务**（§8.21）。
- 解析结果逐字段保存「原始值 / 标准化值 / 置信度 / 来源位置 / 解析器版本 / 复核结论」；
  **低置信度字段默认不勾选**；**正式字段只有在人工确认后才更新**（不得自动覆盖正式经历）。
- 上传需校验扩展名/MIME/大小；`scan_status` 为安全扫描状态（§8.13）。
- 简历下载必须**先经 `TalentScopeDomainService` 资源级鉴权**（该人才可见）**再写审计**再返回内容。

### 2.2 B 线：教育与工作经历（§8.14、§11）

- 三类经历 CRUD，挂 `/talent/profiles/{id}/educations`、`/works`、`/projects`，权限复用 `PERM_PROFILE_EDIT`（读用 `PERM_PROFILE_QUERY`）。
- 字段以 DDL 为准（教育：学校/专业/学历/学位/起止/是否全日制；工作：公司/部门/职位/起止/是否当前任职/职责/业绩/离职原因/行业；项目：名称/角色/起止/说明/职责/成果）。
- `source_type` 表达来源（人工/导入/解析）；**解析生成的内容在人工确认前不得直接覆盖正式经历**（§8.14）。
- 日期区间要校验（开始 ≤ 结束）；「是否当前任职」为真时结束日期应为空或按业务口径处理，需在 JavaDoc 写明。
- 删除为逻辑删除。

### 2.3 C 线：人才池、分组与标签（§8.15、§11）

| 方法 | 路径 | 权限 |
|---|---|---|
| GET/POST/PUT | `/talent/pools`、`/talent/pools/{id}` | `PERM_POOL_LIST` / `PERM_POOL_ADD` / `PERM_POOL_EDIT` |
| POST | `/talent/pools/{poolId}/members` | `PERM_POOL_MEMBER` | **重复加入返回已有关系**，不报错 |
| DELETE | `/talent/pools/{poolId}/members/{memberId}` | `PERM_POOL_MEMBER` | 移出：**只结束成员关系，不删人才主档** |
| GET/POST/PUT/DELETE | `/talent/groups`、`/talent/groups/{id}`、`/talent/groups/{id}/members` | `PERM_POOL_MEMBER`（个人收藏用 `PERM_PROFILE_LIST` 即可，见 JavaDoc 说明） |
| PUT | `/talent/profiles/{id}/tags` | `PERM_PROFILE_EDIT` | 更新标签关系 |
| GET/POST/PUT | `/talent/tags` | `PERM_PROFILE_EDIT`（查询 `PERM_PROFILE_LIST`） | 标签字典维护 |

规则：
- 人才池有管理员、可见范围、成员状态；加入时记录加入原因、推荐岗位、适配等级、加入人、加入时间、下次联系时间。
- **移出人才池只结束成员关系，不删除人才主档**（§8.15）。
- **分组**：`公共分组`（团队共同整理，由人才池管理员维护）与 `个人收藏`（用户个人快速访问，**不改变人才数据权限**）。
- **标签**：使用标签字典；**禁止随意创建敏感或歧视性标签**（§8.15）。请在服务层对 `sensitive_flag` 与名称做校验，
  且**背调失败/健康/家庭/年龄等敏感内容不得自动生成可被普通用户检索的标签**（§7.6.4）。
- 人才池/分组的可见性判定统一走 `TalentScopeDomainService`，不得自写规则。

### 2.4 D 线：人才跟进 + 共享授权（§8.16、§8.19、§11）

- 跟进：`POST/GET /talent/profiles/{id}/follow-ups`，权限 `PERM_PROFILE_EDIT`（列表 `PERM_PROFILE_LIST`）。
  记录联系时间/方式/结果/沟通摘要/意向岗位/意向变化/下次联系时间/跟进人与附件。
  **跟进与招聘阶段历史分开**（§8.16）；**沟通摘要不得保存与招聘无关的高度敏感个人信息**（服务层校验提示）。
- 共享授权：`POST /talent/profiles/{id}/grants`、`DELETE /talent/grants/{id}`，权限 `PERM_GRANT_ADD` / `PERM_GRANT_REVOKE`（列表 `PERM_GRANT_LIST`）。
  记录授权对象（用户/角色/公司部门/部门）、权限级别（summary < detail < attachment）、有效起止、授权人、授权原因。
  **共享只扩大查看范围，不自动授予电话明文、附件下载、背调和导出权限**（§8.19）——这条必须体现在 JavaDoc 与校验里。
- **落地 P1 留下的扩展点 `support/TalentScopeGrantProvider`**：实现它（`@Component`），
  使 `TalentScopeDomainService.hasGrant(...)` 能真正查到有效授权。实现要点：
  按 `GrantSubject`（类型+ID 成对）查 `hr_talent_scope_grant`，校验 `del_flag='0'` 且
  `valid_from <= now` 且（`valid_to is null` 或 `valid_to > now`），并比较权限级别。
  **这会让 P3 里"显式授权"分支首次真正生效**，请补单测。
- 授权过期立即失效；如有缓存必须同步失效（§11.1）。

### 2.5 E 线：重复治理与合并（§8.18、§21.7、§11）

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/talent/duplicates` | `PERM_DUPLICATE_LIST` |
| POST | `/talent/duplicates/{id}/confirm` | `PERM_DUPLICATE_CONFIRM` | 确认疑似重复 |
| POST | `/talent/duplicates/{id}/ignore` | `PERM_DUPLICATE_IGNORE` | 忽略 |
| POST | `/talent/duplicates/{id}/merge` | `PERM_DUPLICATE_MERGE` | **事务合并** |

- 三级匹配（§8.18）：强=标准化手机号哈希/邮箱哈希相同（**阻止静默新增**）；中=姓名+公司/学校/简历哈希；弱=姓名+岗位方向（**仅提示**）。
  P3 的 `TalentDuplicateDomainService` 已实现分级，**复用，不要重写**。
- **合并必须**：校验版本 → 获取分布式锁 → **在单个数据库事务内**转移应聘记录、简历、经历、附件、人才池成员、标签、跟进记录与授权范围（§21.7、§11.1）；
  人工选择冲突字段保留值；被合并主档标记 `merged` 并保存目标主档 ID；
  **写入完整合并快照（`hr_talent_merge_log`）与审计记录**；**不物理删除任何资料**。
- **只允许集团人才管理员执行**（§8.18）——用 `TalentScopeDomainService.isGroupLevelAdmin()` 之类的既有判定，不要自写角色判断。
- 合并后相关缓存/索引失效（`TalentMergedEvent` 已在 §21.6 定义，可实现并发布）。

### 2.6 F 线：人才检索增强 + 导出任务（§8.17、§8.20）

- 检索增强：在 P3 的人才列表基础上补齐 §8.17 的组合条件
  （手机号后四位、当前/历史岗位、意向岗位与标签、学历/专业/毕业院校、当前与意向城市、工作年限/行业/当前公司、
  来源渠道/归属公司/负责人、人才池/状态/最近联系时间、是否有当前简历/解析状态/资料完整度）。
  **默认结果必须叠加人才可见范围**（§8.17 硬要求），且统一走 `TalentScopeDomainService`。
  手机号后四位检索需基于 `phone_hash` 或规范化列实现，**不得解密全量比对**（请说明你的实现方式）。
- 导出（§8.20）：`POST /talent/profiles/export`（`PERM_PROFILE_EXPORT`）。
  - **普通台账**：人才编号、脱敏姓名、岗位方向、学历、区域、状态、标签、负责人等。
  - **敏感台账**：姓名、联系方式、薪资、附件访问地址 —— **需要独立权限和导出原因**（`purpose` 必填，写审计）。
  - 新增表 `hr_talent_export_task`：筛选条件、字段清单、导出人、用途、记录数、结果文件、过期时间、状态。
  - 导出文件存**私有对象存储**，**到期自动删除**；**不导出对象存储永久地址**（§8.13、§11.1），
    如导出附件链接必须是系统内受控访问地址。
  - 导出为**异步任务或限制最大条数**（§11.1）。

---

## 3. 新增 3 张表的 DDL 要求

必须**同时**改 `script/sql/hr_talent.sql`（初始化，`drop table if exists` + `create table`）
与 `script/sql/hr_talent_migration.sql`（幂等，`create table if not exists`，**不要 drop**），
两处列名列序定义**逐字一致**；通用字段（`del_flag` + 5 个 BaseEntity 字段 + `remark`）与本模块其他表一致；
格式（对齐、注释风格、`engine=innodb comment='...'`）**照抄同文件相邻表**。

- `hr_talent_export_task`：`task_id`(PK)、`task_no`、`export_type`（normal/sensitive）、`scope_json`（筛选条件快照）、
  `fields_json`（字段清单）、`exported_by`、`purpose`、`record_count`、`oss_id`、`file_name`、`status`、
  `expire_time`、`finished_time` 等 + 通用字段。索引：`(exported_by, create_time)`、`(status, expire_time)`。
- `hr_talent_group`：`group_id`(PK)、`group_name`、`group_type`（public 公共分组 / personal 个人收藏）、
  `owner_dept_id`、`owner_id`、`visibility_type`、`status` 等 + 通用字段。
- `hr_talent_group_member`：`member_id`(PK)、`group_id`、`talent_id`、`added_by`、`added_time` 等 + 通用字段；
  **唯一索引 `(group_id, talent_id)`**（防止重复加入）。

---

## 4. 验收与自检（每个 agent 交付前必须自行执行）

1. 编译：`cmd /c "D:\DeepseekHarness\.tools\mvn.cmd -o -B -pl ruoyi-modules/ruoyi-hr-talent -am -DskipTests compile"`
2. **实体 ↔ DDL**：`node D:\DeepseekHarness\.tools\entity-ddl-chk.mjs` 你的新实体必须 ✓（主键用表级名）。
3. **三文件 DDL 一致性**（若你新建了表）：`node D:\DeepseekHarness\.tools\ddl-sync-chk.mjs` 必须 0 问题。
4. **权限常量 ↔ 菜单**：`node D:\DeepseekHarness\.tools\perm-const-chk.mjs` 必须 exit 0
   （若你新增权限串，必须同时在两个 SQL 文件里补 F 按钮与角色绑定，否则接口对非超管永远 403）。
5. 单测（**必做**）：测试类**必须标 `@Tag("dev")`**（否则 surefire 按 `<groups>${profiles.active}</groups>` 会**静默跳过**）。
   运行：`... -Dmaven.test.skip=false -DskipTests=false -Dtest=你的测试类 -Dsurefire.failIfNoSpecifiedTests=false test`
   并贴 `Tests run: N`（**N=0 视为未运行，不得当通过**）。不要用 Mockito（本机 JVM 禁止其自附加 Agent），
   参考 `RecruitJobServiceImplTest` 用 JDK 动态代理写替身。
6. **并行提醒**：同模块有多个 agent 同时改代码。编译报错指向**你不负责的文件**时是并行瞬时态，重试一次；
   **不要改别人的文件**，不要跑 `mvn clean`。**若需修改共享文件（如 `HrTalentConstants`），先重读再增量追加**。
7. 汇报须含：新增/修改文件清单、编译真实输出、单测真实输出、与 SPEC 不一致处（**不得隐瞒**）、
   DDL 缺口（**不要自行加列**，上报主控）、未完成项。

# P1 地基实施规范（招聘与人才管理一体化系统）

> 设计依据：`C:\Users\hehuo\.dsh\attachments\v1\files\5d\5d15789ebaea7cb46a181473a252355e27766faa851281a1a2415c66f569853a\招聘与人才管理一体化系统详细设计方案_RuoYi-Vue-Plus-v6.0.0.md`
> （下称「设计文档」，引用时标注其章节号；**该文件路径在 git 仓库之外，只读参考**）
>
> 代码仓库：`D:\DeepseekHarness\hotter-ai-platform`，工作分支 `feature/hr-talent`
> 基线：RuoYi-Vue-Plus v6.0.0（commit 见仓库），Java 21 / Spring Boot 4.1

---

## 0. 本次范围（仅 P1 地基）

已完成的清理（无需再做）：旧 `ruoyi-talent` 模块、`ry_talent*.sql`、前端 `src/api/talent`、`src/views/talent` 已删除；
根 pom / `ruoyi-modules/pom.xml` / `ruoyi-admin/pom.xml` 的注册已改为 `ruoyi-hr-talent`；
`application.yml` 的 `spring.config.import` 已改为 `classpath:hr-talent.yml`。

P1 要交付：

| # | 交付物 | 负责 |
|---|---|---|
| 1 | 全部业务表 DDL（约 35 张，`hr_recruit_*` / `hr_talent_*`） | A |
| 2 | 模块骨架：pom、`hr-talent.yml`、公共枚举、常量、配置类 | B |
| 3 | 领域服务：`TalentScopeDomainService`、附件封装 | B |
| 4 | 菜单/角色/权限/字典 SQL（含幂等迁移版） | C |
| 5 | 前端 `PageHeading` 模块导航替换为 hrtalent | C |

**不在 P1 范围**：各领域 Entity/BO/VO/Mapper/Service/Controller 与页面（属 P2/P3/P4）。

---

## 1. 模块坐标（不得改动）

| 项 | 值 |
|---|---|
| Maven 模块 | `ruoyi-modules/ruoyi-hr-talent`，artifactId `ruoyi-hr-talent` |
| 包根 | `org.dromara.hrtalent` |
| 配置前缀 | `hrtalent` |
| 配置文件 | `src/main/resources/hr-talent.yml`（已被 `application.yml` import） |
| 表前缀 | 招聘过程 `hr_recruit_`；人才主数据 `hr_talent_` |
| 前端目录 | `frontend/src/api/hrtalent/`、`frontend/src/views/hrtalent/` |

SQL 文件命名（放入 `script/sql/`）：

| 文件 | 内容 |
|---|---|
| `hr_recruit.sql` | 招聘过程表 DDL |
| `hr_talent.sql` | 人才主数据表 DDL |
| `hr_talent_menu.sql` | 菜单、角色、角色菜单绑定、字典 |
| `hr_talent_migration.sql` | 上者的**幂等生产版**（见 §5） |

> **不要**新建 `ry_*` 文件；本次全部用 `hr_*` 前缀命名。

---

## 2. 表清单（严格对齐设计文档 §9.2）

**招聘过程表（`hr_recruit_`，共 17 张）**

`hr_recruit_demand`、`hr_recruit_demand_change`、`hr_recruit_plan`、`hr_recruit_plan_item`、
`hr_recruit_plan_rollover`、`hr_recruit_plan_application_rel`、`hr_recruit_job`、
`hr_recruit_application`、`hr_recruit_stage_log`、`hr_recruit_interview`、`hr_recruit_interviewer`、
`hr_recruit_background`、`hr_recruit_attachment`、`hr_recruit_standard`、`hr_recruit_channel`、
`hr_recruit_channel_survey`、`hr_recruit_peer_company`

> 另需 `hr_recruit_import_batch`、`hr_recruit_import_error`、`hr_recruit_sensitive_audit` —— 共 **20 张**。

**人才主数据表（`hr_talent_`，共 15 张）**

`hr_talent_profile`、`hr_talent_profile_change`、`hr_talent_resume`、`hr_talent_education`、
`hr_talent_work`、`hr_talent_project`、`hr_talent_pool`、`hr_talent_pool_member`、`hr_talent_tag`、
`hr_talent_profile_tag`、`hr_talent_follow_up`、`hr_talent_scope_grant`、`hr_talent_duplicate_case`、
`hr_talent_merge_log`、`hr_talent_parse_task`、`hr_talent_parse_result`

> 共 **16 张**。合计 **36 张**。以设计文档 §9.2 表格的字段为准，字段缺失或多出都算偏离。

**每个字段的核心字段名必须与 §9.2 表格逐字一致**（例如 `hr_recruit_plan_item` 必须有
`source_type`/`plan_qty`/`credited_arrival_qty`/`remaining_qty`/`control_status`/`execution_status`/
`completion_status`/`carryover_enabled`/`previous_plan_item_id`/`root_plan_item_id`/`carryover_batch_id`）。

---

## 3. 物理设计约定（设计文档 §21.13，必须遵守）

| 数据类别 | 类型 |
|---|---|
| 主键、用户/部门/业务关联 ID | `bigint` |
| 人数、版本号、重试次数 | `int`（人数需非负） |
| 状态、类型、字典值 | `varchar(32)`，存稳定编码不存中文 |
| 业务编号 | `varchar(64)`，建唯一索引 |
| 月份 | `char(7)`（`yyyy-MM`） |
| 日期 | `date` |
| 业务时间 | `datetime(3)` |
| 金额 | `decimal(12,2)` |
| 哈希 | `char(64)` |
| 密文 | `varchar(512)` 或 `text`，禁止截断 |
| 长文本 | `text` / `longtext` |
| 结构化快照 | `json` |
| 逻辑删除 | `char(1) default '0'` |

**通用字段**（每张业务表都要有，按 RuoYi 基线）：
`del_flag char(1) default '0'` + `create_dept`/`create_by`/`create_time`/`update_by`/`update_time`/`remark`。
**注意**：RuoYi 6.0.0 的 `BaseEntity` 只有 `create_dept/create_by/create_time/update_by/update_time` 五个字段，
`del_flag` 与主键由每个实体自声明。

**必须落的约束**（§9.5 索引 + §21.13 检查逻辑）：

- `hr_recruit_demand.demand_no`、`hr_recruit_plan.plan_no` 唯一索引。
- `hr_recruit_plan(company_dept_id, plan_month)` 唯一索引。
- `hr_recruit_plan_item(plan_id, execution_status, completion_status)`、`(job_id, plan_id)` 普通索引；
  **不得**对公司+岗位+月份建唯一约束。
- `hr_recruit_plan_rollover(source_item_id, target_month)` 唯一索引（保证结转幂等）。
- `hr_talent_profile.phone_hash`、`email_hash` 普通索引（**不加唯一约束**）。
- `hr_talent_profile(owner_id, talent_status)`、`(current_city, talent_status)` 索引。
- `hr_talent_pool_member(pool_id, member_status)` 索引、`(pool_id, talent_id)` 唯一索引。
- `hr_talent_profile_tag(talent_id, tag_id)` 唯一索引。
- `hr_talent_resume(talent_id, version_no)` 唯一索引，`file_hash` 普通索引。
- `hr_talent_scope_grant(talent_id, grantee_type, grantee_id, valid_to)` 索引。
- `hr_recruit_application` 按 `talent_id`/`job_id`/`current_stage`/`recruiter_id` 索引。
- `hr_recruit_stage_log(application_id, operate_time)` 索引。
- `hr_recruit_interview(schedule_time, status)` 索引。
- `hr_recruit_attachment(biz_type, biz_id, current_flag)` 索引，`file_hash` 索引。
- `hr_recruit_sensitive_audit(biz_type, biz_id, event_time)` 索引。
- 关键聚合根加 `version int default 0` 乐观锁字段（需求、岗位、应聘记录、人才主档）。

---

## 4. 菜单、角色、权限（设计文档 §5.1 / §5.2 / §6）

**菜单树**（`招聘管理` 为一级目录）：

```text
招聘管理 (dir)
├─ 管理驾驶舱        recruit:dashboard:view/export
├─ 招聘需求          recruit:demand:list/query/add/edit/submit/pause/close/import/export
├─ 公司月度计划      recruit:plan:list/query/add/edit/confirm/close/export
├─ 月度结转中心      recruit:rollover:preview/execute/retry/detail
├─ 岗位需求          recruit:job:list/query/add/edit/assign/close
├─ 候选人跟进        recruit:candidate:list/query/add/edit/transfer/stage/phone-view/export
├─ 面试管理          recruit:interview:list/schedule/feedback/cancel
├─ 背调与报到        recruit:background:list/add/edit/view-sensitive
├─ 人才管理 (dir)
│  ├─ 人才档案       talent:profile:list/query/add/edit/archive/phone-view/export
│  ├─ 人才池与分组   talent:pool:list/add/edit/member/share
│  ├─ 简历中心       talent:resume:list/upload/download/parse/review/version
│  ├─ 重复人才治理   talent:duplicate:list/confirm/merge/ignore
│  └─ 人才共享授权   talent:grant:list/add/revoke
├─ 招聘渠道          recruit:channel:list/query/add/edit
├─ 同行信息          recruit:peer:list/query/add/edit
├─ 招聘标准          recruit:standard:list/query/add/edit
├─ 数据导入中心      recruit:import:template/upload/confirm/cancel/detail
└─ 敏感操作审计      recruit:audit:list/export
```

**角色（9 个，设计文档 §6）**：
`hr_platform_admin`、`hr_recruit_admin_group`、`hr_talent_admin_group`、
`hr_company_recruit_owner`、`hr_recruiter`、`hr_dept_owner`、`hr_interviewer`、`hr_auditor`、`hr_talent_viewer`

**字典（23 组，设计文档 §10 表格）**：字典类型、编码值、用途严格按该表。

**ID 规划**（避免与既有模块冲突；现有 `ry_vue.sql` 已用 1761xxxxxxxxx，旧 talent 用 1762xxxxxxxxx 已删除）：

| 范围 | 用途 |
|---|---|
| `1763000000000000001` | 招聘管理一级目录 |
| `1763000000000000100` ~ `1763000000000000199` | 二级菜单 |
| `1763000000000000200` ~ `1763000000000000299` | 人才管理二级目录及其中菜单 |
| `1763000000000001000` ~ `1763000000000003999` | 按钮 |
| `1763100000000000001` ~ `...009` | 角色 |
| `1763200000000000001` ~ | 字典类型 |
| `1763300000000000001` ~ | 字典数据 |

---

## 5. 幂等迁移脚本要求

`hr_talent_migration.sql` 由 `hr_recruit.sql` + `hr_talent.sql` + `hr_talent_menu.sql` 合成，规则：

- 去掉全部 `drop table if exists`，改为 `create table if not exists`（**重跑不得清空数据**）。
- 全部 `insert into` 改为 `insert ignore into`（固定主键，重跑不重复插入）。
- 与生产发布约定一致：`script/deploy/README.md` 明确发布入口**不执行 SQL**，schema 变更必须手工迁移。

---

## 6. 后端骨架要求（设计文档 §21.2）

包结构：

```text
org.dromara.hrtalent
├─ controller/{recruitment,talent}
├─ domain/{entity,bo/{recruitment,talent},vo/{recruitment,talent}}
├─ mapper
├─ service/{recruitment,talent,impl}
├─ domainservice/      TalentScopeDomainService 等
├─ event/  listener/  job/  converter/  validator/  enums/  constant/  support/
```

P1 只需落地：`enums/`、`constant/`、`support/`、`domainservice/TalentScopeDomainService`、
`support/` 下的附件封装，以及 `config/`。

**约定**（设计文档 §21.2）：

- Entity 继承 `BaseEntity`，用 `@TableName`/`@TableId`/`@TableLogic`，需要时 `@Version`。
- BO 用 `@AutoMapper` 与分组校验；VO 用于出参，敏感字段不复用 Entity。
- Mapper 继承 `BaseMapperPlus<Entity, Vo>`。
- Controller 返回 `R<T>`，用 `@SaCheckPermission`/`@Log`/`@RepeatSubmit`。
- 注释与 JavaDoc 使用中文。

**P1 必须实现的领域服务**：

1. `TalentScopeDomainService` —— 按设计文档 §21.14 生成人才可见范围条件
   （`visibility_type` 的 group/company/department/owner/explicit + `hr_talent_scope_grant` 子查询），
   并提供资源级鉴权入口。**不依赖前端传入字段**。
2. 附件封装 —— 复用 `ruoyi-common-oss`；上传/下载/受控访问；只存 OSS 对象 ID，不存长期公网 URL
   （设计文档 §9.4）。附件表为 `hr_recruit_attachment` / `hr_talent_resume`。

**枚举必须覆盖**设计文档 §10 列出的全部编码值（状态、阶段、结果、类型等）。

---

## 7. 硬性约束

1. **不修改** `ruoyi-system` / `ruoyi-common` / `ruoyi-api` 等框架模块；只新增 `ruoyi-hr-talent` 内容，外加已经改好的 3 个注册文件与 `application.yml`。
2. **不引入**未经批准的第三方依赖；OSS 用 `ruoyi-common-oss`，Excel 用 `ruoyi-common-excel`（Apache Fesod），
   加解密用 `ruoyi-common-encrypt`，脱敏用 `ruoyi-common-sensitive`。
3. 日志禁止记录电话明文、简历正文、背调明细、对象存储长期地址（设计文档 §21.9）。
4. 所有新增注释与 JavaDoc 用中文。
5. **不要 git commit / git push**，由主控统一提交。
6. 完成后必须能编译：

```
cd D:\DeepseekHarness\hotter-ai-platform
cmd /c "D:\DeepseekHarness\.tools\mvn.cmd -o -pl ruoyi-modules/ruoyi-hr-talent -am -DskipTests -Dmaven.test.skip=true compile"
```
（首次若缺依赖去掉 `-o`）

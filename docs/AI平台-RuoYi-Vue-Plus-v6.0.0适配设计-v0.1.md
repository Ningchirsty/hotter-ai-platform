# AI 平台 RuoYi-Vue-Plus v6.0.0 适配设计 v0.1

## 1. 文档目标

本文将《AI 平台代码仓构建包 v1.1》的业务目标映射到官方
`RuoYi-Vue-Plus v6.0.0` 代码基线，作为模块拆分、数据库设计、接口设计、
前端实施、权限配置和迭代排期的第一版技术方案。

本版是架构与功能设计，不包含生产密码、IP、Token、业务人员数据或 ComfyUI
原始工作流。

## 2. 已验证的技术基线

| 项目 | v6.0.0 实际情况 | 本项目采用方式 |
|---|---|---|
| 上游版本 | Tag `v6.0.0`，提交 `7180b5297` | 固定 Tag，引入上游远程用于差异追踪 |
| Java | Java 21 | 统一使用 Java 21 |
| Spring Boot | 4.1.0 | 不自行降级或替换 |
| 权限认证 | Sa-Token | 复用登录、菜单、角色和按钮权限 |
| 数据访问 | MyBatis-Plus、MPJ | 业务模块保持同一持久层风格 |
| 数据权限 | `@DataPermission` + MyBatis 拦截器 | 视频列表与修改操作强制使用；**人才库不使用**，因其区域语义无法用框架 `DataScopeType` 封闭枚举表达，改为服务端显式区域过滤（依据见 `docs/talent/02-6.0.0框架API核对表.md`） |
| 多数据源 | dynamic-datasource | `master`、`video` 两数据源；**人才库业务表建在平台库内**，复用账号/组织/角色/菜单/数据权限 |
| 文件存储 | S3 兼容 OSS，支持 MinIO | 为业务 Bucket 配置独立 OSS 客户端 |
| 工作流 | WarmFlow | 用于视频审核、人才导出审批 |
| 实时通知 | SSE / WebSocket | 优先 SSE 推送视频任务状态 |
| 定时与重试 | SnailJob | 清理、超时补偿、指标汇总；任务主队列仍用 Redis |
| 前端 | 本仓库内置 `frontend/`，为 plus-ui 6.0.0 | 后端与前端同仓维护，多个业务共用同一前端工程 |

## 3. 设计假设与待确认项

### 3.1 当前假设

- 一期采用模块化单体后端，一个 `ruoyi-admin` 进程加载业务模块。
- MySQL、Redis、MinIO 与应用位于深圳平台内网。
- ComfyUI 已有可调用 API，GPU 执行适配器部署在 GPU 服务器上。
- 深圳与汕头组织已能在 `sys_dept` 中形成一棵稳定的组织树。
- 一期前端采用一个 `plus-ui` 工程，通过域名和角色加载不同首页与菜单。

### 3.2 实施前必须补齐的信息

| 信息 | 对设计的影响 |
|---|---|
| `plus-ui` 与后端 v6.0.0 的兼容 Tag | 决定前端依赖、路由和 API 类型生成方式 |
| ComfyUI API、回调和工作流参数样例 | 决定执行器协议及模板参数模型 |
| 深圳/汕头部门编码和人员唯一标识 | 决定人才数据导入、去重和数据权限 |
| 视频最大尺寸、时长、并发与超时 | 决定上传、队列、MinIO 生命周期和容量 |
| 审核层级与人才导出审批人 | 决定 WarmFlow 流程定义 |
| 是否启用 RuoYi 多租户 | 本方案默认不以租户隔离深圳和汕头，而以部门数据权限隔离 |

### 3.3 最常见的错误

最常见的错误是把视频、人才字段直接加到 `sys_user`、`sys_dept` 或其他
`ruoyi-system` 表中。这样会把业务生命周期与平台账号体系耦合，并显著增加以后
同步上游 v6.x 修复的冲突。本文只引用系统用户、部门、角色和 OSS 能力，业务数据
放在独立模块与独立数据库中。

## 4. 总体架构决策

```text
Cloudflare Tunnel
        |
      Nginx
        |
  单前端工程 frontend/（plus-ui 6.0.0）
  videoai / ai 由域名决定入口；
  人才库不设独立域名，作为 pm.hottter.cn 管理后台的一级菜单「集团人才库」
        |
  ruoyi-admin 模块化单体
  + ruoyi-system      账号、组织、菜单、角色、审计
  + ruoyi-video       模板、任务、审核、归档、统计
  + ruoyi-talent      人才档案、简历附件、重复预警、解析复核、台账导出、敏感审计
  + ruoyi-edge        执行节点、签名、调度、回调
        |
  MySQL（平台库 + 视频库）+ Redis + MinIO
        |
  内网 HTTPS/HMAC
        |
  GPU 执行适配器 -> ComfyUI -> 2 x A100
```

一期选择模块化单体，因为登录、角色、菜单、审计和业务事务都能直接复用 RuoYi，
运维复杂度也较低。GPU 执行适配器保持独立进程，它属于不同信任边界，不能与
面向用户的管理端共享进程或数据库凭据。

知识库、Agent 和 Skill 放到二期。v6.0.0 已带 `ruoyi-ai`，但一期业务模块不得
直接依赖它，以免视频生产流程与通用模型编排耦合。二期先评审其能力和数据访问
边界，再决定扩展还是新增模块。

## 5. 代码仓与模块设计

以官方 Maven 根结构为准，不再把 RuoYi 后端整体嵌套进 `apps/`：

```text
hotter-ai-platform/
├─ ruoyi-admin/                    启动模块，增加业务模块依赖
├─ ruoyi-api/                      跨模块稳定接口
├─ ruoyi-common/                   上游公共能力
├─ ruoyi-modules/
│  ├─ ruoyi-system/                上游系统模块
│  ├─ ruoyi-workflow/              上游审批模块
│  ├─ ruoyi-video/                 新增：视频领域
│  ├─ ruoyi-talent/                新增：人才领域
│  └─ ruoyi-edge/                  新增：GPU 节点与执行协议
├─ frontend/                       plus-ui 6.0.0 前端工程（已在本仓库内）
├─ deploy/ubuntu/                  部署、健康检查、回滚
├─ infra/                          Nginx、Cloudflare、监控配置
├─ script/sql/                      平台库与业务库的初始化 SQL（ry_*.sql）
├─ docs/                           架构、接口、ADR、运维手册
└─ .github/workflows/              CI 和发布流程
```

新增模块统一使用 `org.dromara.video`、`org.dromara.talent`、
`org.dromara.edge` 包名，遵循现有 Controller、BO、VO、Service、Mapper、Domain
分层以及 `BaseEntity`、`BaseMapperPlus`、MapStruct Plus 约定。

### 5.1 上游变更控制

- 上游代码原则上不改，业务扩展只进入新增模块。
- 必须修改上游的地方单独记录 ADR，并保持小提交。
- `upstream` 远程只用于拉取官方 Tag，不向其推送。
- 每次升级先比较 `v6.0.0..目标Tag`，再重放本项目的小范围补丁。

## 6. RuoYi 功能复用映射

| 业务需求 | 直接复用 | 新增适配 |
|---|---|---|
| 登录、验证码、Token | `ruoyi-admin`、Sa-Token | 失败锁定策略、管理员二次验证 |
| 用户、组织、岗位 | `ruoyi-system` | 人才档案只保存 `sys_user_id`、`dept_id` 引用快照 |
| 角色、菜单、按钮权限 | `sys_role`、`sys_menu` | 新增 `video:*`、`talent:*`、`edge:*` 权限标识 |
| 部门数据范围 | `@DataPermission` | 视频库切换时固定从 `master` 读取权限范围；人才库改用服务端区域过滤，不依赖框架数据范围 |
| 操作审计 | `@Log`、操作日志 | 状态流转、签名调用另建不可变业务事件日志；人才库另建 `tl_sensitive_audit` 记录下载/导出/完整信息访问 |
| 文件存储 | `ruoyi-common-oss` | 独立 Bucket 或对象键前缀、受控流式下载（人才库不下发预签名 URL） |
| 审批 | `ruoyi-workflow` | 视频审核和人才导出两类流程 |
| 任务通知 | `ruoyi-common-push` | 视频任务状态 Topic 与前端订阅 |
| 定时任务 | `ruoyi-job` / SnailJob | 超时回收、失败补偿、清理、指标聚合 |
| Excel 导入导出 | `ruoyi-common-excel` | 人才模板校验、导出审批、导出水印与审计 |

## 7. 权限与数据隔离

### 7.1 权限模型

建议首期角色：

| 角色 | 主要权限 | 数据范围 |
|---|---|---|
| `video_creator_sz` | 创建、查看本人/本部门任务 | 深圳部门及以下 |
| `video_reviewer_brand` | 审核、退回、查看成片 | 品牌审核范围 |
| `video_operator` | 模板、失败重试、节点查看 | 视频全量，不含人才 |
| `talent_hr_sz` | 深圳人才维护 | 可见 `SZ` + `GROUP`，仅可写 `SZ` |
| `talent_hr_st` | 汕头人才维护 | 可见 `ST` + `GROUP`，仅可写 `ST` |
| `talent_group_admin` | 授权范围内跨地区维护和统计 | 全部区域 |
| `talent_admin` | 人才库全部权限与单条授权配置 | 全部区域 |
| `talent_viewer` | 用人部门查阅者 | 仅单条授权，手机号脱敏 |
| `talent_auditor` | 敏感操作审计 | 只读审计记录 |
| `platform_admin` | 账号、角色、菜单、配置 | 无人才内容修改权限 |
| `edge_operator` | 节点维护、任务诊断 | 技术管理员专用 |

权限标识示例：

```text
video:template:list/query/add/edit/publish
video:task:list/query/add/submit/cancel/retry/download
video:review:list/query/approve/reject
video:stats:view
talent:profile:list/query/add/edit/archive/phone/grant
talent:attachment:manage/upload/download
talent:duplicate:view/confirm
talent:parse:view/confirm/retry
talent:export:create/download
talent:audit:list
edge:node:list/query/add/edit/disable
edge:task:list/query/reconcile
```

Controller 使用 `@SaCheckPermission`，导出、审核、模板发布、失败重试等动作增加
`@Log`。Mapper 的查询、更新、删除都使用 `@DataPermission`，避免只保护列表接口
而遗漏按 ID 查询和修改接口。

### 7.2 数据库适配

```text
master  -> ruoyi_platform   （含人才库业务表 tl_*）
video   -> ruoyi_video
```

视频业务 Service 类标记 `@DS("video")`。禁止一个本地事务中
同时写两个数据库；跨库业务采用“本库事务 + 事件/补偿”模式。

**人才库不新建独立数据库**：`tl_*` 业务表建在平台库内，复用账号、组织、角色、
菜单与数据权限；人才库不切换数据源，因此不需要 `@DS` 注解，也不涉及跨库事务。

RuoYi 数据范围服务 `sdss` 会读取平台库的 `sys_dept` 和 `sys_role_dept`。当业务
Service 已切换到 `video` 数据源时，必须确保该服务显式使用
`@DS("master")`。这是当前基线下预计唯一需要评审的上游小补丁；实施时先做集成
测试，确认 dynamic-datasource 的嵌套切换行为，再决定在原服务加注解，或用框架
支持的自定义 `ISysDataScopeService` 替换实现。

数据库账号最小权限：平台账号只访问 `ruoyi_platform`（含人才库 `tl_*` 表）；视频账号只访问
`ruoyi_video`。应用配置中的凭据只来自生产
`.env`，不进入 Git。

### 7.3 人才字段保护

- 一期不存储身份证、薪酬、健康信息。
- 手机、邮箱等字段使用 RuoYi 脱敏注解输出；确需存储的敏感值使用字段加密。
- 导出文件写入 `talent-export`，设置短生命周期和一次性下载授权。
- 导出记录包含申请人、审批人、筛选条件、字段范围、对象键、下载次数和过期时间。
- 超级管理员不能凭平台身份直接获得人才业务菜单；必须另行授予人才角色。

## 8. AI 视频中心设计

### 8.1 子域

| 子域 | 职责 |
|---|---|
| 模板 | 业务参数定义、工作流版本、模型版本、白名单、发布状态 |
| 任务 | 草稿、输入素材、提交、状态、优先级、结果 |
| 调度 | Redis 队列、GPU 槽位、租约、心跳、重试 |
| 执行 | 请求签名、幂等、执行器调用、回调验签 |
| 审核 | WarmFlow 流程、意见、通过、退回 |
| 归档 | 成片、保留期、下载、清理 |
| 运营 | 耗时、P95、显存、成功率、审核通过率 |

### 8.2 核心表

| 表 | 关键字段 |
|---|---|
| `video_template` | 模板编码、类型、参数 Schema、当前版本、状态、负责人 |
| `video_template_version` | 工作流引用、模型版本、参数映射、校验摘要、发布时间 |
| `video_task` | 任务号、幂等键、模板版本、用户、部门、状态、优先级、租约 |
| `video_task_input` | 输入类型、OSS 对象键、摘要、大小、MIME |
| `video_task_output` | 输出对象键、摘要、时长、分辨率、文件大小 |
| `video_task_event` | 任务号、前后状态、事件类型、操作者、时间、原因 |
| `video_review` | 流程实例、审核人、结果、意见、完成时间 |
| `video_metric_daily` | 模板/模型/GPU 维度的每日汇总 |

所有业务表使用雪花 ID，并保留 `create_dept`、`create_by`、`create_time`、
`update_by`、`update_time`。任务号使用不可猜测的业务编号，外部接口不暴露连续 ID。

### 8.3 状态机

```text
DRAFT
  -> SUBMITTED -> QUEUED -> ASSIGNED -> RUNNING -> TRANSFERRING
  -> PENDING_REVIEW -> APPROVED -> ARCHIVED
                         |             |
                         -> REJECTED --+

任意执行阶段 -> FAILED_VALIDATION / FAILED_EXECUTION /
               FAILED_TIMEOUT / FAILED_TRANSFER / CANCELLED
```

状态只能通过领域服务执行合法迁移。每次迁移同时写 `video_task_event`，并使用
乐观锁版本号防止回调、人工操作和补偿任务互相覆盖。

### 8.4 队列和 GPU 租约

- Redis Stream 作为一期任务队列，消费者组负责领取与确认。
- 每个 GPU 槽位以 Redis 锁和数据库租约双重约束，一张卡默认一个长任务。
- Worker 领取后写 `ASSIGNED`、节点、GPU、租约到期时间，再调用执行器。
- 心跳延长租约；超时补偿任务将失联任务转为超时或重新排队。
- 重试创建新的执行尝试记录，不覆盖旧错误；幂等键保持业务提交唯一。
- 高优先级只影响尚未分配的任务，不抢占正在运行的任务。

### 8.5 GPU 执行器协议

平台只发送模板编号和版本，不发送任意 Workflow JSON 或本地路径。请求至少包含：

```json
{
  "taskNo": "VID-...",
  "attemptNo": 1,
  "idempotencyKey": "...",
  "templateCode": "t2v-brand-01",
  "workflowVersion": "3",
  "modelVersion": "wan-...",
  "inputs": [{"objectKey": "video-input/...", "sha256": "..."}],
  "parameters": {},
  "priority": 50,
  "timestamp": 0,
  "expiresAt": 0,
  "nonce": "...",
  "signature": "..."
}
```

采用 HTTPS + HMAC-SHA256，签名覆盖请求方法、路径、时间戳、nonce 和 body 摘要。
执行器校验时钟偏差、有效期、nonce、防重键和模板白名单。回调使用独立密钥并带
结果文件摘要。密钥轮换支持新旧双密钥短期并存。

### 8.6 API 草案

```text
GET    /video/templates
POST   /video/tasks
PUT    /video/tasks/{taskNo}
POST   /video/tasks/{taskNo}/submit
POST   /video/tasks/{taskNo}/cancel
POST   /video/tasks/{taskNo}/retry
GET    /video/tasks/{taskNo}
GET    /video/tasks/{taskNo}/events
POST   /video/reviews/{taskNo}/approve
POST   /video/reviews/{taskNo}/reject
GET    /video/stats/overview

POST   /edge/v1/tasks/claim-result       内网回调
POST   /edge/v1/tasks/progress           内网回调
POST   /edge/v1/tasks/complete           内网回调
POST   /edge/v1/tasks/fail               内网回调
```

前端通过 RuoYi SSE 通道接收状态变化；数据库仍是最终事实来源，断线重连后重新拉取。

## 9. 集团人才库设计

### 9.1 系统与人才边界

`sys_user` 表示可登录账号，人才档案表示人员业务记录，两者不是一一等价：离职人员
可以保留档案但停用账号，尚未开通账号的人员也可以先进入人才库。档案通过可空的
`sys_user_id` 关联账号，并保存组织、岗位名称快照用于历史追溯。

### 9.2 核心表

一期交付的是**招聘人才资料库**口径，业务表统一 `tl_` 前缀、建在平台库内，完整定义见
`script/sql/ry_talent.sql`，字段字典见 `docs/talent/03-数据字典与字段规范.md`。

| 表 | 关键字段 |
|---|---|
| `tl_talent` | 人才编号、姓名、手机号密文/哈希/后四位、性别、出生日期、学历、期望薪资、岗位、联系日期、区域、状态、共享范围 |
| `tl_talent_attachment` | 人才、对象键、附件类型、版本、当前版本标记、文件哈希、扫描状态 |
| `tl_talent_contact` | 人才、联系时间、联系人、结果、备注 |
| `tl_talent_duplicate` | 来源人才、命中人才、匹配规则、匹配分数、确认结论、确认人 |
| `tl_talent_access_grant` | 人才、被授权主体（用户/角色）、权限、生效/失效时间、授权人、原因 |
| `tl_parse_task` | 附件、状态、解析服务版本、重试次数、错误摘要（不存简历正文） |
| `tl_parse_field` | 任务、字段名、解析值、置信度、确认值、确认人/时间 |
| `tl_export_task` | 发起人、查询条件快照、状态、对象键、行数、过期时间 |
| `tl_sensitive_audit` | 操作人、动作、对象、结果、IP 摘要、原因、时间 |

> **二期**：技能、培训、人才盘点、组织档案历史等属于后续范围，届时另立表与菜单，
> 不与一期招聘资料库混用表前缀语义。

### 9.3 关键流程

```text
人才新增：提交 -> 按钮权限 + 可写区域校验 -> 手机号规范化与哈希 -> 重复预检
          -> 无命中：建档 / 有命中：返回疑似清单，HR 确认“不同人”才建档（禁止命中即覆盖）
简历上传：格式/大小/权限校验 -> 自定义对象键写入私有桶 -> 扫描状态机
          -> 仅 CLEAN 可下载 -> 创建解析任务（未获批时落 DISABLED）
查询下载：列表服务端注入区域范围 -> 返回按角色脱敏 -> 点附件二次校验单条授权与扫描状态
          -> 服务端流式输出 -> 写敏感审计
台账导出：创建异步任务 -> 冻结查询范围快照 -> 生成 Excel -> 上传私有桶
          -> 24 小时有效期 -> 下载校验归属与时效 -> 写敏感审计
```

一期**不包含**工作流审批链路（人才导出为发起人自助异步导出，不做 WarmFlow 审批）。

导入必须以员工号作为业务唯一键，禁止以姓名去重。批量更新先展示新增、修改、冲突、
忽略数量，不允许上传后立即覆盖。

## 10. 前端适配设计

一期维护一个 `frontend/` 工程（plus-ui 6.0.0），共享登录、Token、菜单、字典、用户状态和组件库。

| 域名 | 默认入口 | 可见菜单 |
|---|---|---|
| `pm.hottter.cn` | RuoYi 管理后台首页 | 集团人才库一级目录及其子菜单（`/talent/profile` 等），外加系统管理与系统监控 |
| `videoai.hottter.cn` | `/video/workbench` | 视频中心及获授权的系统菜单 |
| `ai.hottter.cn` | `/ai/workbench` | 二期知识库、Agent、Skill |

> **集团人才库不设独立域名与独立入口**：它作为 `pm.hottter.cn` 管理后台的一级菜单目录部署，
> 前端路由由 RuoYi 菜单动态下发，不注册独立 Host、不配置独立登录页、不跨域调用 API。
> 前端工程与目录结构见 `docs/talent/`。

后端权限仍是最终授权依据，域名和前端路由不能作为安全边界。用户拥有多个应用权限时，
可在顶部应用切换器切换；没有权限时返回 403 页面而不是隐藏错误。

视频端首期页面：任务工作台、创建任务、任务详情、审核队列、模板管理、运营统计。
人才端首期页面：人才列表、人才详情、技能字典、培训记录、盘点批次、导入中心、导出申请。

## 11. OSS、留存与对象键

对象键不包含姓名、手机号、员工号等明文信息：

```text
video-input/{yyyy}/{MM}/{taskId}/{uuid}.{ext}
video-temp/{yyyy}/{MM}/{taskId}/{attempt}/{uuid}.{ext}
video-output/{yyyy}/{MM}/{taskId}/{uuid}.{ext}
talent-attachment/{profileId}/{uuid}.{ext}
talent-export/{requestId}/{uuid}.xlsx
```

业务表只保存对象键和摘要，不保存永久公网 URL。下载时校验业务权限，再生成短时签名 URL。
生命周期按构建包执行：视频中间文件 14 天、失败文件 30 天、人才导出按审批设置短期保留，
每日清理任务记录删除结果并保留审计事件。

## 12. 部署设计

```text
public_net:    cloudflared <-> nginx
app_net:       nginx <-> ruoyi-admin
data_net:      ruoyi-admin <-> mysql / redis / minio
monitor_net:   prometheus / grafana -> 服务指标
gpu_lan:       ruoyi-admin/worker -> GPU 执行适配器
```

MySQL、Redis、MinIO、Grafana 和 ComfyUI 不映射公网端口。生产配置只放在
`/opt/hotter-ai-platform/shared/secrets/.env`。健康检查至少覆盖应用、数据库、Redis、
MinIO 和 GPU 执行器连通性，但健康接口不得返回凭据或内部地址。

初期 Worker 可随 `ruoyi-admin` 部署，但代码必须以独立 Spring Bean 和队列消费者组织，
当任务量增加时可拆成单独进程。拆分前不得让两个进程同时消费同一任务而没有消费者组。

## 13. CI/CD 与质量门禁

### 13.1 Pull Request CI

- 校验当前基线仍为 Java 21 和 `revision=6.0.0`。
- Maven 编译和必要单元测试。
- 校验 SQL 文件命名、重复版本和危险语句。
- 校验 Docker Compose、Nginx 和 Shell 语法。
- 扫描密钥、Token、私钥、生产 IP 和 `.env`。
- 对业务状态机、数据权限和签名协议运行集成测试。

### 13.2 发布

只允许从 `main` 手工触发带版本号的 Release。构建不可变制品与 SHA256 清单，经生产
环境审批后上传 Ubuntu。部署脚本完成数据库备份、迁移、启动和健康检查；失败自动切回
上一制品。数据库迁移必须提供向前修复方案，不能假设所有结构变更都可自动回滚。

## 14. 首期迭代计划

| 迭代 | 目标 | 主要交付物 | 验收条件 |
|---|---|---|---|
| 0 | 基线与工程化 | 上游源码、前端版本锁定、CI、双数据源、Compose | 后端编译通过，空环境可启动 |
| 1 | 视频最小闭环 | 模板、任务、上传、队列、执行器协议、状态查询 | 文生/图生任务可完成并保存结果 |
| 2 | 视频可试点 | 审核、SSE、重试、归档、统计、权限 | 品牌与销售按角色完成试点 |
| 3 | 人才基础 | 组织映射、档案、技能、培训、导入 | 深圳/汕头只能访问授权数据 |
| 4 | 人才治理 | 盘点、导出审批、审计、脱敏、清理 | 导出全过程可追溯，越权测试通过 |
| 5 | 上线准备 | 监控、备份、压测、安全测试、回滚演练 | 生产检查清单全部通过 |

## 15. 第一批实现任务

1. 锁定并导入与后端 `v6.0.0` 兼容的 `plus-ui` Tag。
2. 新建 `ruoyi-video`、`ruoyi-edge` Maven 空模块并接入启动模块（`ruoyi-talent` 已交付，见 `docs/talent/`）。
3. 配置双数据源和两个最小权限数据库账号，验证视频库切源时的数据权限行为；人才库不切源。
4. 编写视频任务状态机、幂等提交和并发迁移测试。
5. 定义 GPU 执行器 OpenAPI、签名测试向量和模拟执行器。
6. 建立 MinIO Bucket、对象键策略、上传校验和临时下载。
7. 建立视频菜单、角色、字典和首批 SQL 迁移。
8. 实现视频任务最小闭环后，再开始人才档案模块，避免两个高风险领域同时铺开。

## 16. 验收红线

- 任何普通用户不能提交 Workflow JSON、模型路径或本地文件路径。
- 任何视频角色和通用 AI 角色不能查询人才数据。
- 按 ID 查询、更新、删除必须与列表使用同等级数据权限。
- 回调重复、乱序、超时后到达时不能破坏任务状态。
- 文件 URL 不得永久公开，数据库与日志不得记录敏感签名参数。
- 所有生产写入、视频发布和人才导出保留人工确认和审计记录。
- 所有改动有独立 Git 提交，并通过 PR 和 CI 后进入 `main`。

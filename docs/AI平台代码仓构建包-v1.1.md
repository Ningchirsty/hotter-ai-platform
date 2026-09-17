# 集团 AI 数智化平台代码仓构建包 v1.1

> 用途：作为深圳机房私有化 AI 平台的代码仓、部署与首期开发统一基线。  
> 适用对象：架构、后端、前端、AI 视频、运维、品牌与 HR 负责人。  
> 版本基线：RuoYi-Vue-Plus v6.0.0、Java 21、Docker Compose、Cloudflare Tunnel、MySQL、Redis、MinIO、ComfyUI。

---

## 1. 已确认的建设边界

| 项目 | 当前确定方案 |
|---|---|
| 平台运行主机 | 深圳机房独立 Ubuntu 主机 |
| GPU 引擎 | 深圳同局域网 Ubuntu GPU 服务器，2 × A100 80GB |
| 平台底座 | RuoYi-Vue-Plus v6.0.0 |
| 对外访问 | Cloudflare Tunnel，Ubuntu 主机主动出站连接 |
| 登录认证 | 用户直接进入 RuoYi 登录页，使用 RuoYi 账号密码验证 |
| 视频能力 | 文生视频、图生视频，调用现有 ComfyUI API |
| 视频试点用户 | 深圳品牌部、深圳销售部、品牌审核员 |
| 人才库用户 | 深圳 HR、汕头 HR、授权管理人员 |
| 文件存储 | 深圳 Ubuntu 主机 MinIO，后续可迁移至独立存储 |
| 代码管理 | GitHub 私有仓库、分支保护、提交留痕、GitHub Actions |

本地 Windows 工作区只用于代码编写、Git 提交和开发准备，不承载生产平台服务。

---

## 2. 总体架构

```text
深圳品牌、销售、深圳 HR、汕头 HR、后续集团用户
                         │
                         ▼
          pm.hottter.cn（现有 RuoYi 平台）/ videoai.hottter.cn / ai.hottter.cn
                         │
                         ▼
                  Cloudflare Tunnel
                         │
                         ▼
────────────────── 深圳 Ubuntu 平台主机 ──────────────────
Nginx
├─ AI 视频前端
├─ 人才库前端
└─ 后续 AI 应用前端

RuoYi-Vue-Plus v6.0.0
├─ 用户、组织、角色、权限、菜单、日志
├─ AI 视频业务模块
├─ 人才库业务模块
├─ 后续知识库模块
├─ 后续 Agent 模块
└─ 后续 Skill 模块

基础服务
├─ MySQL
├─ Redis
├─ MinIO
├─ 视频任务服务与 Worker
├─ Prometheus
└─ Grafana
                         │
                         │ 同局域网 API、固定地址、服务签名
                         ▼
────────────────── 深圳 GPU 视频引擎服务器 ───────────────
视频执行适配器
→ ComfyUI
→ 文生视频工作流 / 图生视频工作流
→ 本地视频模型 / 本地大模型
→ 2 × A100 80GB
```

### 核心安全边界

- Cloudflare Tunnel 只连接 Ubuntu 平台主机的 Nginx。
- ComfyUI、GPU、MySQL、Redis、MinIO 不开放公网。
- 业务用户不直接访问 ComfyUI，也不能提交原始工作流 JSON。
- GPU 视频引擎只接受 Ubuntu 平台主机的视频任务服务调用。
- 人才库与视频业务必须使用独立 MinIO Bucket 前缀和独立权限策略；
  人才库业务表建在平台库内（复用账号、组织、角色、菜单、数据权限），不新建独立数据库。

---

## 3. 域名、前端与登录

| 域名 | 前端 | 授权对象 | 后端模块 |
|---|---|---|---|
| `videoai.hottter.cn` | AI 视频生产中心 | 深圳品牌、销售、审核员 | `ruoyi-video` |
| `pm.hottter.cn` | 现有 RuoYi 管理后台，人才库是其中的一级业务目录「集团人才库」 | 集团人才库管理员、集团 HR、深圳 HR、汕头 HR、查阅者、审计员 | `ruoyi-talent` |
| `ai.hottter.cn` | 知识库、Agent、Skill 中心 | 后续按应用授权 | `ruoyi-knowledge`、`ruoyi-agent`、`ruoyi-skill` |

> **集团人才库不设独立子域名、不部署独立前端**：它作为 RuoYi 平台的一级菜单目录挂在现有
> `pm.hottter.cn` 之下，页面路径形如 `https://pm.hottter.cn/talent/profile`，路由与按钮权限
> 全部由 RuoYi 菜单管理动态下发（见 `docs/talent/`）。此条为 2026-09 评审确认口径，
> 取代本文档此前 `talent.hotter.cn` + 独立入口的写法。

登录流程：

```text
用户浏览器
→ Cloudflare Tunnel
→ Ubuntu Nginx
→ 前端登录页
→ RuoYi 用户名、密码、验证码校验
→ 返回访问 Token
→ RuoYi 根据角色、部门与数据权限授权
```

Cloudflare 在一期仅承担安全访问通道，不承担用户身份认证。RuoYi 负责账号、密码、Token、菜单、角色和数据权限。

### 登录安全要求

- 强密码与首次登录改密。
- 登录验证码。
- 连续失败锁定，例如连续 5 次失败锁定 30 分钟。
- 登录接口按 IP、账号和设备进行限流。
- 登录、失败登录、导出、审核、模板变更均写入审计日志。
- 平台管理员、技术管理员和 HR 管理员启用二次验证。
- 账号离职、调岗、停用必须纳入 HR 与管理员流程。

---

## 4. Ubuntu 平台主机服务清单

```text
ai-platform/
├─ cloudflared       Cloudflare Tunnel 出站连接
├─ nginx             域名分流、前端托管、API 反向代理
├─ ruoyi-admin       RuoYi 后端
├─ ruoyi-ui-video    视频前端
├─ ruoyi-ui-talent   人才库前端
├─ ruoyi-ui-ai       后续 AI 应用前端
├─ video-worker      视频队列、调度、状态回传、重试
├─ mysql             平台与业务数据
├─ redis             队列、缓存、分布式锁
├─ minio             文件对象存储
├─ prometheus        指标采集
└─ grafana           监控看板
```

推荐采用 Docker Compose。除 Nginx 和 Cloudflared 外，其余服务仅加入 Docker 内部网络，不映射宿主机对外端口。

---

## 5. RuoYi 业务模块

```text
ruoyi-modules/
├─ ruoyi-system       用户、组织、角色、权限、日志
├─ ruoyi-video        视频模板、任务、审核、归档、运营
├─ ruoyi-talent       组织、岗位、人才档案、技能、培训、盘点
├─ ruoyi-knowledge    企业知识库，二期
├─ ruoyi-agent        Agent 编排中心，二期
├─ ruoyi-skill        Skill 注册、权限、版本与审计，二期
└─ ruoyi-edge         视频执行器与边缘节点管理
```

原则：不直接修改上游系统模块；视频、人才、知识、Agent、Skill 以独立业务模块开发，降低上游升级冲突。

---

## 6. AI 视频中心

### 6.1 视频能力

```text
视频模板
├─ 文生视频模板
└─ 图生视频模板

视频任务
├─ 创建任务
├─ 上传素材
├─ 提交队列
├─ 查看生成状态
├─ 成片预览
├─ 品牌审核
└─ 下载、归档
```

### 6.2 任务流程

```text
视频前端
→ RuoYi 视频任务服务
→ Redis 队列
→ 视频 Worker
→ GPU 视频执行适配器
→ ComfyUI 本机 API
→ 本地模型与 A100
→ MinIO 保存成片
→ 品牌审核
→ 下载或归档
```

### 6.3 任务状态

```text
草稿
→ 已提交
→ 排队中
→ 已分配 GPU
→ 生成中
→ 结果转存中
→ 待审核
→ 审核通过 / 审核退回
→ 已归档
```

异常状态：参数校验失败、执行失败、执行超时、文件转存失败、人工取消。

### 6.4 GPU 调度规则

```text
GPU 0：最多 1 个长视频任务
GPU 1：最多 1 个长视频任务
其他任务：进入 Redis 队列
```

每个视频模板必须统计平均耗时、P95 耗时、显存占用、成功率、审核通过率、重试次数和输出文件大小。试点完成后依据真实数据调整并发。

### 6.5 GPU 执行器接口约束

请求必须包括：

```text
任务编号、幂等键、模板编号、工作流版本、模型版本、
输入文件、业务参数、优先级、服务签名、时间戳、有效期
```

GPU 执行器拒绝：未签名请求、过期请求、重复请求、非白名单模板、任意 Workflow JSON、任意模型路径和任意本地文件路径。

---

## 7. 集团人才库

### 7.1 一期范围

```text
组织与岗位
├─ 公司、部门、岗位、汇报关系

人才档案
├─ 基础信息
├─ 技能标签
├─ 培训与认证
├─ 项目经历
└─ 人才盘点标签
```

首期不纳入薪酬、身份证件、健康信息等高敏感个人信息。

### 7.2 两地 HR 权限

| 角色 | 默认数据范围 |
|---|---|
| 深圳 HR | 深圳人员维护，授权的集团统计 |
| 汕头 HR | 汕头人员维护，授权的集团统计 |
| 集团 HR 管理员 | 按职责授权的跨地区人才数据 |
| 平台管理员 | 账号与权限，不直接修改人才业务数据 |

深圳与汕头 HR 共同负责人才字段标准、权限调整、导出审批和数据质量复核。

---

## 8. 数据与文件隔离

### MySQL

```text
ruoyi_platform  用户、组织、角色、菜单、日志、通用配置
                + 集团人才库业务表（tl_talent / tl_talent_attachment / tl_talent_contact /
                  tl_talent_duplicate / tl_talent_access_grant / tl_parse_task /
                  tl_parse_field / tl_export_task / tl_sensitive_audit）
ruoyi_video     视频模板、任务、审核、执行日志
```

> 人才库**不新建独立数据库**：它与平台共用 `ruoyi_platform`，以 `tl_` 前缀区分业务表，
> 从而直接复用账号、组织、角色、菜单与数据权限。详见 `script/sql/ry_talent.sql`
> 与 `docs/talent/`。

### MinIO

```text
video-input
video-temp
video-output
video-template
talent-attachment
talent-export
platform-backup
```

人才库使用独立 Bucket（或 `talent-private/` 对象键前缀）、独立访问策略和导出审计，
所有人才附件均以私有对象存储，下载只经人才库受控接口，不下发预签名 URL。
视频模块、品牌用户、销售用户及通用 Agent 默认无权访问人才数据。

建议留存策略：

| 内容 | 建议留存 |
|---|---:|
| 原始视频输入素材 | 180 天 |
| 视频中间文件 | 14 天 |
| 失败任务文件 | 30 天 |
| 审核退回视频 | 90 天 |
| 审核通过成片 | 12 个月 |
| 模板、Prompt、工作流 | 长期版本化保留 |
| 数据库备份 | 每日备份，至少保留 30 天 |

---

## 9. 后续知识库、Agent 与 Skill

```text
AI 应用
→ Agent 编排
→ Skill 执行
→ 知识库 / 视频引擎 / 受控业务 API
```

Skill 必须登记：业务负责人、输入、输出、数据权限、工具权限、审批节点、版本、测试样例、风险等级、成本和审计规则。

禁止 Agent 直接拥有数据库、文件系统、人才库或业务系统的无限权限。所有写入业务系统、对外发送内容、对外发布视频和导出人才信息的操作必须保留人工确认。

---

## 10. 代码仓结构与协作规范

```text
hotter-ai-platform/
├─ apps/                  RuoYi 二次开发模块与前端应用
├─ deploy/ubuntu/         Ubuntu 初始化、部署、回滚脚本
├─ docs/                  架构、接口、运维、实施文档
├─ infra/                 Nginx、MySQL、Cloudflare 等配置
├─ scripts/               构建、校验、辅助脚本
├─ .github/workflows/     GitHub Actions
└─ compose.yaml           Docker Compose 服务编排
```

分支规范：

| 分支 | 用途 |
|---|---|
| `main` | 已验证生产基线，禁止直接推送 |
| `develop` | 集成功能 |
| `feature/*` | 独立功能开发 |
| `hotfix/*` | 紧急修复 |

每次改动必须经过：创建分支、提交代码、触发 CI、审查、合并、版本化发布。所有新增代码注释使用中文。

---

## 11. CI/CD 与 Ubuntu 快速安装

```text
开发提交
→ GitHub Pull Request
→ GitHub Actions 校验与编译
→ 合并 main
→ 手工触发 Release 并指定版本号
→ 生产环境审批
→ 上传部署制品至深圳 Ubuntu
→ Docker Compose 启动、健康检查
→ 失败时回滚至上一版本
```

### GitHub Actions

- `ci.yml`：校验 Shell 脚本和部署文件，使用 Java 21 编译 RuoYi-Vue-Plus `v6.0.0` 基线。
- `release.yml`：打包部署制品，只有 `ENABLE_UBUNTU_DEPLOY=true` 时才允许触发 Ubuntu 发布。

### GitHub Secrets

```text
UBUNTU_HOST
UBUNTU_USER
UBUNTU_SSH_KEY
```

### Ubuntu 私有配置

```text
/opt/hotter-ai-platform/shared/secrets/.env
```

私有配置可保存数据库密码、Cloudflare Tunnel Token、MinIO 密钥和视频执行器服务令牌，但严禁提交到 GitHub。

### 首次初始化

```bash
sudo bash deploy/ubuntu/bootstrap.sh
```

### 手工回滚

```bash
sudo bash /opt/hotter-ai-platform/current/deploy/ubuntu/rollback.sh <版本号>
```

---

## 12. 组员分工建议

| 角色 | 首期职责 |
|---|---|
| 架构/后端 | RuoYi 模块、权限、视频任务 API、执行器契约 |
| 前端 | 视频中心、人才库、统一登录、任务与审核页面 |
| AI 视频工程师 | ComfyUI 模板、视频执行适配器、GPU 队列与指标 |
| 运维 | Ubuntu、Docker、Cloudflare Tunnel、备份、监控、发布 |
| HR 负责人 | 人才字段、数据标准、权限、导出审批、数据质量 |
| 品牌负责人 | 视频模板、审核规范、试点验收 |

---

## 13. 首个迭代顺序

1. 创建 GitHub 私有仓库并推送当前 `main`。
2. 配置分支保护、GitHub Actions、生产环境审批和 GitHub Secrets。
3. 在深圳 Ubuntu 部署 Docker、目录、私有 `.env`、备份与 Cloudflare Tunnel。
4. 引入并审查 RuoYi-Vue-Plus v6.0.0 上游源码。
5. 开发视频模板、视频任务、视频审核、视频前端和 GPU 执行适配器。
6. 深圳品牌部与销售部试点文生视频、图生视频。
7. 建设人才库组织、岗位、档案、技能、培训与深圳/汕头权限。
8. 视频与人才库稳定后，建设产品知识库、Agent 和 Skill 中心。

---

## 14. 禁止事项

- 不提交密码、Token、证书、私钥、生产 IP、用户数据、素材和人才档案。
- 不将 ComfyUI、MySQL、Redis、MinIO 暴露至公网。
- 不允许普通用户直接调用 GPU 或 ComfyUI。
- 不允许视频、营销、通用 Agent 默认读取人才库。
- 不允许未经人工审批的 Agent 自动发送邮件、发布内容、导出人才数据或修改业务系统。

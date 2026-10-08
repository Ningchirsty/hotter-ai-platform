# 0-2 ADR-001~008 决定记录

> 状态：**完成**（2026-10-08；ADR-008 于同日追加）
> 每条 ADR 给：**决定** / **依据** / **落地为可执行契约的方式** / **被否决的替代方案**。
> "已核实"项均指读了源码或查了生产。

---

## ADR-001 控制面与执行面分离 —— **接受**

| 项 | 内容 |
|---|---|
| 决定 | RuoYi 单体保留为**控制面**（注册/Package/版本/授权/场景/任务元数据/审批/审计）；重负载与不可信执行隔离在**独立进程与容器**（Worker、沙箱、HTTP Agent、ComfyUI） |
| 依据 | 已核实：生产是单后端容器 + 独立 GPU 侧 ComfyUI + 独立 snail-ai 容器；平台已有 `aig_task.execution_mode`（PLATFORM/EXTERNAL）表达"谁在执行" |
| 落地方式 | 控制面**不新增同步长调用**；对外只暴露"提交即返回"的异步入口（见 Execution Contract §4）。执行面通过**服务身份**回调控制面（见 ADR-002 落地项） |
| 否决的替代 | 全面微服务化（否决：当前规模不划算，且会打断既有发布/审计链路） |

**必须同时接受的限制**（写进 ADR，避免以后被当成"能力"）：控制面单点；`sweep()` 是定时轮询而非推送，事件可见性下限 = sweep 周期。

---

## ADR-002 内部 `Execution Contract v1` + Java Runtime SPI —— **接受（并追加一项前置）**

| 项 | 内容 |
|---|---|
| 决定 | 定义与框架无关的 `Execution Contract v1`（JSON Schema）+ `AigRuntimeExecutor` SPI；不绑定任何单一 Agent 框架 |
| 依据 | 平台已有同类成功先例：`IAigEvaluationSubject`（评测执行器 SPI，业务侧实现、治理层只定义契约）与 `IAigTaskExecutor` |
| 落地方式 | 契约以**可执行 Schema** 入库（见 `contract/`）；SPI 在阶段 1 落地，**不在阶段 0 写死代码** |
| **追加前置（本 ADR 的关键修改）** | **必须先补"服务身份/机器令牌"**。已核实：现有 `/aigov/invoke/{capabilityCode}` 只认 Sa-Token **会话**，没有机器身份。Python/外部 Agent 若不解决身份，就只能拿到一个"人"的会话（不可接受）或绕过治理（更不可接受） |
| 否决的替代 | 让外部应用携带管理员会话 token（否决：违反最小权限且不可审计） |

---

## ADR-003 Skill 兼容 Agent Skills 规范；MCP 承载工具；A2A 用于独立 Agent 间通信 —— **接受，但 A2A 后移**

| 项 | 内容 |
|---|---|
| 决定 | 三个协议各司其职、不混用：`SKILL.md` = 知识与步骤；MCP = 工具；A2A = 独立 Agent 间通信 |
| 依据 | 设计文档 §8/§9/§7.3 的分工清晰；平台现有 `sai_agent_skill`/`sai_skill` 属 snail-ai 自有体系，**不作为治理层 Skill 事实源** |
| **修改** | **A2A 降到阶段 5 之后**（设计文档已放在 P3，此处进一步明确：A2A 之前必须先把 HTTP Agent + Skill + Tool 三条走通） |
| 落地方式 | Skill 解析器按设计文档 §8.1 的 `mode`（INSTRUCTION / TOOL_GUIDE / EXECUTABLE）分三步上，**EXECUTABLE 最后**，且**依赖扫描器先就位**（见 ADR-004 与基线 3.2） |
| 否决的替代 | 先做 A2A（否决：平台尚无任何外部 Agent 接入经验，A2A 的协商语义在没有真实对端时无法验证） |

---

## ADR-004 Manifest V2 扩展命名空间，V1 完全兼容 —— **接受（这条最关键，附实施红线）**

| 项 | 内容 |
|---|---|
| 决定 | V1 Manifest 与状态机/审计账本**完全不动**；V2 新增 `manifest_schema_version` 与 `x_*` 扩展，走**新接口 + 新表** |
| 依据 | 已核实：V1 校验器是**严格白名单**——未声明字段**一律拒绝**（这是刻意的安全设计，不是缺陷）。所以 `x_*` **不可能**通过现有入口 |
| **实施红线** | **不得为了兼容 V2 去放宽 V1 白名单**。V1 的"拒绝未知字段"是安全控制；放宽它等于让所有既有包都能夹带任意字段 |
| 落地方式 | `POST /aigov/v2/packages/imports` 接收 → 解析 → 预检 → 再调用既有 Package API 或内部事务完成元数据登记（设计文档 §5.3 的方案正确） |
| 关联前置 | V2 首次真正解压 ZIP / 落脚本，因此 **V2 内容扫描器是 ADR-004 的硬前置**（基线 3.2 已述：现有零实现） |

---

## ADR-005 业务 Workflow 与第三方 Agent 内部 Workflow 分层 —— **接受**

| 项 | 内容 |
|---|---|
| 决定 | **外层**业务 Workflow（节点/责任人/审批/返工/预算）由平台管；**内层** Agent Workflow（VibePoster 内部 Planner/Visual/Layout/Critic、ComfyUI 工作流）由原框架自管，平台只维护"外部任务边界 + 可观测事件" |
| 依据 | 已核实：平台正是用 `aig_task.execution_mode=EXTERNAL` 表达"这个任务由业务域执行、平台只登记与展示、不执行也不扫描"——**这条边界已经有了** |
| 落地方式 | 外层编排落 `aig_task_step` / `aig_task_checkpoint`（见基线 §四）；内层只接收外部 job 状态 |
| 否决的替代 | 把 ComfyUI 工作流翻译进平台 DSL（否决：等于重写 ComfyUI，且会在协议膨胀后失控） |

---

## ADR-006 所有模型/工具外发**在网关执行点强制策略校验** —— **接受，且必须落成代码（本 ADR 不接受"文档承诺"）**

| 项 | 内容 |
|---|---|
| 决定 | 不把 `allow_external` / `networkAccess` 这类**声明字段**当作管控；外发管控必须在**执行点**（模型网关 / 工具网关 / 出站网络层）强制 |
| 依据 | 平台历史教训真实存在：`aig_route_policy.require_approval` 曾是"装饰开关"（页面显示、调用照跑），直到 C3③ 才做实；同理 Agent 版本的 `provider_capability` 与 `allow_external` **今天仍不参与路由**（已核实） |
| 落地方式 | ① 每次敏感调用落 `aig_policy_decision_log`（可举证）；② 业务侧扩展开关"审计中 `agentVersionId` 缺失"告警；③ **网络层出站白名单**（这是唯一能拦住"外部 Agent 自己外发模型"的手段） |
| **残余风险（必须书面接受）** | 不可改的第三方 HTTP Agent 可能**自行**调用别处模型，平台**无法凭其元数据证明其内部调用路径**。因此：标 `OPAQUE`、按外部服务外发策略+许可证+费用约束单独审批，**且不得对外声称"统一模型治理已覆盖"** |
| 否决的替代 | 仅靠 Manifest 声明 `network_access: NONE`（否决：声明不是管控） |

---

## ADR-007 第一优先用 VibePoster(HTTP) / 标准 Skill(文件) / ComfyUI(异步) 三类验收 —— **接受，但范围收窄为"先只做 VibePoster"**

| 项 | 内容 |
|---|---|
| 决定 | 用三类不同形态做验收样例，避免平台被单一视觉场景绑死：HTTP Agent（跨语言）、Skill（文件与解析）、异步 GPU 任务（长任务与恢复） |
| **修改** | **阶段 1 只做 VibePoster 一条**。三条并行会把阶段 1 拉长，且 Skill/异步两条各自还有硬前置（Skill 依赖扫描器；异步依赖 checkpoint + 外部 job 语义） |
| 依据 | 设计文档 §19.5 自述"18 周是排布示意、不是交付保证"；§12.1 自述"整页自动排版/一致性质检不是 VibePoster 开箱能力"——**试点 A 本身已含不确定工作量** |
| 落地方式 | 阶段 1 验收 = VibePoster 单场景端到端（含 Artifact 落库 + 沙箱证据入门槛）；Skill 与 ComfyUI 顺延 |
| 否决的替代 | 三条并行（否决：人力假设不成立；文档也承认 1~2 人时只做 P0） |

---

## ADR-008 图像能力门禁的权威层：**治理层管路由，`CloudImageValidation` 只管验收** —— **接受**

> 提出时间：2026-10-08（由 B-2 阻塞项触发）。**背景是一次实测推翻了我方此前的错误结论**，
> 因此本 ADR 的价值一半在"决定"，一半在"记录为什么原来判断错了"。

### 背景：图像能力其实有**四处判据**，不是两处

| 层 | 位置 | 实际作用 |
|---|---|---|
| ① 前端硬编码清单 | `frontend/src/components/CreativeInspiration/cloud-image-capabilities.ts:19-23` | 决定 UI **列出**哪些能力（纯展示，提交仍由 ② 把关） |
| ② 云端逐能力验收快照 | `ruoyi-ai/.../CloudImageValidation.java:10-17` | **真正的云端能力开关**：`verified()` = `status=="PASSED"`；`CloudImageRequest.requireVerified()` 在提交时抛 `CLOUD_CAPABILITY_UNVERIFIED` |
| ③ 云端输出参数验收档 | `ruoyi-ai/.../cloud-image-output-validation.json` | **不是能力开关**：仅在用户传了 custom 输出参数时，校验该「型号 × 能力 × 参数」组合是否验证过 |
| ④ 治理层路由 | `aig_model_governance.lifecycle_status` | 决定 **aigov** 能否路由到它 |

**关键事实（已核验）**：① ② ③ 所在的云端图像链路**完全不经过 aigov**——
`ruoyi-modules/ruoyi-ai` 的 `src/main/java` 里 **0 处** `aigov` 引用、
`ImageCloudService` **0 处** audit 引用、`ruoyi-ai/pom.xml` **不依赖 aigov**。
它是直连 BluOcto 的独立子系统。

**另一个必须同时看的门禁**：④ 之外还有一层 **服务端 Key 的模型授权**
（`GET /image/cloud/check` → `authorizedModels`）。这不是配置项，而是**问上游网关当前 Key 能用哪些模型**。
实测（`b2-authorized-models.out`）：

```
authorizedModels = [qwen-image-3.0-pro, wan2.7-image, gpt-image-2.5-flare,
                    wan2.7-image-pro, gpt-image-2.5-sunburst, qwen-image-3.0]
generationVerified = True
（7 个登记模型中只有 flux-2-pro 未授权）
```

⇒ 所以"云端链路能用什么"= **② 验收状态 ∩ Key 授权**。flare 两项都满足。

### 原判断错在哪（留档，避免重犯）

原结论是「`cloud-image-output-validation.json` 已把 flare 5 项能力标为开放，但治理层仍
`SUSPENDED`，所以 flare 被路由排除、**"开放了能力"是空的**」。

**错在两处**：

1. 把 ③（输出参数验收档）当成了能力开关——真正的开关是 ② 的硬编码 `PASSED` 表；
2. 把两条**互不依赖**的路径当成了同一个门禁——云端图像链路里 flare **实测 6/6 `PASSED`**
   **且在 `authorizedModels` 里**，**真实可用**
   （`blockers-cloud-gate.out` GET `/image/cloud/models`；`b2-authorized-models.out` GET `/image/cloud/check`）。

**正确表述**：不是"开放是空的"，而是**同一模型在两处状态相反，且没有单一事实源**——
任何一处改了都不同步，且无人收到提示。

### 决定

| 项 | 内容 |
|---|---|
| **决定** | **治理层 `aig_model_governance.lifecycle_status` 是"能否被路由"的唯一权威**（它管路由、审计、数据等级）；`CloudImageValidation` 是**参数/输出验收快照**，只回答"这个型号×能力×参数组合有没有被真实验证过"，**不是路由开关** |
| **依据** | 两条链路各有明确职责且已验证独立（见上）；治理层是唯一同时参与路由、审计与数据等级判定的层，因此由它定"可用性"不会产生第二个事实源 |
| **落地方式** | ① 两者**不一致时必须告警**（不得各自静默生效）——建议纳入 M-006 类巡检；② `CloudImageValidation` 的验收状态**迁出硬编码、改为可运维配置**（见下"迁移项"）；③ 文档与 UI 须显式说明"云端图像链路独立于治理路由" |
| **否决的替代** | (a) 让 `CloudImageValidation` 也参与路由（否决：会产生两个可用性权威，正是本次问题的成因）；(b) 把云端图像链路并入 aigov 治理（否决：会打断既有直连链路与审计边界，且当前无此需求） |

### 迁移项（本 ADR 要求实施，属代码变更）

`CloudImageValidation` 把验收状态**硬编码在 Java 源码**里：

```java
public static final String TESTED_AT = "2026-10-08";
private static final Map<String, Map<String, String>> LATEST = Map.of( ... );
```

问题：**改一次验收结论就要改代码、走一次发版**；且这是一份**会过期的快照**，
没有任何机制提醒它过期。

**要求**：迁到配置/数据库（保留"空配置时回落到内置默认值"的兜底，避免表未初始化就整体失效）。
**不要求**与治理层同源——它们回答的是不同问题。

### flare 状态定调（本次一并决定）

| 项 | 内容 |
|---|---|
| 决定 | flare 治理层 `lifecycle_status`：`SUSPENDED` → **`GRAY`** |
| 依据 | 云端链路实测其 6 项能力全部 `PASSED`；且其它已验证图像提供方（`qwen-image-3.0`、`wan2.7-image` 等）**都是 `GRAY`** |
| 为什么不是 `PRODUCTION` | 那会让 flare 成为唯一 `PRODUCTION` 的图像模型，暗示它是主路径——**没人这么决定过** |
| **范围限定** | **只改生命周期，不加绑定**。`aig_capability_model` 里 flare 仍 0 行 ⇒ 它成为"可用候选"但**不是 `image_generation` 的候选**。**路由结果不因本次变更而改变**（已实测确认候选集未变） |
| 附带影响 | 仍受 `data_level_max=INTERNAL` 限制 ⇒ `RESTRICTED` 一律排除；`allowExternal='N'` 时也排除 |

---

## ADR-009 已启用的定时任务「谁来触发」：**进程内 `@EnableScheduling`，不用外部 cron** —— **接受**

> 提出时间：2026-10-08（由"R65 三个定时任务在生产都不会跑"触发）。
> **背景同样含一次自我纠正**：我最初给出的第 2 步（外部 cron 调 HTTP 入口）经实测**根本走不通**。

### 背景：`@Scheduled` 只是声明，没人注册后处理器就等于没写

本仓 `@EnableScheduling` 此前**只**出现在 `ruoyi-common-job` 的 `SnailJobConfig`，
且被 `snail-job.enabled` 门控（生产 false，**且没有部署 SnailJob server**，17888 不可达）。
结果：三个 `@Scheduled` 任务在生产**一个都不会跑，且不会有任何报错**——只是"什么都没发生"。

### 关键实测（决定了 ADR 的取舍）

| 事实 | 证据 |
|---|---|
| 全仓只有 **3 个** `@Scheduled`，**全部**在 `ruoyi-ai-gov` | 全仓 grep `@Scheduled` |
| 其中 2 个被各自的 `@ConditionalOnProperty` 门控且生产未设置 ⇒ **打开调度只会激活"模型健康探测"1 个任务** | `AigTaskSchedulerJob`(`aigov.task.scheduler.enabled`)、`AigCallApprovalExpireJob`(`aigov.approval.expire-scan-enabled`) 在 `application-prod.yml` 中**均未出现** |
| 同步探测入口实测 **133019 ms**（8 个模型逐个真外呼），客户端却在 120 s 超时 ⇒ **网关掐断不会让后端停下来** | 第一次实跑：客户端超时，服务端日志正常跑完并写回 7 个模型 |
| 三个触发入口**都**有 `@SaCheckPermission` | `AigModelController:171`、`AigTaskController:191`、`AigCallApprovalController:131` |
| 机器登录的唯一通道要过**图形验证码**，且**没有** per-client 例外 | `captcha.enable: true`（`application.yml:20`，生产未覆盖）；`PasswordAuthStrategy:64-68` 无条件校验；`ruoyi-common` 无内部调用旁路、无 `@SaIgnore` |

⇒ **第 2 步（主机 cron 调 HTTP 入口）被验证码挡住**：cron 拿不到令牌。
可用的绕过手段只有"人工过一次验证码把 token 存盘"或"root cron 自己往 Redis 写验证码答案"，
两者都等于把一个人工/管理员凭据放进定时任务。**这不是缺个配置，而是缺一个机读凭据**——
正是 **ADR-002 已列为前置的「服务身份/机器令牌」**。

### 决定

| 项 | 内容 |
|---|---|
| **决定** | 触发机制用**进程内调度**：新增 `AigSchedulingConfig`（`@EnableScheduling`，被 `aigov.scheduling.enabled` 门控），生产置 `true`。HTTP 入口**保留**，但定位改为"按需立即探测一次" |
| **依据** | ① 打开开关实际只激活 1 个任务（上表已逐个核对），爆炸半径可枚举；② 不需要任何凭据、不碰验证码、不新增攻击面；③ 回滚是"改配置回 false 重新发布"，不必改代码 |
| **为什么用一个门控开关而不是把注解加在启动类上** | 加在启动类上等于"永远开启且无法关闭"，而本仓有两个**从未在生产跑过**的写操作任务；用配置门控至少让"是否启用调度"是一个显式、可 grep 的决定 |
| **否决的替代** | (a) **外部 cron + HTTP（原第 2 步）**：否决，见上（缺机读凭据）；待 ADR-002 的机器令牌落地后可重新评估；(b) **`snail-job.enabled=true`**：否决，生产没有 SnailJob server，打开只会在启动时连不上 17888；(c) 保持现状（只留 HTTP 入口）：否决，等于"允许但没人触发"，`health-check.sh` 会永久 WARN |
| **必须同时接受的代价** | ①`@EnableScheduling` 注册的后处理器**作用于整个容器**，将来任何人新增 `@Scheduled` 都会随本开关一起上线；②探测单轮约 133 秒会占用调度线程池的 1 个线程——但本仓的池**不是** Spring Boot 默认的 1 个线程（见下"一处自我纠正"） |

### 后果（已实施）

1. 第 1 步：`aigov.model.health-probe.enabled=true`（只决定"允许探测"）；
2. 第 2 步：**未采用**（原因见上）；
3. 第 3 步：`aigov.scheduling.enabled=true`（真正的触发机制）；
4. 入口的同步等待改为"提交即返回 + 在途去重"，因为 133 秒必然被 Cloudflare（约 100 秒即 524）掐断。

### 一处自我纠正：调度线程池不是"1 个线程"

我在本 ADR 的初稿里写了"默认调度线程池只有 1 个线程，探测会占用它，将来启用另外两个任务时
必须调大 `spring.task.scheduling.pool.size`"。**这句是错的**，已在实施后由运行证据推翻：

- `ruoyi-common-core` 的 `ThreadPoolConfig` 定义了全局 `ScheduledExecutorService` bean
  （`ScheduledThreadPoolExecutor`，核心线程数 = `availableProcessors() + 1`，线程名 `schedule-pool-%d`）；
- Spring 的 `ScheduledAnnotationBeanPostProcessor` 在没有 `TaskScheduler` bean 时会采用
  **该 `ScheduledExecutorService` bean**，所以 `@Scheduled` 跑在这个池上，**不是** Boot 默认的
  单线程调度器；
- 实测证据：生产启动后第一轮探测的日志线程名是 **`schedule-pool-1`**
  （`2026-10-08 12:34:52 [schedule-pool-1] INFO o.d.aigov.job.AigModelHealthProbeJob`），
  而该命名只来自 `ThreadPoolConfig`。
- 生产主机 `nproc = 8`、容器无 CPU 限额 ⇒ 该池 **9 个线程**。
- 另外 `spring.task.scheduling.pool.size` 在本仓**根本不起作用**（它配的是 Boot 自动配置的
  `ThreadPoolTaskScheduler`，而这里没有 `TaskScheduler` bean 参与）。

⇒ 正确表述：探测占用 CPU+1 池中的 1 个线程，另两个任务**不会**因为它而排不上队。
**教训与本次主题一致：注解"在不在"、配置"写没写"都不算数，要看运行时到底用了哪个池。**

---

## 决定汇总

| ADR | 决定 | 关键修改/前置 |
|---|---|---|
| 001 | 接受 | 书面接受"控制面单点 + sweep 轮询"两条限制 |
| 002 | 接受 | **追加前置：服务身份/机器令牌** |
| 003 | 接受 | **A2A 后移到阶段 5 之后**；EXECUTABLE Skill 依赖扫描器 |
| 004 | 接受 | **红线：不放宽 V1 白名单**；扫描器是硬前置 |
| 005 | 接受 | 复用 `execution_mode=EXTERNAL` 这条既有边界 |
| 006 | 接受 | **必须落成代码**；书面接受 `OPAQUE` 残余风险 |
| 007 | 接受 | **阶段 1 只做 VibePoster 一条** |
| 008 | 接受（2026-10-08 追加） | **治理层是路由唯一权威**；`CloudImageValidation` 验收状态**迁出硬编码**；flare → `GRAY`（只改生命周期、不加绑定） |
| 009 | 接受（2026-10-08 追加） | 定时任务的触发用**进程内 `@EnableScheduling`**（`aigov.scheduling.enabled`）；**外部 cron 方案否决**（缺机读凭据，见 ADR-002 前置）；探测入口改为"提交即返回 + 去重" |

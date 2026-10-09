# 0-2 ADR-001~008 决定记录

> 状态：**完成**（2026-10-08；ADR-008/009 于同日追加；**ADR-010~014 于 2026-10-09 追加**）
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
| **为什么用一个门控开关而不是把注解加在启动类上** | 加在启动类上等于"永远开启且无法关闭"，而本仓有两个**从未在生产跑过**的写操作任务；用配置门控至少让"是否启用调度"是一个显式、可 grep 的决定。（**2026-10-09 补注**：这两个任务已在生产启用，走的就是本 ADR 选定的进程内调度——见 `application-prod.yml` 的 `aigov.task.scheduler` / `aigov.approval`，启用当天用生产夹具做过正反例对照） |
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

## ADR-010 制品账本落成 `aig_task_artifact`（而不是新开 `aig_artifact*` 表组）—— **接受**（2026-10-09 追加）

| 项 | 内容 |
|---|---|
| **决定** | 制品的**登记事实**收进 `aig_task_artifact` 一张表：平台铸造 `artifact_id`（事件与 `execution-result.outputs[].artifactId` 引用它）、`storage_ref` 指向对象存储键、`sha256/size_bytes/mime_type` 记摘要；被拒登记也留一行（`validation_status=FAIL`） |
| **依据** | 已核实：契约的 `outputs[]` 与事件 `AI_TASK_ARTIFACT_ADDED` 都要求一个**稳定的制品引用**，而字节在对象存储里、位置会随存储策略变——引用与位置必须分开；设计 §9 的口径「业务表只存资产 ID / 对象引用，不存服务器本地路径」 |
| **落地方式** | `script/sql/aig_task_artifact.sql` + `AigTaskArtifact` / `AigArtifactValidator` / `IAigTaskArtifactService` + `POST /aigov/task/artifact`、`GET /aigov/task/artifact/list`；事件写入方经 `IAigTaskService#recordEvent`（序号与操作者的唯一实现） |
| **否决的替代（a）：只回错误码、不落被拒行** | 否决。生产方多半是外部系统（VibePoster 一类），事后我方只剩对方的日志可查——「对方到底交了什么、为什么没收」我方无法回答 |
| **否决的替代（b）：拿对象键当制品主键** | 否决。引用要跨表、跨事件、跨版本稳定，而对象键会随存储前缀/分桶策略变化；同内容被两个任务引用时也表达不了 |
| **否决的替代（c）：给 `(task_id, storage_ref)` 建唯一键来做幂等/挡重复** | 否决。同一份对象被重推是常态，而**每一次被拒都是独立的时间事实**，唯一键会把第二次被拒变成插入失败——证据反而丢了。PASS 行的幂等由服务层按 `(task_id, storage_ref, sha256)` 先查后返回 |
| **否决的替代（d）：新开 `aig_artifact*` 表组 + 独立权限点** | 否决（当前阶段）。现在只有「某任务产出了某制品」这一种事实；过早拆表会多一层关系，而新权限点还得有对应菜单行才能种子（`AigPermissionSeedCoverageTest` 会拦住没有菜单行的权限码）。等出现「制品版本/血缘」这类独立事实时再拆 |
| **必须同时接受的限制** | ①平台**不**回读对象存储重算哈希，故 v1 的 `sha256` 是**生产方声明值**，`hash_verified` 一律 `N`——账本与接口都必须把这个值显式带出去，避免「有哈希」被读成「验过」；②生产库**尚未**执行该 DDL（需单独授权），且**还没有任何生产方在调它**，故生产上这张表会是空的；③本账本只做**形态校验**（MIME 清单/大小/哈希形态/对象引用），不做内容对错的判断——那是质检（`qa_verdict`）那条线的事 |

### 为什么必需的那一列是 `hash_verified` 而不是「再校验一次」

「有 sha256」与「这个 sha256 被核对过」是两件事。把两者混同的失效方式是安静的：
内容被替换后，账本里的哈希照旧、下游按哈希判「同一份」——而实际上不是同一份。
平台现阶段拿不到制品字节（生产方自己写对象存储），所以**不能**假装验过；
记一个 `N` 并把它显示出来，比「什么都不说」诚实，也比「引入一个需要对象存储可读的强校验、
再在拿不到字节时静默跳过」可靠。

---

## ADR-011 策略决策要落到任务上：两套结论词表 + 细因「认不出就留空」—— **接受**（2026-10-09 追加）

| 项 | 内容 |
|---|---|
| **决定** | ① 任务级策略结论 `aig_task.policy_result` 用 **PASS/REJECT/MANUAL**（DDL 口径），路由结论保持 **MODEL/MANUAL/DENIED**（引擎口径），映射唯一实现为 `AigTaskPolicyResultEnum#fromDecision`；② 路由细因 `reasonCode` 用契约 `reasonCodes.recommended` 的镜像枚举，**只在无歧义时写入，认不出留空**；③ 执行任务时把策略结论与细因写进两列 + 事件 `AI_TASK_POLICY_DECIDED`（同一次写入），并把 `taskId` 带进调用入参以填 `aig_policy_decision_log.task_id` |
| **依据** | 已核实：`policy_result`/`policy_reason` 与 `aig_policy_decision_log.task_id`/`reason_code` **四列全表无人写入**，而治理台任务详情的「策略结论」正在渲染前两列 ⇒ 页面那一格永远是 `-`，与「错误码/失败原因两列曾长期为空」是同一类缺陷；同时事件 `AI_TASK_POLICY_DECIDED` 有词表、无写入方 |
| **落地方式** | `AigRouteDecision.reasonCode` + 路由引擎两处无歧义写入；`AigTaskPolicyResultEnum`；`AigPolicyReasonCodeEnum`（契约镜像，`AigContractEnumDriftTest` 双向钉住）；`IAigTaskService#recordPolicyDecision`（列 + 事件，乐观锁）；执行器在调用后写入 |
| **否决的替代（a）：把错误码与细因合成一个字段** | 否决。错误码决定「怎么处置」（重试/换候选/转人工/熔断），细因说明「具体卡在哪」；契约明写「不要把细因做成错误码」。合并的后果是 `NO_ROUTE_POLICY` 这类一次性配置问题也要有重试/熔断语义 |
| **否决的替代（b）：把路由词表直接写进 `policy_result`** | 否决。任务列是给业务读者看的（页面直接显示），`MODEL` 这种词对业务无意义；且 DDL 已定 PASS/REJECT/MANUAL 三值 |
| **否决的替代（c）：候选全被排除时也写一个细因** | 否决。那种拒绝的原因往往是**多个排除条件的合成**（有的被外发禁令排除、有的被健康状态排除），挑一个写进去会让读的人以为那就是全部原因——而「猜错的细因」比「没有细因」更坏：它把排障引向错误方向，且看不出是猜的。首批只覆盖「未配置策略」「未绑定模型」两个无歧义结论，具体原因仍在 `policyHits`/`excluded_json` 里逐条可查 |
| **必须同时接受的限制** | ①细因覆盖率目前很低（生产里已在用的能力都配了策略、都绑了模型），它的价值主要在**将来撞到配置缺失时**能立刻被统计出来；②策略结论写在**执行时**而不是建任务时——建任务阶段没有路由结论，那时写只能靠猜；③`aig_policy_decision_log.reason_code` 只在原因无歧义时有值 |

---

## ADR-012 暴露 Provider 回调 HTTP 入口：签名走请求头、真实状态码、必须限流—— **接受**（2026-10-09 追加）

| 项 | 内容 |
|---|---|
| **决定** | 新增 `POST /aigov/task/callback`（契约 `contract/provider-callback.schema.json`）：Provider 编码与 HMAC 签名走**请求头**、签名覆盖**原始请求体字节**；按处理结论返回**真实 HTTP 状态码**；**IP 限流 120/分钟**；缺 Provider 头或坏 JSON **不进服务层、不写账本**；回调声明的错误码由**平台**归类；携带的进度 best-effort 记录 |
| **依据** | 已核实：验签、幂等账本、定位任务、状态机推进、错误归类都已实现并有测试，但 `handleCallback` **只有服务与接口两处调用者——没有 HTTP 入口**，任何走 HTTP 的执行面都无法回报结果（能力在代码里存在、在现实里不存在）。同时实测：XSS 过滤器注册在 `/*`，对 `application/json` 执行 `HtmlUtil.cleanHtmlTag(json).trim()`（删标签、去首尾空白），而 `SecurityConfig` 对**所有非排除路径**执行 `StpUtil.checkLogin()` |
| **落地方式** | `AigTaskCallbackController` + `AigTaskCallbackBo.progress` + 服务层的错误归类与进度 best-effort；`security.excludes` 与 `xss.excludeUrls` 各加一条；两条守卫测试：读 `application.yml`（含 prod 覆盖情形）断言排除项存在、并用**真的 XssFilter** 跑 MockMvc 断言"排除时逐字节相等 / 不排除时确实被改写" |
| **否决的替代（a）：签名放在请求体里** | 否决。验签算的是原始字节，签名进请求体就多一道「先解析、再取原文」的改写机会；而这类失败看起来像"密钥配错了" |
| **否决的替代（b）：沿用平台「200 + body 里的 code」** | 否决。调用方是**机器**，它按状态码决定要不要重推：401/404/409 都是「再推也没用」，回 200 会让对方的监控显示一切正常。面向页面的约定与面向机器的约定在这里刻意不同 |
| **否决的替代（c）：不限流** | 否决。这是唯一不需要登录态就能到达的写路径，而**未验签的回调也会写一行账本**（刻意的：对方乱推/密钥配错/有人伪造，处置完全不同）。不限流时刷 `aig_callback` 就能把磁盘写满——2026-10-08 那次「磁盘满 → 容器起不来 → 回滚也失败」说明代价 |
| **否决的替代（d）：裸请求/坏 JSON 也记一行账本** | 否决。账本该记「一次回调尝试」，而不是任何人的随便一 POST；这两种请求在传输层就不成立，日志留痕足够 |
| **否决的替代（e）：用服务令牌而不是 HMAC 验签** | 否决。Provider 是外部系统，发不出我们的平台令牌（那需要先把令牌交给对方，等于把平台身份借出去）；共享密钥 + HMAC 是双方对等、可轮换的标准做法 |
| **必须同时接受的限制** | ①**没有时间窗**：同一载荷重放会被 `(providerCode, eventId)` 幂等挡住，改动字段会破坏签名，因此可接受——但没有「拒绝 5 分钟前的载荷」这一层；②**真实端到端 HTTP 未验证**（起本地后端 + 打签名请求未做），目前证据是配置守卫 + 真过滤器单测两级；③未配密钥的 Provider 一律拒绝（既定口径），接入方必须先拿到密钥；④回调失败后**不自动重试**（落在 FAILED）——是否为平台执行的任务自动转 `RETRY_WAIT` 是另一个决定，它会影响是否真的再花一次钱 |

---

## ADR-013 包体内容安全检查（F-02 第一切片）：只扫 ZIP、只映射既有规则、未扫必须显式标记—— **接受**（2026-10-09 追加）

| 项 | 内容 |
|---|---|
| **决定** | 注册 Package 时对**包体内容**做安全检查：只认 ZIP（`PK` 魔数）、全程内存流不解压落盘、逐条目**带上限读**、发现按既有五条规则码归类、**不新造规则码**；不通过则整笔拒绝（与 Manifest 拒绝同一口径）；**没扫 ≠ 通过**——`scanned=false` 必须显式标记并留日志 |
| **依据** | F-02 已冻："现状**没有**压缩包安全检查（因为不解压）→ V2 扫描器为**全新开发**，是 `EXECUTABLE` Skill 与 V2 导入的硬前置"。实测确认：`register` 只校验 Manifest 五条规则 + 包体哈希，**包体内容从头到尾没有人看**——Manifest 干净、包里塞 `install.sh` 或 ELF 的包会被照单收下 |
| **落地方式** | `AigPackageSecurityProperties`（阈值可配）+ `AigPackageArchiveScanner` + `AigArchiveScanResult`；`AigPackageServiceImpl.register` 在"哈希核对通过、落库之前"调用；9 条测试用**真实生成的 ZIP**覆盖每种形态（含正例与"没扫"两态） |
| **规则映射（不新造码的理由）** | 路径穿越/绝对路径/盘符/反斜杠 → `EXECUTABLE_OR_PRIVILEGED_ACCESS`（越界写入）；脚本扩展名、ELF/PE/Mach-O/class 魔数 → `UNBOUNDED_CODE_EXECUTION`（平台无法界定其行为）；条目数/单条体积/总解压量/压缩比超限 → `UNBOUNDED_CODE_EXECUTION`（§6.2-3 在"体量无法界定"形态上的**应用**，不是新规则）；私钥/`docker.sock`/`id_rsa` 特征 → `EXECUTABLE_OR_PRIVILEGED_ACCESS`（§6.2-1 明列生产密钥与 Docker Socket） |
| **否决的替代（a）：解压到磁盘再扫** | 否决。那等于"为了检查先炸一次"——检查器本身成了攻击面 |
| **否决的替代（b）：新造一条"不安全压缩包"规则码** | 否决。§6.2 的五条是冻结词表（ADR-004 的口径是逐条对齐、不放宽），加第六码需要设计侧签字；用既有码 + 写明解释更稳 |
| **否决的替代（c）：只信 `ZipEntry` 声明的体积** | 否决（**实测逼出来的**）：流式 ZIP 的 `getSize()/getCompressedSize()` 常为 -1，只信声明值等于没有体积防线。改成"逐条读到上限为止"后，zip bomb 的两个测试才真的红→绿 |
| **否决的替代（d）：非 ZIP 体一律拒绝** | 否决（本切片）。会把合法的非压缩包体一刀切掉，而本切片没有定义其它形态的判定规则；改为返回"未扫描"并显式标记，把边界写进结论而不是假装覆盖 |
| **~~未决~~ 已决（2026-10-09，用户拍板）** | **被拒的包体留证据行**。落地为 `aig_package_rejection`（追加型账本、**独立事务**写入、超长字段截断到列宽），覆盖注册的三个拒绝点：`MANIFEST_INVALID` / `CHECKSUM_MISMATCH` / `ARCHIVE_UNSAFE`（后者带命中规则码）。理由与 ADR-010/011 同源：注册失败会回滚调用方事务，证据必须活下来，否则"留痕"只在成功路径上成立，而那是**最不需要它**的路径。**在线验证**：上传含 `install.sh` 的包体 → 接口整笔拒绝、`aig_package`/`aig_package_version` **0 行**，而 `aig_package_rejection` 有且只有一行（`body_sha256`/`body_size` 与上传字节一致、`hit_rules=UNBOUNDED_CODE_EXECUTION`） |
| **已知缺口（第一切片时不假装覆盖；第二切片已补大半）** | ① ~~**不识别符号链接**~~ → **2026-10-10 已补**：自解中央目录（EOCD → 中央目录头），读 `version made by` 的高字节判宿主、读 external attributes 高 16 位取 Unix 模式位；`S_IFLNK` 与 FIFO/设备/套接字一律按 `EXECUTABLE_OR_PRIVILEGED_ACCESS` 拒绝（zip-slip 变体：先落链接、后续条目穿过它写入）。顺带增加「中央目录声明的条目数 vs 实际可读条目数」一致性检查。② ~~只嗅头部若干字节（默认 4096），不做全文凭据扫描~~ → **2026-10-10 已扩到** `max-text-scan-bytes`（默认 8MB/条，滑动窗口跨分片匹配，避免把特征对准 8KB 读边界就绕过）；**仍未覆盖**：base64 过的私钥、把 `AKIA` 拆两段拼接这类被编码/拆分的凭据——上界仍是"明显特征"而不是"找到所有秘密"，且超出上限的部分**不扫，并在结论里写明"只扫到上限"**。③ **只覆盖 ZIP 形态** → 第二切片能**认出**形态（gzip/bzip2/xz/7z/rar/cab/tar/PDF/图片/文本/未知二进制）并写进结论；**要不要因此拒绝**由 `reject-non-zip-archives` 决定（**默认 false = 维持第一切片"不判不拒"**，因为那会改变上传口的准入策略）。④**不执行、不沙箱运行**：扫的是"装了什么"，不是"跑起来会做什么"——要执行外部代码的前提是沙箱运行时（P1-003），本 ADR 不覆盖它 |

---

## ADR-014 没有平台评测执行器的对象：由**管理员人工评测**产出黄金用例证据 —— **接受**（2026-10-09 追加）

| 项 | 内容 |
|---|---|
| **决定** | 新增 `POST /aigov/evaluation/manual-run`：管理员按用例给出 PASS/FAIL，平台把它登记为**人工产出**的评测运行（`aig_evaluation_run.executed_by='ADMIN'`），从而满足 `GOLDEN_CASE` 门槛、使版本可以推进到 `CANDIDATE` |
| **依据（实测，见 `00-基线修订补遗.md` §五）** | ①平台侧 `IAigEvaluationSubject` 只有 4 个实现，**全部只 `supports(AGENT_VERSION, creative_*)`**；②Package 安装带入的第三方 Agent 与 Package 版本**结构上取不到任何评测结论**，而执行器注册表是设计上的"业务模块按需注册"；③生产 `aig_agent_version` **4 行全为 `STABLE`**、`aig_evaluation_run` **0 行**——`assertGoldenCaseEvidence` 与 `assertCanaryEvidence` **从未被真实触发过**，`CANDIDATE` 至今没有对象到达过。即：门槛存在、判据正确、但**没有任何对象能走到它前面** |
| **落地方式** | 新列 `aig_evaluation_run.executed_by`（`PLATFORM`/`ADMIN`，存量回填 `PLATFORM`）+ `AigEvaluationManualRunBo` + `AigEvaluationExecutorEnum` + 服务层 `recordManualRuns`；机器路径也显式写 `PLATFORM`；证据对象 `AigGoldenCaseEvidence` 增加 `adminCaseCodes`，发布门槛在放行时 `log.warn` 记下"这次靠人工结论放行" |
| **不放松的四条（与机器评测同一口径）** | ①只对 `SANDBOX_TESTED` 的版本取证（证据必须产生在门槛要求它的阶段）；②用例集合必须**等于**版本声明的集合、每条都要给 PASS/FAIL（不许挑着录、不许留空）；③**有平台执行器的对象拒绝走这条**——否则人工录入就成了绕过平台判据的通道；④用例声明了成本范围时必须上报成本（未上报≠在范围内） |
| **为什么必须有 `executed_by` 这一列** | 门槛只认 `result_status=PASS`，不区分谁产出的。两种来源都合法，但**可信度来源不同**：机器结论 = "平台的判据在同样输入上判过了"；人工结论 = "一个人看了并签了字"。没有这一列，人工结论会**伪装成机器结论**——这正是本 ADR 最不能出的问题。不写进 `remark` 的理由：remark 会被人工复核**追加**，性质放在会变的地方等于没放 |
| **保留的第二道判断** | 含 `rubric_json` 的用例，人工录入 PASS 后仍写 `review_result=MANUAL`，须再走一次 `/aigov/evaluation/review`。人工录入解决的是"谁产出结论"，复核解决的是"另一个人认不认"——两者合并等于顺手取消了复核环节 |
| **权限口径** | **独立权限 `aig:evaluation:manual`**（2026-10-09 拆分，只授 `aig_admin`）。原先复用 `aig:evaluation:run`（"能跑评测的人才能录人工结论"），但两者并不等价：机器评测的可信度来自"平台判据在同样输入上判过了"，人工录入的可信度只来自"一个人签了字"，而它直接决定版本能不能进灰度 ⇒ 收紧为管理角色。对 `aig_security` / `aig_viewer` 无影响（它们本就没有 `run`）；影响的是**自定义角色**：只被授 `run` 的不再能录人工结论 |
| **否决的替代（a）：补一个平台侧沙箱执行器** | 否决（本轮）。那需要先有沙箱运行时（属 F-02/F-06 一带），而它解决的是"平台能不能执行第三方代码"，与"这个版本该不该进灰度"不是同一个问题；在沙箱就位前，让门槛一直无人可过并不比人工结论更安全，只是**看不见**而已 |
| **否决的替代（b）：让 `runEvaluation` 接受一坨人工结论** | 否决。同一条路由两个语义，读代码的人无法从调用方式上看出这次是平台判的还是人填的 |
| **否决的替代（c）：把人工结论直接写进 `aig_release_event`** | 否决。发布事件是"谁在何时推进了状态"，不是评测证据；写在那里就绕开了评测账本，等于取消 `goldenCaseEvidence` 这条唯一判据 |
| **必须同时接受的限制** | ①人工结论的**质量取决于人**：平台只能强制"方法与依据必填 + 操作人必填 + 逐条 PASS/FAIL"，不能验证人真的跑过；②**权限已收紧**为独立权限 `aig:evaluation:manual`（只授 `aig_admin`），但仍不是"某个具体人"——凡被授予该权限的角色成员都能录；③前端页面**已接入**（2026-10-09，运行列表带「产出方」列、证据区标出人工来源）；④`executed_by` 不参与门槛判定（不阻断），只做**可见性**——把它做成"人工结论不算数"就等于本 ADR 没有落地 |

---

## ADR-015 不可信执行：宿主侧独立进程 + 一次性受限容器（沙箱执行器第一切片） —— **接受**（2026-10-10 追加）

| 项 | 内容 |
|---|---|
| **决定** | 外部/第三方代码的执行体是**宿主机上的独立进程**（`script/deploy/sandbox-run.sh`），它把作业放进**一次性受限容器**里跑。**平台后端不持有 docker socket、也不装 docker CLI**——这条边界必须保持 |
| **依据（生产实测）** | ①平台后端容器的挂载只有两个密钥文件 + logs/temp 卷，`/var/run/docker.sock` **不存在**、`docker` 命令**不存在**（实测）；②宿主机 docker 29.8、cgroup v2、seccomp 可用、8 核 / 12GB 可用内存、docker 根盘余 19.6GB；③**docker-socket 就在平台自己的禁用工具表里**（§6.2-1），包体扫描器也把它的特征串当拒绝理由——若为了让后端能开容器而把 socket 给它，等于让治理平台持有它自己禁止的能力 |
| **为什么必须是独立进程** | ADR-001 已定"不可信执行隔离在独立进程与容器"。本 ADR 把这句话落成可运行的东西，并明确了**谁**拥有 Docker：不是平台，是宿主侧 worker（`aiadmin`/`gh-deploy` 已在 docker 组） |
| **隔离参数（每条都在生产上验过）** | `--network none`（默认无网）· `--read-only` 根 + `/tmp` 64m tmpfs · `--cap-drop ALL` · `--security-opt no-new-privileges` · `--pids-limit` · `--memory`/`--memory-swap` 相等（不给交换）· `--cpus` · `--ulimit nofile` · **非 root 运行**（用作业账户 uid，root 调用则退 65534）· 不使用 `--privileged`、不共享 pid/network 命名空间、除作业目录外不挂宿主路径 |
| **实测结论（12/12）** | 非 root（uid=1000）· 无网（容器内 wget 失败）· 根只读而 `/work` 可写 · `CapEff=0` · 看不到 `/opt/ai-video-poc` · 超内存被 OOM 杀（`exitCode=137`）· 超时被杀且**无残留容器** · 产物 sha256 与宿主重算一致 · 非白名单镜像被拒 · 受保护工作目录被拒 · 并发第二次被拒 |
| **工作目录：三种做法的取舍（这一段是本次最有价值的实测）** | ① **直接绑定宿主普通目录** ⇒ ❌ **内存上限管不住磁盘**：128m 内存限制下 `dd` 照样写出 400MB（写文件走 page cache，不占容器内存）；而"把宿主机磁盘写满"正是 **2026-10-08 那次生产事故**的成因。想用 0.5s 轮询补救也不行——300MB 在 **387ms** 内就写完了。<br>② **容器内 tmpfs 当 `/work`** ⇒ 尺寸由内核强制（好），但 **tmpfs 随容器停止而消失**，跑完 `docker cp` 什么都取不到（实测产物全丢）。<br>③ **宿主侧定长 scratch（tmpfs）绑定挂载** ⇒ ✅ 同时满足"有硬上限"与"产物留得下"：内核在写满时给 ENOSPC（实测 `dd` 写出 67,080,192 字节后被截住、剩余 0MB），产物在容器停止后仍在宿主机上。**采 ③**，由 `sandbox-install.sh` 建（默认 1G，可配）。 |
| **已被实测教训钉住的两条实现细节** | ①命令通过**环境变量**传进容器，不拼进 `sh -lc` 字符串（引号/反斜杠会破坏拼接）；②宿主侧 `timeout` 杀掉的是 **docker CLI**、不等于容器停了，因此无论成败都要 `docker rm -f` 清容器（并有专门测试断言"无残留"） |
| **刻意不做的事** | ①**不执行平台自己的代码**：这是给不可信内容用的，第一切片只证明隔离成立；②**不自行拉取镜像**：镜像必须已在本地且在白名单里（白名单不存在 ⇒ 拒绝运行），否则"能改作业参数"就等于"能运行任意镜像"；③**默认不出网**：要出网得显式 `--allow-network`，而出站策略本身属 **F-09（安全+法务，未冻）**；④**不做 worker 的调度与上报**（见下） |
| **仍然缺的那一半（说清楚，别把"有执行器"读成"能接外部代码"）** | ①**worker**：谁去领取作业、把结果与产物上报平台——平台**无 MQ**，现有模式是 `sweep()` 轮询；worker 若要调平台接口需要**服务身份**（服务令牌已实现、**开关仍关**）；②**后端侧接口**：作业领取 + 结果上报 + `SANDBOX_RUN` 这道门槛的**证据断言**（补遗 §1.1 实测：`SANDBOX_RUN` 与 `HUMAN_APPROVAL` 目前**没有任何证据校验**，只是申报）；③**产物回传**：从 scratch 取走并登记到制品账本（`aig_task_artifact`）；④**镜像分发策略**：第三方镜像如何进白名单（当前只想得到"平台构建并推 GHCR 摘要，worker 按摘要拉取"这条） |
| **必须同时接受的限制** | ①scratch 用 tmpfs ⇒ 占内存（默认 1G）且宿主重启即清空（沙箱作业本来是一次性的，产物须尽快取走）；②**并发固定为 1**（首版，8 核/12GB 的保守选择），要靠实测再调；③首版**只有"跑一次命令"，没有多步骤编排**；④隔离参数防的是"越界与耗尽"，不防"容器逃逸内核漏洞"——那要靠保持 docker/内核版本更新，属运维例行 |

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
| 010 | 接受（2026-10-09 追加） | 制品账本落成 **`aig_task_artifact` 单表**（平台铸造 `artifact_id` + `storage_ref` 指字节）；被拒登记**留 FAIL 证据行并独立事务提交**；`hash_verified` 记录「哈希是否被平台核对过」（v1 一律 `N`）；**不建对象键唯一键**、**不新开制品权限点** |
| 011 | 接受（2026-10-09 追加） | 策略决策落到任务上：`policy_result` 用 **PASS/REJECT/MANUAL**（映射唯一实现），写事件 `AI_TASK_POLICY_DECIDED`；细因 `reasonCode` 用**契约镜像枚举**且**只在无歧义时写**（认不出留空，不猜）；`taskId` 带进调用入参以填决策账本的 `task_id` |
| 013 | 接受（2026-10-09 追加） | 新增**包体内容安全检查**（F-02 第一切片）：只扫 ZIP、内存流、逐条带上限读；发现只映射既有的五条规则码（不新造码）；不通过整笔拒绝；**没扫必须显式标记**（scanned=false）。未决：被拒包体要不要留证据行 |
| 012 | 接受（2026-10-09 追加） | 暴露 **`POST /aigov/task/callback`**：签名/Provider 走请求头（签原始字节）、按结论返回**真实 HTTP 状态码**、**IP 限流**、裸请求/坏 JSON 不写账本；该路径**必须同时**进 `security.excludes` 与 `xss.excludeUrls`（后者会重写请求体导致签名全废）；回调声明的错误码由平台归类；进度 best-effort |
| 014 | 接受（2026-10-09 追加） | 平台没有该对象执行器时，**由管理员人工评测产出 `GOLDEN_CASE` 证据**（`POST /aigov/evaluation/manual-run`，行上标 `executed_by=ADMIN`）。不放松四条：只对 `SANDBOX_TESTED` 取证、集合等于声明集合且逐条 PASS/FAIL、**有执行器的对象拒绝走这条**、声明了成本范围必须上报成本。含 Rubric 的用例仍须再走一次复核 |
| 015 | 接受（2026-10-10 追加） | **不可信执行 = 宿主侧独立进程 + 一次性受限容器**（`script/deploy/sandbox-run.sh`）：默认无网、根只读、cap 全丢、非 root、内存/CPU/进程数硬限、**定长 scratch** 当工作目录（内核强制 ENOSPC 且产物留得下）。**平台后端不持有 docker socket**（它自己的禁用工具表里就有 docker-socket）。12/12 隔离项在生产实测通过。worker/后端接口/`SANDBOX_RUN` 证据断言仍未做 |

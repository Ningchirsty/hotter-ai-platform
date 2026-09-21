# 实现规范：AI 模型能力接入与配置管控（阶段1）

> 仓库 `hotter-ai-platform`（RuoYi-Vue-Plus 6.0.0），分支 `feature/talent-library`。
> 依据《AI 模型能力接入与配置管控设计》§2 §4 §5 §6 §9 §10。
> **本文是编码唯一契约，类名/字段名/方法签名以此为准。**

---

## 0. 已核实的事实（不得凭 5.x 或想象改写）

| # | 事实 | 证据 |
|---|---|---|
| 1 | 模型主数据在 **snail-ai**：`sai_model_provider`（7 条，含 Ollama 本地）、`sai_model_config`（含 `model_key`/`model_type`/`adapter_key`/`api_endpoint`/`scope`/`is_default`/`is_enabled`） | 数据库实测 |
| 2 | 现有 1 个默认模型：`glm-5.1`，`model_type=CHAT`，`adapter_key=openai-compatible`，`is_default=1` | 数据库实测 |
| 3 | **程序化调用入口**：注入 `OpenApiChatClient`（由 `SnailAiOpenApiAutoConfiguration` 注册），调 `chatSync(OpenApiChatRequest) → Result<OpenAiChatSyncResponse>`；另有 `chatStream` 返回 `Flux<OpenApiChatStreamEvent>` | `snail-ai-openapi-core` / `-starter` |
| 4 | 门面 `SnailAiOpenApi` 提供 `chat(Long appId)` builder、`listAgents()`、`getAgent()` | 同上 |
| 5 | 该客户端是**指向 snail-ai server 的 HTTP 客户端**，配置项 `snail-ai.server.host/port`、`app-id`、`token`（`application-dev.yml`）；**需要独立运行 `ruoyi-extend/ruoyi-snailai-server`** | 同上 |
| 6 | `snail-ai-agent-chat-starter` 暴露的是 `/api/snail/chat` **聊天 UI 嵌入**（发 embed token），**不是**程序化调用接口 | `SnailAiChatGatewayController` javap |
| 7 | `snail-ai.enabled=false` 时整个 `SnailAiConfig` 不加载，`OpenApiChatClient` 等 Bean **不存在** | `SnailAiConfig.java:11-15` |
| 8 | `ruoyi-common-ai` 只有一个类 `SnailAiConfig`，依赖三个 starter | 模块实查 |
| 9 | `sai_model_config.api_key` 是 **SM4 密文列**（`Mode.CBC` + `PKCS5Padding`，key/iv 取自 snail-ai 的 `snail-ai.crypto.secret-key` / `snail-ai.crypto.iv`，二者均为 hex 字符串）；治理层若写入，必须产出**同口径**密文 | 反编译 `CryptoHelper` + 落库实测 |
| 10 | `sai_model_usage_stat` 按 模型×用户 聚合，字段 `total_calls/success_calls/failed_calls/total_tokens_used/total_cost/avg_response_time` | 表结构 |
| 11 | 菜单 ID `1763*`、角色 `1763*`、字典 `17632*` 空闲；`1761*` 核心、`1762*` 人才库、`9200*` 视频 | 数据库实测 |

### 由事实导出的 3 条架构决策

1. **不重建模型注册**。`aig_model_governance.model_id` 关联 `sai_model_config.id`，只补治理属性，**不加外键**。
2. **调用走可插拔 SPI**。治理层不直接绑死 snail-ai：定义 `ModelInvoker` 接口，阶段1 提供两个实现：
   - `SnailAiChatInvoker` — 注入 `OpenApiChatClient`（`@ConditionalOnBean(OpenApiChatClient.class)`，snail-ai 关闭时不加载）
   - `LocalRuleModelInvoker` — 本地规则实现，用于 `talent_match`（设计 §6.3 明确「本地匹配模型 + 白名单字段」）
   这样 snail-ai 未启用时治理层仍可编译、可启动、可验证。
3. **密钥：读只为探测，写必须密文**。
   - **读**：只有连通性测试（`POST /aigov/model/{modelId}/test`）会取 `api_key`，因为要拿真实凭据发一次探测请求；结果只进服务端内部 VO。
   - **写**：只有 `POST /aigov/model`（可选 `apiKey`）与 `PUT /aigov/model/secret`；写前一律由 `AigModelSecretCipher` 加密成 snail-ai 可解的 SM4 密文。
   - **不出**：任何查询响应都不返回密钥原值，列表只回 `keyConfigured` 布尔位（在 SQL 内算好）。
   - `aig_model_governance.secret_ref` 仍只做「引用登记」，**不参与运行时取密钥**——没有任何组件解析它，别把它当成生效开关。

---

## 1. 模块与坐标

新建 `ruoyi-modules/ruoyi-ai-gov`：
- `artifactId=ruoyi-ai-gov`，`parent=org.dromara:ruoyi-modules:${revision}`，包根 `org.dromara.aigov`
- 依赖（org.dromara 一律不写 version）：`ruoyi-api`、`ruoyi-common-core`、`ruoyi-common-web`、`ruoyi-common-mybatis`、`ruoyi-common-satoken`、`ruoyi-common-security`、`ruoyi-common-log`、`ruoyi-common-redis`、`ruoyi-common-json`、`ruoyi-common-ai`（**用于注入 `OpenApiChatClient`**，需 `optional`/条件加载）
- 注册：`ruoyi-modules/pom.xml` 加 `<module>`；根 `pom.xml` dependencyManagement 加坐标；`ruoyi-admin/pom.xml` 加依赖

> ✅ **启动风险已实证关闭**：`ruoyi-modules/ruoyi-ai` 本就依赖 `ruoyi-common-ai`，
> 而当前**正在稳定运行的 `ruoyi-admin.jar` 内已包含** `snail-ai-agent-chat-starter`、
> `snail-ai-agent-executor-starter`、`snail-ai-openapi-starter` 三个 jar，
> 且应用在 `snail-ai.enabled=false` 下正常启动。
> 因此本模块新增该依赖**不引入任何新的启动风险**，无需 `optional` 规避。
> 仍然保留 `SnailAiChatInvoker` 的 `@ConditionalOnBean(OpenApiChatClient.class)`，
> 因为该 Bean 只在 `snail-ai.enabled=true` 时才存在。

---

## 2. 数据层（SQL 已完成，勿改）

`script/sql/aig_ai_gov.sql`（5 表 + talent_match 种子）、`script/sql/aig_ai_gov_menu.sql`（16 菜单/3 角色/6 字典）。
**已在真实数据库执行通过，不要改动这两份文件。**

| 表 | 实体 | 主键 |
|---|---|---|
| `aig_capability` | `AigCapability` | `capabilityId` |
| `aig_model_governance` | `AigModelGovernance` | `governanceId` |
| `aig_capability_model` | `AigCapabilityModel` | `bindId` |
| `aig_route_policy` | `AigRoutePolicy` | `policyId` |
| `aig_invocation_audit` | `AigInvocationAudit`（**追加型，无 `del_flag`、不继承 `BaseEntity`**） | `auditId` |

其余 4 个继承 `BaseEntity` + `@TableLogic delFlag`。字段严格对齐 SQL 列名驼峰化。

---

## 3. 枚举（`org.dromara.aigov.enums`，均 `@Getter @AllArgsConstructor` + `find(code)`）

- `AigDataLevelEnum`：`PUBLIC/INTERNAL/RESTRICTED` + `rank()`（0/1/2，用于比较）
- `AigLifecycleStatusEnum`：`CANDIDATE/TRIAL/GRAY/PRODUCTION/SUSPENDED/RETIRED` + `callable()`（TRIAL/GRAY/PRODUCTION 为 true）
- `AigDeploymentTypeEnum`：`LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API` + `external()`（后两个为 true）
- `AigDataPolicyEnum`：`LOCAL_ONLY/LOCAL_FIRST/EXTERNAL_ALLOWED`
- `AigAuditLevelEnum`：`SUMMARY/FULL/HASH_ONLY`
- `AigUsageTypeEnum`：`PRIMARY/FALLBACK/GRAY`
- `AigInvokeResultEnum`：`SUCCESS("0")/FAILED("1")`
- `AigManualDecisionEnum`：`PENDING/ACCEPTED/REJECTED/NOT_REQUIRED`
- `AigRouteDecisionEnum`：`MODEL`（命中模型）、`MANUAL`（转人工）、`DENIED`（策略拒绝）

---

## 4. 常量（`AigConstants`）

```java
public interface AigConstants {
    String PERM_CAPABILITY_LIST   = "aig:capability:list";
    String PERM_CAPABILITY_QUERY  = "aig:capability:query";
    String PERM_CAPABILITY_ADD    = "aig:capability:add";
    String PERM_CAPABILITY_EDIT   = "aig:capability:edit";
    String PERM_CAPABILITY_REMOVE = "aig:capability:remove";
    String PERM_MODEL_LIST        = "aig:model:list";
    String PERM_MODEL_QUERY       = "aig:model:query";
    String PERM_MODEL_EDIT        = "aig:model:edit";
    String PERM_MODEL_SECRET      = "aig:model:secret";
    String PERM_ROUTE_LIST        = "aig:route:list";
    String PERM_ROUTE_QUERY       = "aig:route:query";
    String PERM_ROUTE_ADD         = "aig:route:add";
    String PERM_ROUTE_EDIT        = "aig:route:edit";
    String PERM_ROUTE_REMOVE      = "aig:route:remove";
    String PERM_AUDIT_LIST        = "aig:audit:list";
    String CAP_TALENT_MATCH       = "talent_match";
    /** 审计摘要最大长度 */
    int AUDIT_SUMMARY_MAX = 500;
}
```

---

## 5. 核心接口与实现

### 5.1 调用 SPI（可插拔，这是解耦 snail-ai 的关键）

```java
// service/invoker/ModelInvoker.java
public interface ModelInvoker {
    /** 支持的部署类型；路由据此筛选 invoker */
    boolean supports(AigDeploymentTypeEnum deploymentType);
    /** 该 invoker 是否可用（Bean 存在且配置就绪） */
    boolean available();
    /** 执行一次调用 */
    ModelInvokeResult invoke(ModelInvokeRequest request);
}

// service/invoker/ModelInvokeRequest：capabilityCode, modelId, modelKey, modelType,
//     deploymentType, secretRef, endpoint, dataLevel, prompt(String), payload(Map<String,Object>)
// service/invoker/ModelInvokeResult：success(boolean), output(String 结构化JSON), errorSummary,
//     tokensUsed(Long), cost(BigDecimal), latencyMs(long), modelVersion(String)
```

**两个实现：**

1. `SnailAiChatInvoker implements ModelInvoker`
   - `@Component` + `@ConditionalOnBean(OpenApiChatClient.class)`
   - 注入 `OpenApiChatClient`；`supports()` 返回 `deploymentType != LOCAL`（外部/集团共享走 snail-ai）
   - `invoke()` 用 `OpenApiChatRequest` 调 `chatSync(...)`，把 `Result` 转成 `ModelInvokeResult`；异常转失败结果，**不得抛出**
   - 需先用 `SnailAiOpenApi.chat(appId)` 或直接构造 `OpenApiChatRequest` —— **具体字段以 javap 实测为准**（见 §8 待办）

2. `LocalRuleModelInvoker implements ModelInvoker`
   - `@Component`，`supports()` 返回 `deploymentType == LOCAL`
   - 阶段1 实现 `talent_match` 的**本地规则匹配**：按输入 `skillTags` 与候选技能标签做交集/权重打分，产出结构化 JSON
   - 不访问任何网络；用于本地端到端验证

### 5.2 路由引擎（设计 §6，本模块的核心）

```java
// service/IAigRouteService.java
public interface IAigRouteService {
    /** 依据 能力 + 数据等级 决定执行路径；不抛异常，用 decision 表达结果 */
    AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel);
    /** 供预览/排障：返回决策依据明细 */
    List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel);
}
```

`AigRouteDecision`：`decision`(MODEL/MANUAL/DENIED)、`modelId`、`modelKey`、`deploymentType`、`invoker`、`policyHits`(List<String>)、`reason`。

**决策算法（必须按此顺序）：**

```
1. 能力必须存在且 status='0' → 否则 DENIED("能力不存在或已停用")
2. 取 aig_route_policy(capability, dataLevel)：
     无策略 → DENIED("未配置该数据等级的路由策略")   ← 默认拒绝，不默认放行
3. 若 policy.allowExternal='N'：候选模型仅限 deploymentType ∈ {LOCAL, GROUP}
4. 取 aig_capability_model(capability, status='0') 关联 aig_model_governance 与 sai_model_config：
     - governance.lifecycle_status 必须 callable()
     - governance.data_level_max 的 rank 必须 >= 本次 dataLevel 的 rank   ← 核心：模型等级不够则不可用
     - sai_model_config.is_enabled=1
     - deploymentType 必须 external()==false 或 policy.allowExternal=='Y'
5. 按 (usage_type=PRIMARY 优先 → GRAY → FALLBACK) 再按 priority 升序取第一个可用
6. 命中 → MODEL；无命中：
     - policy.fallbackToManual='Y' → MANUAL("无可用模型，转人工")
     - 否则 → DENIED("无可用模型且未允许转人工")
7. 若整条链路中因 allowExternal='N' 过滤掉了外部模型 → policyHits 里必须记录该原因
```

### 5.3 调用编排（唯一对外调用入口）

```java
// service/IAigInvokeService.java
public interface IAigInvokeService {
    AigInvokeVo invoke(AigInvokeBo bo);        // 同步调用一次能力
    AigInvokeVo dryRun(AigInvokeBo bo);        // 只做路由决策，不真调用
}
```

`AigInvokeBo`：`capabilityCode(@NotBlank)`、`dataLevel(@NotBlank)`、`prompt`、`payload(Map)`。
`AigInvokeVo`：`traceId`、`decision`、`modelId`、`modelKey`、`deploymentType`、`externalCall`、`output`、`pendingConfirm(List<String>)`、`reason`、`latencyMs`。

**`invoke()` 必须做的事（顺序固定）：**
1. 生成 `traceId`（`IdUtil.fastSimpleUUID()`）
2. `routeService.decide(...)`
3. **无论成功失败都要写一条 `aig_invocation_audit`**（用 try/finally 或独立 Bean，保证失败也留痕）
4. 决策为 `DENIED` → 直接返回，`result=1`，不调用模型
5. 决策为 `MANUAL` → 返回并标记 `pendingConfirm`，`manualDecision=PENDING`
6. 决策为 `MODEL` → 选 invoker（按 `supports`）→ 调用 → 写回 latency/tokens/cost
7. 输出不符合能力模板 `output_schema` 时**不视为成功**：`result=1`，`error_summary="输出不符合Schema"`
8. **不得把模型原始输出写入业务事实表**；只返回给调用方与审计

**审计写入（独立 Bean，避免自调用）**：
```java
@Component
public class AigAuditRecorder {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AigAuditContext ctx);
}
```
- `input_hash` = SHA-256(输入摘要) 前 64 位；`input_summary` 仅在 `audit_level` 允许且**不含受限内容**时写入
- **禁止**把人才个人资料（姓名/手机/证件/简历正文）写入 `input_summary`
- 异常只 `log.error`，不得因审计失败中断调用（与 `TalentAuditRecorder` 同口径）

### 5.4 管理服务

```java
IAigCapabilityService    // 能力目录 CRUD + 分页
IAigModelGovernanceService // 模型列表（关联 sai_model_config 展示 model_key/model_type/is_default）+ 治理属性 upsert
IAigRoutePolicyService   // 路由策略 CRUD + 分页
IAigModelBindingService  // 能力-模型绑定 CRUD
IAigAuditService         // 逐次审计分页查询
IAigTalentMatchService   // talent_match 具体能力：入参/出参加工（本地规则）
```

**`IAigModelGovernanceService.list()`** 要**跨表读取**：以 `sai_model_config` 为主表
（`model_key`、`model_type`、`is_default`、`is_enabled`、`api_endpoint`），左连 `aig_model_governance` 补治理属性。
用 MyBatis XML 写联表查询。
- 返回给前端时 **`api_endpoint` 仅在有 `aig:model:secret` 权限时下发**；`secretRef` 同理
- **绝不返回 `sai_model_config.api_key` 原值**；只回 `keyConfigured`（SQL 内算成布尔位），
  用于暴露「治理属性登记完整、但密钥为空」这类静默失败

**`updateModelSecret(bo)` — 模型密钥录入（阶段1 追加）**

治理台直接录入明文密钥，服务端加密后写入 `sai_model_config.api_key`，免去「治理台配一半、
snail-ai 管理端配一半」的割裂。要点：

- 加密口径必须与 snail-ai 的 `CryptoHelper` **逐字节一致**：`SM4/CBC/PKCS5Padding`，
  key/iv 为 hex 解码后的 16 字节。任何偏差都会让 snail-ai 运行时解密失败（**静默**故障）。
- 配置前缀 `aigov.model-crypto`：`enabled`（默认 **false**）、`secretKey`、`iv`。
  两侧配置不一致时，靠启动日志打印的 `fingerprint`（key+iv 的 SHA-256 前 12 位）人工对账。
- `enabled=true` 时启动做一次「加密→解密」自检，失败即**启动失败**——宁可起不来，
  也不要让不可解密的密文进库。
- `clearKey=true` 走显式清除语义（不采用 snail-ai 管理端「留空=不修改」的隐式约定）。
- 写入需要 `aig:model:secret`：控制器 `@SaCheckPermission` + 服务层二次校验。
- 审计：控制器 `@Log(..., excludeParamNames = {"apiKey"})`，
  否则明文会经 `sys_oper_log.oper_param` 泄漏。
- **已知边界**：`secret_ref` 与 `api_key` 是两套东西。`secret_ref` 只是引用登记，
  没有任何组件解析它；真正生效的是 `api_key`。

---

## 6. Controller（`org.dromara.aigov.controller`）

| 类 | 路径 | 主要接口 | 权限 |
|---|---|---|---|
| `AigCapabilityController` | `/aigov/capability` | `GET /list`、`GET /{id}`、`POST`、`PUT`、`DELETE /{id}` | 对应 `aig:capability:*` |
| `AigModelController` | `/aigov/model` | `GET /list`、`GET /{modelId}`、`POST`（新增模型，可选明文 `apiKey`）、`PUT /governance`、**`PUT /secret`**（写入/清除模型密钥）、`POST /{modelId}/test`（连通性测试）、`GET /providers`、`GET /providers/all`、`POST /provider`、`PUT /provider` | `aig:model:list/query/edit`；新增与供应商走 `aig:model:add`；**密钥走 `aig:model:secret`** |
| `AigRoutePolicyController` | `/aigov/route` | `GET /list`、`POST`、`PUT`、`DELETE /{id}` | `aig:route:*` |
| `AigModelBindingController` | `/aigov/binding` | `GET /list`、`POST`、`DELETE /{id}` | `aig:route:*`（绑定属于路由配置） |
| `AigInvokeController` | `/aigov/invoke` | `POST /{capabilityCode}`、`POST /dryRun` | `aig:capability:query` |
| `AigAuditController` | `/aigov/audit` | `GET /list` | `aig:audit:list` |

硬约束：Controller 只做参数接收与组装 `R<T>`；**不注入 Mapper、不写路由逻辑**；写接口加 `@RepeatSubmit`。

---

## 7. 前端（`frontend/src/`）

```
api/aigov/capability/{index.ts,types.ts}
api/aigov/model/{index.ts,types.ts}
api/aigov/route/{index.ts,types.ts}
api/aigov/audit/{index.ts,types.ts}
views/aigov/capability/index.vue
views/aigov/model/index.vue
views/aigov/route/index.vue
views/aigov/audit/index.vue
```

- 组件路径必须与菜单 SQL 的 `component` 完全一致：`aigov/capability/index` 等
- **不新增路由文件**（菜单动态下发）
- 模型页：展示 snail-ai 的模型清单 + 编辑治理属性；`secretRef`/`api_endpoint` 用 `v-hasPermi="['aig:model:secret']"` 控制显示
- 能力页：输入/输出 Schema 用 JSON 文本域；数据策略、审计等级用字典下拉（`aig_data_policy`、`aig_audit_level`）
- 路由页：按 能力×数据等级 配置；`allowExternal` 用开关，并在页面上**醒目标注风险**
- 审计页：只读列表，展示 traceId/能力/模型/数据等级/是否外发/策略命中/耗时/结果
- 参考 `views/talent/profile/index.vue` 与 `views/system/post/index.vue` 的风格（`useDict`、`v-hasPermi`、`proxy.$modal`）

---

## 8. 验证要求

1. 后端 `mvn -o -DskipTests -Dmaven.test.skip=true -pl ruoyi-modules/ruoyi-ai-gov -am compile` 必须 BUILD SUCCESS
2. 前端 `pnpm lint` 与 `pnpm build` 必须通过
3. **本地真实环境端到端**（本机已跑通 MariaDB 3306 / Redis 6379 / 应用 8080）：
   - 登录后 `POST /aigov/invoke/dryRun` 传 `{capabilityCode:"talent_match", dataLevel:"RESTRICTED"}` → 应返回 `MODEL` 且 `deploymentType=LOCAL`
   - 传一个未配置策略的能力 → 应返回 `DENIED`
   - `POST /aigov/invoke/talent_match` → 走 `LocalRuleModelInvoker`，返回结构化输出，且 `aig_invocation_audit` 落一条记录
   - 用超管之外的角色验证 `aig:model:secret` 未授权时**看不到** secretRef/api_endpoint
4. 仓库内不得出现任何真实密钥或个人资料

## 9. `SnailAiChatInvoker` 的确定实现依据（已实测确认，勿再猜）

从 `snail-ai-commons-core-1.1.1.jar` javap 得到的真实结构：

```java
// com.aizuda.snail.ai.common.openapi.dto.OpenApiChatRequest
Long agentId; String openId; String conversationId; String content;   // content = 提示词
List<OpenApiChatAttachmentRequest> attachments; List<Long> disabledMcpServerIds;
List<Long> disabledSkillIds; Boolean deepPlanEnabled; Boolean webSearchEnabled;
String sid; long timeout;

// com.aizuda.snail.ai.common.openapi.dto.OpenApiChatSyncResponse
String conversationId; String content;   // content = 模型输出
Long durationMs;
```

### ⚠️ 架构错配（必须写进代码注释与交付文档）

**snail-ai 的聊天入口收的是 `agentId`（Agent），不是 `model_key`（模型）。**
即：经 snail-ai 调用时，**实际模型由 Agent 决定，治理层无法精确指定某个 `sai_model_config` 模型**。

处置口径：
- `agentId` / `openId` / `timeout` 从配置取（`aigov.snail-ai.*`），**不从 `AigCapabilityModel.modelId` 强推**
- `available()` = `openApiChatClient != null && aigov.snail-ai.enabled && agentId > 0`
- `tokensUsed` 与 `cost` 保持 `null` —— 响应结构不返回 token 用量，**禁止编造**
- 路由引擎仍按 `sai_model_config` 做「能不能用 / 等级够不够」的治理判定，
  但走 snail-ai 路径时实际模型由 Agent 决定；`AigRouteDecision` 需保留该说明
- 建立「模型 ↔ Agent」精确映射属于**阶段 2**

### 其余待办
- `snail-ai.app-id` 的实际取值与 `sai_app` 表的对应关系（阶段1 不阻塞）

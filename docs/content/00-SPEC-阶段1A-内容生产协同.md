# 内容生产协同 · 阶段1A 实施设计契约（SPEC）

> 依据：《资料详情页AI协同生产系统设计_V3_模型能力接入版》
> 技术底座：RuoYi-Vue-Plus 6.0.0（非微服务）
> 本文是阶段1A 的**实现契约**，实现与本文不一致时以本文为准；如需偏离，先改本文。
> 目标分支：`feature/talent-library`（不合入 main）

---

## 0. 已确认基线（不再讨论，实现前提）

| # | 决策 | 说明 |
|---|---|---|
| B1 | 范围 = **阶段1A** | 对齐设计文档 §19 的 P0 内容协同部分 |
| B2 | **复用 `ruoyi-ai-gov` 作为 AI 网关** | 不重建治理层；内容模块只调用「业务能力编码」 |
| B3 | 阶段1A **不依赖 snail-ai** | 解析与预检用本地 POI/PDFBox + 规则；真实模型接入待 snail-ai 就绪 |
| B4 | OCR **暂不处理** | 图片型资料仅归档 + 明确提示；后续经 snail-ai 的 docling/PaddleOCR 补齐 |
| B5 | 产品/SKU 建**轻量表** | 不建产品主数据平台，但需要可追溯的产品/SKU 标识 |
| B6 | 互动卡责任人**由任务指定** | 不按角色自动推导；系统只提供默认值（任务负责人） |
| B7 | 闸门强制项**表驱动** | 运营可改，不发版 |
| B8 | 菜单挂**业务应用** | 业务应用 → 内容生产协同 |
| B9 | 异步用**应用内执行器 + 任务状态表** | 不引入 SnailJob 依赖 |
| B10 | 阶段1A **不碰视频** | `video_generate` 属阶段4 |

### 0.1 不可违反的业务红线（来自设计文档 §2.3 / §3.4）

1. **AI 不得自动确认**产品真实性、包装法规、说明书步骤、品牌合规性。
2. **产品事实只能来自经确认的产品资料**；参考图与 AI 输出**不得**反向成为产品结构、颜色、数量、包装、参数的依据。
3. 未授权外部调用数必须为零（由 aigov 路由审计保证）。

落到实现上：**任何解析结果一律以「待确认」落库**，不存在「AI 直接写入既定事实」的代码路径。

---

## 1. 模块坐标

| 项 | 取值 |
|---|---|
| 后端模块 | `ruoyi-modules/ruoyi-content` |
| 包根 | `org.dromara.content` |
| 表前缀 | `cp_`（talent 用 `tl_`、aigov 用 `aig_`） |
| 菜单 ID 段 | `1765xxxxxxxxxxxxxxx` |
| 前端 | `frontend/src/api/content/`、`frontend/src/views/content/` |
| 依赖方向 | `content → ai-gov`（单向；ai-gov 不反向依赖 content） |

---

## 2. 数据模型（8 张表）

所有表统一含：`del_flag`、`create_dept`、`create_by`、`create_time`、`update_by`、`update_time`、`remark`（审计表除外）。

### 2.1 `cp_product` 轻量产品/SKU

| 列 | 类型 | 说明 |
|---|---|---|
| product_id | bigint PK | |
| product_code | varchar(64) | 产品编码，唯一 |
| product_name | varchar(255) | 产品名称 |
| sku_code | varchar(64) | SKU 编码，可空（产品级） |
| sku_name | varchar(255) | SKU 名称 |
| category | varchar(64) | 品类（首期试点：积木花） |
| version | varchar(32) | 产品版本（用于影响面追溯） |
| status | char(1) | 0 正常 1 停用 |

### 2.2 `cp_task` 内容生产任务

| 列 | 类型 | 说明 |
|---|---|---|
| task_id | bigint PK | |
| task_no | varchar(32) | 任务号，唯一 |
| task_name | varchar(255) | |
| deliverable_type | varchar(32) | 见 §3.1 |
| product_id | bigint | 关联 cp_product |
| sku_code | varchar(64) | |
| deadline | datetime | 截止时间 |
| owner_id / owner_name | bigint / varchar(64) | 任务负责人（互动卡默认指派人） |
| data_level | varchar(16) | PUBLIC/INTERNAL/RESTRICTED，复用 aigov 口径 |
| allow_external | char(1) | 是否允许外部 AI（Y/N），默认 N |
| status | varchar(32) | 见 §3.2 |
| block_reason | varchar(500) | 当前阻断原因（闸门写入） |
| parse_done_at | datetime | 解析完成时间 |

### 2.3 `cp_task_file` 任务附件

| 列 | 类型 | 说明 |
|---|---|---|
| file_id | bigint PK | |
| task_id | bigint | |
| file_name | varchar(255) | 原始文件名 |
| file_ext | varchar(16) | |
| file_size | bigint | 字节 |
| file_ref | varchar(500) | OSS 对象键或文件引用；**业务库不存文件本体** |
| file_kind | varchar(16) | 见 §3.3 |
| source_type | varchar(16) | UPLOAD / REFERENCE |
| data_level | varchar(16) | 单个文件可高于任务等级 |
| parse_status | varchar(16) | 见 §3.4 |
| parse_message | varchar(500) | 失败/跳过原因（**给用户看得懂的话**） |
| parsed_text_ref | varchar(500) | 抽取文本的引用，避免正文入库 |

### 2.4 `cp_fact_snapshot` 产品事实快照

| 列 | 类型 | 说明 |
|---|---|---|
| snapshot_id | bigint PK | |
| task_id | bigint | |
| snapshot_version | int | 快照版本，每次确认递增 |
| field_code | varchar(64) | 事实字段编码（如 `product_height`） |
| field_name | varchar(128) | |
| field_value | varchar(500) | 值 |
| unit | varchar(32) | 单位 |
| source_file_id | bigint | 来源附件 |
| source_locator | varchar(255) | 如「产品参数表 V2 第 3 行」 |
| source_excerpt | varchar(500) | 原文摘录（**证据**，用户可见） |
| confidence | decimal(5,2) | 解析置信度 |
| confirm_status | varchar(16) | PENDING/CONFIRMED/CONFLICT/REJECTED |
| confirmed_by / confirmed_at | bigint / datetime | |

> **同一 task + field_code 允许多行**：多来源不同值即构成冲突，冲突由 §4.3 检出并为每行标 CONFLICT。这样「证据链」不会被覆盖。

### 2.5 `cp_gate_rule` 闸门规则（表驱动）

| 列 | 类型 | 说明 |
|---|---|---|
| rule_id | bigint PK | |
| deliverable_type | varchar(32) | |
| field_code | varchar(64) | 要求确认的事实字段 |
| field_name | varchar(128) | |
| gate_level | varchar(16) | BLOCK / CONDITION / NOTICE |
| require_present | char(1) | 是否必须存在（Y=连值都没有也算未满足） |
| enabled | char(1) | |
| sort_no | int | |

### 2.6 `cp_interaction_card` 互动确认卡

| 列 | 类型 | 说明 |
|---|---|---|
| card_id | bigint PK | |
| task_id | bigint | |
| card_type | varchar(16) | 见 §3.5 |
| field_code | varchar(64) | |
| title | varchar(255) | 一句话问题 |
| question | varchar(1000) | 具体问题描述 |
| evidence_json | text | 证据来源（文件、定位、摘录） |
| impact_json | text | 影响对象（交付物/页面/包装/说明书） |
| options_json | text | 处理选项 |
| gate_level | varchar(16) | 是否阻断由它决定 |
| blocking | char(1) | 冗余自 gate_level=BLOCK，便于列表筛选 |
| assignee_id / assignee_name | bigint / varchar(64) | **任务指定** |
| due_at | datetime | |
| status | varchar(16) | 见 §3.6 |
| resolved_value / resolved_option | varchar(500) / varchar(64) | |
| resolved_by / resolved_at | bigint / datetime | |

### 2.7 `cp_work_package` 设计开工包

| 列 | 类型 | 说明 |
|---|---|---|
| package_id | bigint PK | |
| task_id | bigint | |
| snapshot_version | int | 签发时冻结的事实版本 |
| content_json | longtext | 开工包全文（见 §4.5 结构） |
| status | varchar(16) | DRAFT / ISSUED |
| generated_by / generated_at | bigint / datetime | |
| issued_by / issued_at | bigint / datetime | |

### 2.8 `cp_async_job` 异步作业

| 列 | 类型 | 说明 |
|---|---|---|
| job_id | bigint PK | |
| task_id | bigint | |
| job_type | varchar(16) | PARSE / PRECHECK / PACKAGE |
| status | varchar(16) | QUEUED / RUNNING / SUCCESS / FAILED |
| progress | int | 0–100 |
| message | varchar(500) | 失败原因（用户可读） |
| started_at / finished_at | datetime | |

---

## 3. 枚举

- **3.1** DeliverableType：`ECOM_DETAIL` 电商详情图 / `MAIN_IMAGE` 主图SKU图 / `EXHIBITION` 展会宣传图 / `MANUAL` 说明书 / `PACKAGE` 包装 / `VIDEO` 视频内容
- **3.2** TaskStatus：`DRAFT` 草稿 / `PARSING` 解析中 / `PENDING_CONFIRM` 待确认待补料 / `CONDITIONAL_READY` 条件开工 / `READY` 可开工
  - 1B 预留（阶段1A **不可达**，仅占位）：`PLANNING` / `PRODUCING` / `AI_CHECKING` / `REVIEWING` / `BRAND_REVIEW` / `REVISING` / `CONFIRMED` / `DELIVERED` / `ARCHIVED`
- **3.3** FileKind：`EXCEL` / `WORD` / `PDF` / `IMAGE` / `VIDEO` / `DESIGN` / `OTHER`
- **3.4** ParseStatus：`PENDING` / `PARSING` / `DONE` / `FAILED` / `SKIPPED`（图片与旧版 .doc 为 SKIPPED，须带可读原因）
- **3.5** CardType：`MISSING` 缺料 / `CONFLICT` 冲突 / `APPROVAL` 审批 / `SUPPLEMENT` 补料 / `EXCEPTION` 例外
- **3.6** CardStatus：`PENDING` / `RESOLVED` / `BLOCKED`（暂不确认并阻断）/ `CLOSED`
- **3.7** GateLevel：`BLOCK` / `CONDITION` / `NOTICE`
- **3.8** 数据等级：复用 aigov 的 `PUBLIC` / `INTERNAL` / `RESTRICTED`（字典 `aig_data_level`），**不另建一套**

---

## 4. 核心算法

### 4.1 闸门判定（唯一允许改任务状态的规则入口）

```
输入：task + 该 deliverable_type 的 enabled 闸门规则 + 当前事实快照
对每条规则：
  BLOCK 级：存在 CONFIRMED 快照值 → 满足
            否则                          → 未满足(阻断)
  CONDITION 级：存在 CONFIRMED 值 → 满足；否则 → 未满足(条件)
  NOTICE 级：不参与流转判定，仅记录

任务状态：
  存在未满足的 BLOCK   → PENDING_CONFIRM，block_reason = 未满足的 BLOCK 字段名拼接
  无 BLOCK 未满足，有 CONDITION 未满足 → CONDITIONAL_READY
  全部满足（BLOCK/CONDITION）          → READY
```

> **状态变化必须由「规则条件 + 人工权限」共同决定**（设计文档 §15）。闸门判定只产出「规则结论」，最终由 §6.7 的接口在人类确认动作后触发重算。

### 4.2 解析（能力 `document_parse`）

- Excel：POI 遍历 sheet，产出「行 → 字段候选」
- Word（docx）：结构化遍历（段落 + 表格，复用人才库已修复的表格处理思路）
- PDF：PDFBox 文本层
- 图片 / 旧版 .doc：`SKIPPED` + 明确提示（本期不做 OCR）
- 产出：候选字段（field_code、值、来源定位、原文摘录、置信度），**一律以 PENDING 落库**

### 4.3 冲突检测（能力 `brief_precheck`）

```
按 field_code 分组当前快照：
  同一 field_code 存在多个「值不等」的候选 → 生成 CONFLICT 卡，相关行标 CONFLICT
按闸门规则比对：
  BLOCK/CONDITION 字段无任何候选 → 生成 MISSING 卡
```

- 冲突卡必须带：多个来源文件、各自定位与摘录（设计文档 §7.2 的「来源」要求）
- **不做 AI 判断谁对谁错**，只呈现证据给人选

### 4.4 解析能力如何经由治理层调用

内容模块**不直接调用解析库**，而是调用 aigov 的能力编码（设计文档 §16.1 要求「只调用业务能力编码」）：

```
ContentParseService
  → IAigInvokeService.invoke(capabilityCode='document_parse', dataLevel, payload)
      → aigov 路由（本地优先、默认拒绝）
      → LocalContentInvoker（新增，按能力分发）
      → 审计留痕
```

> **对 aigov 的改动（实施后据实修订）**：原计划把 `LocalRuleModelInvoker` 改成
> 「按 capabilityCode 分发的总调度」。实施时发现那样会把它变成一个上帝类，
> 且治理层将被迫了解各类业务细节。**实际做法更小也更干净**：
>
> 1. `ModelInvoker` SPI 新增 `supportsCapability(String)`（带默认实现 false，向后兼容）；
> 2. 路由的调用器挑选改为**两段式**：先找「声明处理该能力」的调用器，
>    找不到再退回「只按部署类型匹配」——第二段保证既有行为不变；
> 3. `LocalRuleModelInvoker` 仅声明 `talent_match`；内容模块提供
>    `ContentLocalInvoker` 声明 `document_parse` / `brief_precheck`。
>
> **为什么必须看能力编码**：同一部署类型（LOCAL）下会有多个本地调用器。
> 若只看部署类型，路由只能取「列表里第一个可用的」，结果取决于 Spring Bean 的
> 装配顺序——会出现「资料解析被派给人才匹配调用器」这类不确定错配。
>
> 这仍是阶段1A 唯一需要动 aigov 的地方，且是扩展而非改写。
>
> **payload 传参口径**：`document_parse` 的 `fileBytes` 以 `byte[]` 放在 payload 中
> 进程内传递，不落库、不出网。审计侧 `AigInputSanitizer.safeValue` 只写**值的类型名**
> （`byte[]`），不做序列化，因此不会把文件内容带进审计，也不会有体积问题。

同时按 §B2 在 aigov 登记两个能力与路由策略（本地优先、`allow_external='N'`）。

### 4.5 开工包结构（`content_json`）

```json
{
  "taskNo": "...", "deliverableType": "...", "product": {...},
  "confirmedFacts": [ {"fieldCode","fieldName","value","unit","source","excerpt"} ],
  "assets": { "product": [...], "brand": [...], "reference": [...] },
  "contentUnits": [...],            // 1A 仅占位，1B 落地
  "copy": { "confirmed": [...], "forbidden": [...] },
  "gaps": [ {"fieldCode","alternative","decidedBy"} ],
  "allowedAiActions": [...], "immutableItems": [...],
  "spec": { "output","size","acceptance" },
  "owner": {...}, "deadline": "...", "snapshotVersion": 3
}
```

`immutableItems` 必须包含「产品主体、Logo、包装文字」——对应设计文档 §10「AI 生产 Agent 禁止自由重绘」。

---

## 5. 异步模型

- 应用内执行器（与 video 模块同口径），**不引入 SnailJob**
- 每次解析/预检/生成开工包写一条 `cp_async_job`
- 前端通过任务详情轮询作业状态与任务状态
- 并发度 1（本地解析是 IO + CPU 混合，且要避免与其它模块争内存）

---

## 6. 接口清单

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 6.1 | GET | `/content/task/list` | `content:task:list` | 任务分页 |
| 6.2 | GET | `/content/task/{taskId}` | `content:task:query` | 任务详情（含附件/快照/卡片/作业） |
| 6.3 | POST | `/content/task` | `content:task:add` | 新建任务 |
| 6.4 | PUT | `/content/task` | `content:task:edit` | 编辑任务 |
| 6.5 | DELETE | `/content/task/{taskId}` | `content:task:remove` | 删除任务 |
| 6.6 | POST | `/content/task/{taskId}/file` | `content:task:edit` | 上传附件（multipart） |
| 6.7 | POST | `/content/task/{taskId}/parse` | `content:task:edit` | 触发解析（异步） |
| 6.8 | POST | `/content/task/{taskId}/precheck` | `content:task:edit` | 触发冲突检测（异步） |
| 6.9 | POST | `/content/task/{taskId}/recheck` | `content:task:edit` | 重算闸门并刷新任务状态 |
| 6.10 | GET | `/content/card/list` | `content:card:list` | 互动卡分页（支持 mineOnly「待我确认」/ blockingOnly「仅看阻断项」） |
| 6.11 | POST | `/content/card/resolve` | `content:card:handle` | 处理卡片（**cardId 在 body**，path 不带 ID） |
| 6.12 | GET | `/content/fact/list` | `content:task:query` | 事实快照列表（按任务，含来源文件名） |
| 6.13 | POST | `/content/fact/{snapshotId}/confirm` | `content:task:edit` | 确认单条候选值为事实 |
| 6.14 | POST | `/content/fact/{snapshotId}/reject` | `content:task:edit` | 否决单条候选值 |
| 6.15 | POST | `/content/fact/confirmUnambiguous` | `content:task:edit` | 一键确认无争议项（冲突字段跳过） |
| 6.16 | POST | `/content/fact/manual` | `content:task:edit` | 手工录入事实 |
| 6.17 | POST | `/content/workPackage/generate` | `content:package:generate` | 生成开工包（taskId 为 query 参数） |
| 6.18 | POST | `/content/workPackage/{packageId}/issue` | `content:package:issue` | 签发开工包 |
| 6.19 | GET | `/content/workPackage/byTask/{taskId}` | `content:package:list` | 查询任务最新开工包 |
| 6.20 | GET | `/content/product/list` | `content:product:list` | 产品分页 |
| 6.21 | GET | `/content/product/options` | `content:product:list` | 产品下拉选项 |
| 6.22 | GET | `/content/product/{productId}` | `content:product:query` | 产品详情 |
| 6.23 | POST/PUT/DELETE | `/content/product` | `content:product:add/edit/remove` | 产品维护 |
| 6.24 | GET | `/content/gateRule/list` | `content:gateRule:list` | 闸门规则分页 |
| 6.25 | GET | `/content/gateRule/{ruleId}` | `content:gateRule:query` | 规则详情 |
| 6.26 | POST/PUT/DELETE | `/content/gateRule` | `content:gateRule:add/edit/remove` | 规则维护 |

> **实施后补充说明（据实修订）**：原 §6 未包含事实确认接口。实施中发现一个功能缺口——
> 闸门只认 `CONFIRMED` 事实，而预检只为「冲突」与「缺失」生成卡片（§3.1 的设计意图），
> 于是**只有一个候选值的字段既没有卡、也无法被确认**，任务永远停在「待确认」。
> 故补 6.13–6.16 四个接口，其中「一键确认无争议项」符合 §3.1「人只处理例外和确认」：
> 同字段只有一个待确认候选才确认，存在多个取值的（冲突）一律跳过，仍由互动卡逐条裁定。
> 确认事实的权限复用 `content:task:edit`（属推进任务的编辑动作），未另建权限点。


---

## 7. 前端页面与菜单

```
业务应用（1764000000000000003）
└─ 内容生产协同            path=content            1765000000000000001
   ├─ 内容任务             content/task/index      1765000000000000101
   ├─ 互动确认卡           content/card/index      1765000000000000102
   ├─ 设计开工包           content/workPackage/index 1765000000000000103
   ├─ 产品与SKU            content/product/index   1765000000000000104
   └─ 闸门规则             content/gateRule/index  1765000000000000105
```

角色：`content_admin`（内容生产管理员，全部权限）、`content_member`（内容生产人员，任务与卡片权限，无删除/无规则维护）。

---

## 8. 对设计文档的裁剪说明（需知悉）

| 文档要求 | 阶段1A 处理 | 理由 |
|---|---|---|
| §9.1 十个能力编码 | 仅登记 `document_parse`、`brief_precheck`、`talent_match`(已有) | B1 范围；其余按阶段递进 |
| §13.2 调用授权审批 / 用量配额 / 评测灰度 | 不做 | 阶段2/5；aigov 已有审计与路由底座 |
| §10 Agent 协同 | 不做，但**接口与数据结构预留**（能力编码调用点） | Agent 属阶段4 |
| §11 素材谱系 / §12 知识候选池 | 不做，仅任务附件 + 事实快照 | 文档 §2.3 非目标 |
| §16.1 大文件存企业网盘/对象存储 | 复用现有 OSS，业务库存 file_ref | 已有能力 |
| 图片 OCR | 归档 + 明确提示 | B4 |

---

## 9. 验收方式

1. **编译**：`ruoyi-admin -am` 全链路 BUILD SUCCESS
2. **启动**：本地实例起得来，新模块菜单可见
3. **端到端冒烟脚本**（`script/smoke/smoke-content.mjs`，夹具 `script/smoke/fixtures/content-conflict.xlsx`）：
   ```bash
   node script/smoke/smoke-content.mjs          # 默认使用同目录夹具
   ```
   用例主线（与已实现的脚本一致）：
   - 建产品 → 建任务（积木花、电商详情图，初始 `DRAFT`）
   - 上传夹具 Excel：含主体版本 V1/V2 两个值（构造冲突），且**不含**包装版本（构造缺料）
   - 触发解析 → 断言候选字段落库且 `confirm_status=PENDING`（**红线：无 AI 直接写入事实**）
   - 触发预检 → 断言生成 CONFLICT 卡（带**两处来源与摘录**、标记阻断）与 MISSING 卡
   - 断言任务被闸门拦在 `PENDING_CONFIRM`
   - 裁定冲突卡（确认 V1）→ 仍为 `PENDING_CONFIRM`（其它强制项未确认）
   - 裁定缺失卡（手工录入包装版本）+ 一键确认无争议项 → 断言 `CONDITIONAL_READY`
     （本例未提供参考图，而参考图是 `CONDITION` 级、§8.2 明确其默认非强制项）
   - **回归断言**：状态可开工后 `blockReason` 必须被清空
     （MyBatis-Plus `NOT_NULL` 策略下实体传 null 不会进 UPDATE，曾导致此处残留旧阻断文本）
   - 生成并签发开工包 → 断言 `immutableItems` 含产品主体/Logo/包装文字
   - **反向断言**：未经人工确认的解析值不得出现在开工包的 `confirmedFacts` 中，
     且冻结的是人工裁定后的值（V1）
4. **治理侧断言**：`document_parse` / `brief_precheck` 的每次调用在 `aig_invocation_audit` 留痕，且 `external_call='N'`
5. 回归：aigov 与 talent 两套既有冒烟仍全绿

---

## 10. 实施顺序

1. SQL（建表 + 菜单权限 + 闸门规则种子 + aigov 能力与路由种子）
2. 后端骨架：模块注册、枚举、实体、Mapper、BO/VO
3. 解析能力：`LocalContentInvoker`（aigov 侧按能力分发）+ Excel/Word/PDF 抽取
4. 预检与闸门：冲突检测、闸门判定、卡片生成
5. 开工包
6. Controller 与权限
7. 前端五页
8. 冒烟脚本 + 全量验证

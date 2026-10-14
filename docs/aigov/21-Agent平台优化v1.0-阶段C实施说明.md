# 21 - Agent 平台优化 v1.0 · 阶段 C 实施说明（知识中心数据模型与检索端口）

> 依据：附件《Agent 平台优化详细设计 v1.0》§6.4（知识中心）、§7.4（知识检索与引用）、§8.2（建议新增对象）
> 分支：`feature/aigov-optimization-v1`
> 阶段 A/B 见 `19-…阶段A实施说明.md`、`20-…阶段B实施说明.md`。

---

## 1 本轮落地

### 1.1 数据模型（`script/sql/aig_knowledge.sql`）

五张表，逐条对齐设计 §8.2：

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| `aig_knowledge_collection` | 授权与责任的最小单位 | `collection_code` 唯一；`data_level` 默认 `STRICT` |
| `aig_knowledge_document` | 稳定的文档身份 | `(collection_id, status)` 索引 |
| `aig_knowledge_document_version` | 不可变原文 | `(document_id, version)` 唯一；`sha256` 索引（重复提示） |
| `aig_knowledge_processing_job` | 处理阶段与幂等 | `(document_version_id, idempotency_key)` 唯一 |
| `aig_knowledge_chunk` | 分段 | `(document_version_id, ordinal)` 唯一 |

三条与既有约定一致的纪律：**正文不落库**（只存 `text_ref` + 受限长度的 `text_excerpt`）、
**版本不可变**（失效用新版本 + 旧版本退役表达）、**处理状态显式**
（`UPLOADED → SCANNING → PARSING → CHUNKING → INDEXING → READY`，失败 `FAILED`，退役 `RETIRED`）。

### 1.2 服务层（`org.dromara.aigov.knowledge`）

- `AigKnowledgeStatusEnum`：把管道顺序写进 `next()`——调用方无法跳步；`FAILED` 重试从 `SCANNING`
  重新开始（不跳过安全检查）。
- `IAigKnowledgeService` / `AigKnowledgeServiceImpl`：
  - 集合：编码唯一校验、数据等级默认 `STRICT`、非法等级拒绝；
  - 文档登记：按扩展名识别来源类型（**XLSX 明确拒绝**——设计 §6.4 要求结构化导入）；
    同哈希**只提示不拒绝**（不同业务范围不共享授权）；
  - 处理管道：`advanceProcessing` / `failProcessing` / `retire`，
    **幂等键去重**（重试不重复索引）+ **CAS 状态更新**（并发冲突报错而非静默覆盖）；
    `READY` 是终态，不被一次失败改写。
- `KnowledgePort` + `AigKnowledgeRetrievalService`：
  - **授权前过滤**（集合/版本/`READY`/有效期）→ **授权后校验**（片段所属版本复检），两道都有；
  - 首期只做**关键字检索**（`text_excerpt` 包含匹配 + 段序），**不宣称向量/嵌入可用**
    ——设计 §7.4 明确嵌入与排序参数需按试点资料评测后确定；
  - 没有命中时返回 `noSupport=true` + 原因，而不是空列表让人猜。
- `AigKnowledgeAccess`：检索授权的**纯函数判据**（可逐条单测）。

---

## 2 本轮**不做**的事（明确的边界）

1. **HTTP 接口与权限点**：本轮不新增控制器。原因：本仓有 `AigPermissionSeedCoverageTest`
   强制"每个 `@SaCheckPermission` 权限串必须在菜单种子 SQL 里存在"，因此接口必须与
   菜单/角色授权种子一起交付。这部分随下一增量一并给出（集合/文档/版本/检索测试四组端点）。
2. **解析器 / OCR / 表格解析**：仓储层不实现解析算法，只承载"处理阶段"与结论；
   真实 PDF/DOCX 解析需接入解析引擎并按实际资料评估（设计 §6.4 明确"效果需评估"）。
3. **嵌入与向量存储**：未选型、未接入。
4. **接入执行计划**：`AigExecutionPlan.knowledgeRefs` 已在阶段 A 冻结，但执行路径尚未调用
   `KnowledgePort`（下一增量接线：授权集合 → 检索 → 引用进 Prompt，并做引用 ID 合法集合核验）。

---

## 3 验证

- 新增测试 22 条：
  - `AigKnowledgeAccessTest`（5）：状态/有效期闭区间/集合授权/版本限制；
  - `AigKnowledgeServiceImplTest`（13）：集合唯一与默认严格级、格式识别、重复提示不拒绝、
    版本号冲突、管道推进与幂等、终态保护、CAS 冲突；
  - `AigKnowledgeRetrievalServiceTest`（4）：已授权片段返回可核验引用、非就绪不返回、空授权与空查询明说。
- 后端 `ruoyi-ai-gov` 全量：**1107 tests / 0 failures / 0 errors**（阶段 B 后 1085 + 本轮 22）。

---

## 4 部署注意

- 需在测试环境执行 `script/sql/aig_knowledge.sql`（五张新表）。
- 本轮**不新增接口、不新增权限点**，对既有生产行为零影响。

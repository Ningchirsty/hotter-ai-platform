# 24 - Agent 平台优化 v1.0 · 总体实施与评审说明

> 依据：附件《Agent 平台优化详细设计 v1.0》（2026-10-14）
> 基线提交：`fe305fc629e9990661097a7a3be86da1fc4714e3`（附件 §1.1 指定；即拉取时的远端 `main`）
> 工作分支：`feature/aigov-optimization-v1`
> 分阶段说明：`19`（A）、`20`（B）、`21`（C）、`22`（D）、`23`（E）

---

## 1 提交序列（基线之后）

| 提交 | 阶段 | 内容 |
| --- | --- | --- |
| `f78b648b` | A | D01 执行解析器/不可变计划；D02 有效外发；D03 绑定撤销与有效期；D04 失败收口；D08 短事务；D10 助手独立能力 |
| `58dbd0c2` | B | D07 分级预检；D09 统一编译器；`GET /aigov/studio/metadata` |
| `191838fc` | B | D06 未保存保护（三选一） |
| `cb8de099` | B | D11 启动任务模板（员工只填业务字段） |
| `2fc245f6` | C | 知识五表 + 处理管道 + `KnowledgePort` |
| `f2109509` | C | 知识接口与权限点 |
| `01388680` | C | 知识接入执行计划（检索冻结 + 资料节） |
| `8d5f99a2` | D | 工具注册/执行/人工确认（D05） |
| `44430c38` | D | 工具接口与权限 |
| `d33c2d69` | E | 反馈账本/队列规则 + 运营指标口径（D12） |
| `8ebde509` | E | 反馈接口与权限 |

---

## 2 D01–D12 对照

状态口径：✅ 已实现并有测试；🔶 部分实现（边界见各阶段文档）；⏳ 未开始。

| 编号 | 设计问题 | 状态 | 落点与证据 |
| --- | --- | --- | --- |
| D01 | 执行只透传版本 ID，Prompt 由调用方给 | ✅ | `execution/`（`AigExecutionPlan`/`AigAgentExecutionResolver`）；`AigAgentExecutionResolverTest`；存量版本不受影响（只认 `schemaVersion>=2`） |
| D02 | 草稿 `allowExternal` 不是网关硬约束 | ✅ | `AigInvokeBo`/`AigRouteHint`/`AigRouteServiceImpl`；`AigRouteServiceExternalConstraintTest` |
| D03 | 绑定只看 enabled、缺撤销入口 | ✅ | `countActiveBindings` 统一有效期口径；`assertExecutionAuthorized`；`POST /aigov/agent/binding/{id}/revoke`；`AigAgentBindingLifecycleTest` |
| D04 | 执行态内异常直接抛出、等超时清扫 | ✅ | `AigTaskExecutorImpl#settleFailure` 单一收口；`AigTaskExecutorImplTest`（缺能力编码、ServiceException） |
| D05 | 工具/知识可绑定但无执行引擎 | 🔶 | 工具注册+执行+确认（`tool/`）+ **子 Agent 顺序编排与校验**（`AigSubAgentOrchestration`）已完成；**顺序流程节点（条件/人工确认）未做** |
| D06 | 采纳提案/关闭编辑器丢未保存内容 | ✅ | `dirtyGuard.ts` + `dirtyGuard.spec.ts`；`index.vue`/`ProposalPanel.vue` |
| D07 | 预检只查结构、类别在提交阶段查 | ✅ | `AigStudioPrecheck` 分级（blockers/warnings + fieldPath）；`AigStudioDraftServiceImplTest` |
| D08 | 网络调用包在长事务里 | ✅ | `runTest`/`chat` 去掉方法级 `@Transactional`；`AigStudioShortTransactionGuardTest` + 更新的 `AigStudioProposalTransactionBoundaryTest` |
| D09 | 存在两份分节拼装 | ✅ | 提交改调 `AigStudioPromptBuilder.build`，删除第二份实现 |
| D10 | Copilot 复用被训练 Agent 的能力 | ✅ | `aigov.studio.copilot.capability`；`AigStudioCopilotServiceImplTest` |
| D11 | 员工需填实现概念（任务类型/业务域/数据等级/原始 JSON） | ✅ | `aig_launch_template` + `AigLaunchTemplateServiceImpl`；`GET /aigov/portal/launch/template`；前端业务表单 |
| D12 | 缺业务反馈与有效成果口径 | 🔶 | 反馈账本+队列规则+接口、运营指标（口径与聚合，含**故障分类**）已完成（`feedback/`）；**资产索引增量同步未做** |

---

## 3 阶段完成情况

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| A 基线一致性 | ✅ | D01/D02/D03/D04/D08（+D10）全部落地并有正反例测试 |
| B 训练体验 | ✅（界面重构除外） | D06/D07/D09/D10/D11 + 元数据；**训练台三栏线框布局未做**（属界面重构） |
| C 知识试点 | 🔶 | 数据模型/处理管道/检索端口/接口/接入执行计划完成；**解析器与嵌入未接入、知识中心页面未做** |
| D 工具与流程 | 🔶 | 工具注册/执行/确认/接口完成；**子 Agent 编排与顺序流程未做** |
| E 持续运营 | 🔶 | 反馈账本/队列/接口 + 指标口径完成；**聚合查询、资产索引增量同步未做** |

---

## 4 验证汇总

- 后端 `ruoyi-ai-gov`：**1158 tests / 0 failures / 0 errors**（基线 1053 → 新增 105）。
- 前端 `frontend`：**45 files / 442 tests** 全过；`oxlint src` 526 files 0/0。
- 关键守卫全部通过：`AigPermissionSeedCoverageTest`（权限串已种子 + menu_id 不撞）、
  `AigRepeatSubmitCoverageTest`（写接口防重或显式豁免）、`AigWp3WiringAndRoutesTest`。

复现命令：

```bash
# 后端（根 pom 默认 maven.test.skip=true，必须显式关闭）
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false
# 前端
cd frontend && ./node_modules/.bin/vitest run && ./node_modules/.bin/oxlint src
```

---

## 5 部署清单（本轮新增）

**建表脚本**（全部幂等，`create table if not exists`）：

| 脚本 | 内容 |
| --- | --- |
| `script/sql/aig_knowledge.sql` | 知识集合/文档/版本/处理任务/分段（5 张） |
| `script/sql/aig_tool.sql` | 工具定义/版本/调用账本（3 张） |
| `script/sql/aig_result_feedback.sql` | 结果反馈（1 张） |
| `script/sql/aig_launch_template.sql` | 启动任务模板（1 张） |

**权限种子**（幂等，只授 `aig_admin`）：

| 脚本 | 权限点 |
| --- | --- |
| `script/sql/aig_knowledge_menu.sql` | `aig:knowledge:*`（6） |
| `script/sql/aig_tool_menu.sql` | `aig:tool:list` / `aig:tool:manage` |
| `script/sql/aig_feedback_menu.sql` | `aig:feedback:queue` / `aig:feedback:handle` |

> 设计 §4.3 的「业务训练负责人」「知识维护人」等业务角色应由运维按需新建并单独授权；
> 本仓当前只有 `aig_admin` / `aig_security` / `aig_viewer`，因此种子只授 `aig_admin`。

---

## 6 剩余工作（诚实的待办，不影响已交付部分可评审）

1. **D05 续**：子 Agent 顺序编排（版本引用、最大深度、超时与预算、禁止循环）。
2. **D12 续**：指标聚合查询与接口（从任务/审计/评测取数）、故障分类指标、资产索引增量同步。
3. **界面**：知识中心页面（集合/文档/版本/检索测试）、工具页面、训练台三栏线框布局、
   反馈问题队列页面。
4. **执行入口收窄**：§7.2 已落地 **有效知识范围 = 版本声明 ∩ 启用的 KNOWLEDGE 绑定**、
   **有效数据等级 = 各来源最高值**、**有效外发 = 各方取与**（`AigEffectivePolicy`）；
   并在计划中**冻结授权上下文**（身份 + 岗位卡片，来自启动记录，`AigAuthorizationContext`）。
   尚未做：让知识/工具执行**读取**该上下文做进一步相交（当前它已冻结、可核验，但下游尚未消费）。
5. **解析器与嵌入**：PDF/DOCX 解析引擎、嵌入模型与向量存储选型（设计明确需按试点资料评测）。

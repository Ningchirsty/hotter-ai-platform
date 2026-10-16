# 24 - Agent 平台优化 v1.0 · 总体实施与评审说明

> 依据：附件《Agent 平台优化详细设计 v1.0》（2026-10-14）
> 基线提交：`fe305fc629e9990661097a7a3be86da1fc4714e3`（附件 §1.1 指定；即拉取时的远端 `main`）
> 工作分支：`feature/aigov-optimization-v1`（A–E，已由 PR #80 以 `aa53daee` **squash 合并进 `main`**，远端分支已删）
> 能力域重构分支：`feature/aigov-capability-redesign`（基于 `aa53daee`；P0 + Q6 + 训练台能力就绪度，见 §8）
> 分阶段说明：`19`（A）、`20`（B）、`21`（C）、`22`（D）、`23`（E）

---

## 1 提交序列

本分支 `feature/aigov-capability-redesign` 基于 `main` 的 `aa53daee`——它就是**前一分支 `feature/aigov-optimization-v1`（A–E 全量）经 PR #80 squash 合并**的结果。

| 提交 | 阶段 | 内容 |
| --- | --- | --- |
| `aa53daee` | 基线 | **PR #80 squash 合并**：A–E 全量（约 82 个提交压成一个；逐条明细见 §1.1） |
| `b7eba85a` | 能力域/设计 | 走读与改造方案三份 + 界面 demo（走读 24 条问题 / 6 条流程与 12 条断点 / P0 字段级规格）+ `治理台-能力域-demo-v3.html` |
| `b6e162d2` | 能力域/P0 后端 | 能力概览聚合 `GET /aigov/capability/overview`（筛选下发服务端）+ 候选编辑 `PUT /aigov/binding/{bindId}`（用途归一化、不接受换模型） |
| `f08c8507` | 能力域/P0 前端 | 能力清单改卡片网格（服务端筛选）+ 详情整页「展开配置详情」+ 试调助手（dryRun 六段式）+ 原始控制台 + 看板 |
| `c54daacf` | 能力域/文档 | 本文档 §8 交付记录 + §4 数字同步 |
| `56563b8b` | 能力域/Q6 | **影响面**：`GET /aigov/capability/references`（Agent 版本 / 历史任务 / 训练台草稿三类引用，一次拿全） |
| `60041e3c` | 能力域/Q6+训练台 | Q6 前端（删除前先查引用再确认、详情「影响面」区块）+ 训练台「所需能力」改能力下拉与就绪度 + 预检补能力事实（不存在/停用＝阻断，缺候选/缺策略＝提醒） |
| `9e1eecd7` | 能力域/Q3 | dryRun 补 `wouldRequireApproval` 与只读 `quotaStatus`（不写审计/不写账本/不消耗额度/只回本人额度）+ 试调助手第 ⑤ 段渲染"预判" |
| `8c0677a7` | 能力域/Q1C+Q6 | **高风险变更轻量门槛**：新增权限点 `aig:capability:high-risk`（只授 aig_admin）+ 规则纯函数/闸门 + 路由策略（新增/编辑/删除）与能力（停用/删除）接线 + 前端确认框（原因 + 权限点 + 影响面） |
| `5b82c9f6` | Q5/门户 | **员工自助**「我的额度 / 我的申请」：门户工具栏 → 弹窗两页签（额度四态含"读不到"；申请含「已批准但已过期」；续期=新单，按 `aig:approval:apply` 决定是否给入口）——**无新增权限点/菜单/路由/建表** |
| `8b0e1d1a` | 能力域/Q2C | **能力声明最低数据等级**：`aig_capability.min_data_level`（ALTER + 重放清单）+ 路由 `max(声明, 调用方)` **只抬不降**（放在路由引擎，所有入口一处生效）+ 生效等级落审计/审批核对 + 「调低/清空」高风险 + **限制级能力的候选变更**需高风险权限（补 Q1C 那半条） |
| `e57c7a41` | P2/交互规范 | **空态 / 长 ID 折叠 / 缺失的 loading**：新增 `AigEmptyState`（三行式空态）与 `AigLongId`（折叠+复制，纯函数有单测），首批接入 tool / knowledge / feedback / evaluation / audit |
| `b576cc1e` | P2/交互规范 | 第二批：剩余主表空态（model / route / agent / quota / approval / binding）+ 长 ID 折叠扫尾（feedback 的 5 个 19 位 ID 列、quota 用户ID、approval 单号、binding 四个 ID 列） |
| `b37045c9` | P2/交互规范 | **危险操作确认审计**（`script/ci/audit-aigov-dangerous-ops.mjs`，评审用不接门禁）+ 补 2 处缺失确认（approval 超时扫描批量改状态、knowledge 解析并索引会覆盖分段与索引） |
| `e9d73d21` | P2/交互规范 | 第三批空态：9 个页面 / 18 张表（artifactDraft / package / portal / resourceBinding / rolePackage / servicetoken / skill / studio / task），`skill` 与 `task` 达到全覆盖 |
| `0c6d9ef3` | P2/分页 | **问题队列分页**：新增 `GET /aigov/feedback/queue/page`（与全量版**共用同一份过滤条件**），前端队列页改分页 + 改筛选回第 1 页；旧全量接口保留 |
| `73d75d54` | P2/交互规范 | 第四批空态：弹窗子表 / 多类表格 15 张（走读 U10 的"20+ 处无空态"收口）；顺带**修掉一个坏按钮**（portal 任务表空态按钮写了不存在的 `getList`，真实 loader 是 `getTasks`） |

> 表里是**有实质内容的提交**；纯文档补记（例如把本表自己补上一行）不逐条登记——它们不改变交付物。
> `check-aigov-docs.mjs` 会核对**本节**每个提交号都是当前分支 HEAD 的祖先（抄错/被 rebase 掉会红）。

### 1.1 已合并分支 `feature/aigov-optimization-v1` 的历史提交（**不在本分支上**）

> ⚠️ 这些提交已被 **PR #80 squash** 进 `main`（= `aa53daee`），远端分支也已删除，因此它们
> **不是**本分支 HEAD 的祖先。明细保留下来只为可追溯；`check-aigov-docs.mjs` 对这一节只核对
> "提交对象在仓库里确实存在"（防抄错），**不再要求**祖先关系——那正是它被单独放进 §1.1 的原因。

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
| `6f403940` | — | 《24-总体实施与评审说明》（本文件） |
| `037f66ad` | E | 运营指标聚合与接口（含故障口径） |
| `41dfa1ab` | D | 子 Agent 顺序编排与校验 |
| `ea5c7fe9` | A/§7.2 | 有效权限逐层相交与有效数据等级 |
| `e748aac9` | E | 故障分类指标 |
| `04933736` | A/§7.1 | 执行计划冻结授权上下文（身份 + 岗位卡片） |
| `bee7494a` | D | 顺序流程节点校验（`AigSequentialFlow`） |
| `3a6b777c` | E/前端 | 反馈与运营页面（`aigov/feedback/index` + 纯函数与单测） |
| `4d8bc675` | C/前端 | 知识中心页面（`aigov/knowledge/index` + 纯函数与单测） |
| `32f22098` | D/前端 | 工具注册页面（`aigov/tool/index` + 纯函数与单测） |
| `d14c7eb4` | D | 任务步骤账本（`aig_task_step`，编排步骤可追溯） |
| `17123281` | B/前端 | 训练台步骤/辅助区口径 + 页头状态条（`studio/layout.ts`） |
| `120c74a5` | D | 任务步骤状态迁移与 CAS（`AigTaskStepTransition`） |
| `95317aba` | D/文档 | 子 Agent 派发规格（`AigSubAgentDispatch`）+ §7 评审指引 |
| `7040fa31` | D | 子 Agent 运行时驱动（创建子任务 + 对账收尾） |
| `5ca6b6ca` | D | 子 Agent 对账作业与定时触发 |
| `2dee4ce4` | C/§7.2 | 授权上下文进入知识检索（身份层组织收窄） |
| `19ebe830` | D/§7.5 | 工具许可命中有效绑定 |
| `2131ed54` | D/§7.5 | 工具许可命中**调用人权限**（`ToolPermissionChecker`，fail-closed） |
| `4a358069` | D | **流程节点运行时驱动**（`AigFlowRuntime` + `IAigFlowDriverService` + 执行器闸门接线） |
| `873cfb8d` | — | 文档：22/24 补记上述两项与状态收敛 |
| `44ed5e2f` | D12 | **资产索引同步被「短页」误判为到底**（每域只同步前 50 条）修复 + 工具页权限点口径 |
| `4c4f88dd` | D12/E | **资产索引水位线增量同步**（`syncNewForUser` + `?mode=incremental`） |
| `cc3420f2` | D | **人工确认的恢复入口**：节点身份落账本（`node_type`/`node_ref`）+ `GET /task/{id}/flow-nodes` + `POST /task/{id}/flow/confirm`；发布工作流的重放清单补入本分支新增的建表/补列脚本 |
| `dd2708dc` | D/前端 | 任务页**流程节点面板与人工确认**（`TaskFlowPanel.vue` + `flow.ts`/`flow.spec.ts`） |
| `e4726ecb` | D | **条件结论取自冻结输入快照的同名布尔字段**（`AigFlowConditions`；取不到结论不猜真假） |
| `71871254` | B/前端 | **训练台三栏布局**（左步骤导航 / 中编辑 / 右修订差异）+ 源码结构与 SFC 编译守卫 |
| `7e48c0c4` | D | **`peek` 只读判定 + 尾部可自行收尾的节点本轮推到完成**（不留"没跑完"的假象；人工确认节点只探不写） |
| `886f00cc` | C/前端 | **分段预览**（只给受限预览 + 正文/索引状态；复用检索测试权限）+ `views/aigov` 全量 **SFC 编译守卫** |
| `eee74bd4` | C | **谁在引用这个集合**（退役前的影响面：`listResourceReferences` + 界面「看引用」；无新权限点） |
| `87d2946a` | D | **工具契约详情**（§6.5 工具卡片字段：用途/输入输出/数据范围/责任人/接入状态；与清单分开，无新权限点） |
| `b27301fc` | E | **问题队列按后续动作/原因过滤** + 版本与处理人可见（动作不再只躺在行上，负责用例的人能挑出自己那份） |
| `032d704e` | B/前端 | **训练台左右两栏拆成子组件**（`StudioStepsColumn` / `StudioRevisionColumn`，右栏只收 props 回抛事件）+ 结构守卫随拆分调整 |
| `aefde3d5` | B/前端 | **员工业务状态**（设计 §6.8：服务端算好下发；执行成功未复核显示「草稿已生成 待复核」） |
| `fad26a64` | —/CI | **验收脚本 `script/ci/verify-aigov-v1.mjs`**（编排既有守卫，一条命令出逐项结论）+ `check-script-setup.mjs` 支持 `--files-from`（Windows 命令行长度上限） |
| `f528f43a` | —/CI | **文档口径检查 `script/ci/check-aigov-docs.mjs`**（类名 / SQL 清单 / 测试数 / 提交号四类漂移，接入验收脚本最后一步） |
| `84e29fff` | B | **三条路径系统指令同源的用例**（设计 §11.2 #4）+ 阶段文档去掉会过期的"当前 N"数字 |
| `6db724f4` | B/前端 | **右栏两页签**（修订差异 / 测试预览）+ 测试结果的**版本哈希与 Schema 结论**（设计 §6.3、§6.6） |
| `e621929e` | D | **顺序流程每步一次调用**（用户 2026-10-14 授权）：`AigFlowCallPolicy`（次数上限 / 链式输入 / 累计成本边界）+ 执行器多步循环 + `route_snapshot.executedCalls` |
| `c8c8b610` | D | **条件结论支持 `a.b.c` 路径与数组下标**（用户 2026-10-14 要求；取不到就「没有结论」，不猜真假） |
| `f709ee28` | C | **知识解析/嵌入的内部可部署基线**（PDFBox 3 + 内置文本/DOCX 解析 + 哈希嵌入 + 文件向量索引，全部走端口）+ `POST /document-versions/{id}/index` 全链路 + 检索测试 `KEYWORD`/`VECTOR` + 前端入口与模式选择 + 方案文档 `25` |
| `1e86662e` | 前端/CI | **修 3 处「只有 `vite build` 才会失败」的问题**（`TaskFlowPanel` 的 `./flow` 应为 `../flow`；`ProposalPanel` 与两个 studio 子组件的多根节点）+ 验收脚本新增**前端生产构建**步骤 + `sfcCompile.spec.ts` 复用构建插件守"单根节点" |

---

## 2 D01–D12 对照

状态口径：✅ 已实现并有测试；🔶 部分实现（边界见各阶段文档）；⏳ 未开始。

| 编号 | 设计问题 | 状态 | 落点与证据 |
| --- | --- | --- | --- |
| D01 | 执行只透传版本 ID，Prompt 由调用方给 | ✅ | `execution/`（`AigExecutionPlan`/`AigAgentExecutionResolver`）；`AigAgentExecutionResolverTest`；存量版本不受影响（只认 `schemaVersion>=2`） |
| D02 | 草稿 `allowExternal` 不是网关硬约束 | ✅ | `AigInvokeBo`/`AigRouteHint`/`AigRouteServiceImpl`；`AigRouteServiceExternalConstraintTest` |
| D03 | 绑定只看 enabled、缺撤销入口 | ✅ | `countActiveBindings` 统一有效期口径；`assertExecutionAuthorized`；`POST /aigov/agent/binding/{id}/revoke`；`AigAgentBindingLifecycleTest` |
| D04 | 执行态内异常直接抛出、等超时清扫 | ✅ | `AigTaskExecutorImpl#settleFailure` 单一收口；`AigTaskExecutorImplTest`（缺能力编码、ServiceException） |
| D05 | 工具/知识可绑定但无执行引擎 | ✅（WarmFlow/LiteFlow 适配器与条件外部求值未做） | 工具注册+执行+确认、子 Agent 顺序编排 + 运行时派发/对账 + 对账定时作业、顺序流程节点校验、任务步骤账本（含状态迁移与 CAS）、**流程节点运行时驱动（闸门接线 + 人工确认的挂起与恢复入口 + 条件结论取自冻结快照 + 尾部节点收尾 + 顺序流程每步一次调用 + 条件结论支持 `a.b.c` 路径/数组下标）** 已完成；**WarmFlow/LiteFlow 适配器、条件的业务侧实时求值未做**（适配点与边界见 `22` §1.17/§1.18） |
| D06 | 采纳提案/关闭编辑器丢未保存内容 | ✅ | `dirtyGuard.ts` + `dirtyGuard.spec.ts`；`index.vue`/`ProposalPanel.vue` |
| D07 | 预检只查结构、类别在提交阶段查 | ✅ | `AigStudioPrecheck` 分级（blockers/warnings + fieldPath）；`AigStudioDraftServiceImplTest` |
| D08 | 网络调用包在长事务里 | ✅ | `runTest`/`chat` 去掉方法级 `@Transactional`；`AigStudioShortTransactionGuardTest` + 更新的 `AigStudioProposalTransactionBoundaryTest` |
| D09 | 存在两份分节拼装 | ✅ | 提交改调 `AigStudioPromptBuilder.build`，删除第二份实现 |
| D10 | Copilot 复用被训练 Agent 的能力 | ✅ | `aigov.studio.copilot.capability`；`AigStudioCopilotServiceImplTest` |
| D11 | 员工需填实现概念（任务类型/业务域/数据等级/原始 JSON） | ✅ | `aig_launch_template` + `AigLaunchTemplateServiceImpl`；`GET /aigov/portal/launch/template`；前端业务表单 |
| D12 | 缺业务反馈与有效成果口径 | ✅ | 反馈账本+队列规则+接口、运营指标（口径与聚合，含**故障分类**）、**资产索引（全量重建的截断缺陷已修 + 水位线增量同步）** 均完成（`feedback/`、`workspace/portal`）；增量口径明确"只补新增，删除/更新靠全量"（`AigAssetIndexServiceImplTest` 10） |

---

## 3 阶段完成情况

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| A 基线一致性 | ✅ | D01/D02/D03/D04/D08（+D10）全部落地并有正反例测试 |
| B 训练体验 | ✅（三栏布局、左右栏拆分、右栏两页签已落地；表单按步骤拆分未做） | D06/D07/D09/D10/D11 + 元数据；**三栏布局**（左步骤 / 中编辑 / 辅助区）已落地，**左右两栏已拆成子组件**并有结构守卫；**右栏页签**（修订差异 / 测试预览，含版本哈希与 Schema 结论）；**员工业务状态**（§6.8）由服务端计算下发；**按步骤拆分表单未做** |
| C 知识试点 | ✅（内部可部署基线已接入；引擎/嵌入的**最终选型**按试点评测确定） | 数据模型/处理管道/检索端口/接口/接入执行计划、**知识中心页面（含分段预览、引用影响面）**、**解析引擎（PDF=PDFBox 3 / DOCX / TXT / Markdown）与嵌入+向量索引的内部可部署基线**、**「上传并解析索引」入口**、**检索测试可取向量模式**均完成；**OCR、对象存储取文、混合排序权重**按设计留给评测/后续接入（方案见 `25`） |
| D 工具与流程 | ✅（WarmFlow/LiteFlow 适配器未做） | 工具注册/执行/确认/接口、**工具许可（版本声明 + 有效绑定 + 调用人权限）与契约详情**、子 Agent 编排/派发/对账、顺序流程节点的校验与**运行时驱动**、**顺序流程每步一次调用**、**条件结论支持 `a.b.c` 路径**完成；仅剩 WarmFlow/LiteFlow 适配器与条件的外部求值（见 §6） |
| E 持续运营 | ✅ | 反馈账本/队列/接口 + **队列按动作/原因过滤** + 指标口径与聚合（含故障分类）+ **资产索引全量/增量同步** |

---

## 4 验证汇总

- 后端 `ruoyi-ai-gov`：**1413 tests / 0 failures / 0 errors**（基线 1053 → 新增 360；其中 **能力域重构 P0 13 条**、
  **Q6 影响面 5 条**、**训练台能力就绪度预检与能力事实查询 14 条**、**Q3 预演闸门 5 条**、**Q1C 高风险门槛 12 条**、
  **Q2C 声明等级/生效等级 11 条**、**问题队列分页 1 条**，见 §8）。
  > 勘误（2026-10-16）：本节此前记的 **1367** 是 **Q6 影响面提交（`56563b8b`）之前**的数，
  > 漏计了 Q6 的 5 条（引用服务 4 + 路由烟测 1）。⇒ 以本节为准
  > （`script/ci/check-aigov-docs.mjs` 是拿本节数字与磁盘报告对账，所以它本来就会把这类漂移报出来）。
  > 各阶段文档（`19`–`23`）里的测试数**是各自增量当时的数**；
  > **以本节为准**——阶段文档只留"本轮新增几条"，不再写会过期的"当前 N"。
  > 另有 `script/ci/check-aigov-docs.mjs` 会**机械核对**本节的测试数与提交号
  > （类名、SQL 脚本清单、测试数、§1 的提交号是否为 HEAD 祖先、§1.1 历史提交对象是否存在）。
- **整棵反应堆**（`--full-reactor`，在线，实测 **BUILD SUCCESS / 219s**）：本分支没有把别的模块带坏；
  它同时会跑"每个有测试的模块都必须产出 Surefire 报告"的台账守卫。
  ⚠️ 该模式需要 CI 前置步骤装好的 **ffmpeg/ffprobe**（缺了会是 `ruoyi-ai` 的 4 个媒体用例报
  "图片格式转换服务不可用"——环境前提，不是本分支的回归；见 §7.2）。
- 前端 `frontend`：**57 files / 572 tests** 全过；`oxlint src` 572 files 0/0；
  **生产构建 `vite build --mode production` 通过**（与 CI 的 `pnpm build:prod` 同一条命令）。
  其中 `views/aigov/sfcCompile.spec.ts` 真编译全部 34 个治理层 SFC
  （无 DOM 环境下的最低保证：模板/`<script setup>` 写错会在这里红），
  并用**构建插件本身**扫 `src/views/**` 全部页面，守住"根节点只能有一个"（见 §7.2 的说明）。
- 关键守卫全部通过：`AigPermissionSeedCoverageTest`（权限串已种子 + menu_id 不撞）、
  `AigRepeatSubmitCoverageTest`（写接口防重或显式豁免）、`AigWp3WiringAndRoutesTest`。
- **本轮由 CI 首次跑通整条流水线，抓到 3 处"只有 `vite build` 才会失败"的问题**：
  `TaskFlowPanel.vue` 的相对 import 写成 `./flow`（实际在 `../flow`）、
  `ProposalPanel.vue` 与两个 studio 子组件存在**根级注释/第二个根节点**（`<transition>` 只接受单根）。
  它们在本地的单测 + lint + 全部守卫里**零信号**（vitest 只加载测试引用到的文件），
  是"把这个分支推上去让 CI 跑"之后才暴露的。⇒ 已把 `vite build` 加成验收脚本的一步，
  并把"单根节点"判定接进 `sfcCompile.spec.ts`（复用构建插件、口径同源）。

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
| `script/sql/aig_task_step.sql` | 任务步骤账本（1 张；含流程节点身份 `node_type`/`node_ref`） |

**补列迁移（存量库用；`create table if not exists` 不会给已存在的表加列）**：

| 脚本 | 内容 |
| --- | --- |
| `script/sql/aig_task_step_node_identity.sql` | 给已有步骤账本补 `node_type`/`node_ref`（information_schema + PREPARE，幂等） |
| `script/sql/aig_capability_min_data_level.sql` | 给 `aig_capability` 补 `min_data_level`（能力声明的最低数据等级，Q2C；**NULL = 不声明**，存量能力行为不变） |

> **这两类脚本已加进 `.github/workflows/deploy-poc.yml` 的 `Prepare additive runtime schema` 重放清单**，
> 由构建期守卫 `AigDeploySchemaReplayCoverageTest` 把关：**只写在文档里的"部署清单"没人会在切镜像前
> 照着执行**，漏掉的后果是"部署成功但接口 500"，而 RuoYi 把 SQL 异常掩成「发生未知异常」。
> 权限/页面种子（`aig_*_menu.sql` / `aig_*_pages.sql`）**不在自动重放之列**——它们插的是
> `sys_menu`/`sys_role_menu`，属授权变更，应由运维按本清单显式执行。

**知识处理链路（本轮新增；`21` §5 有明细）**：

- **无新增建表、无新增权限点**：`/document-versions/{id}/index` 复用 `aig:knowledge:document:manage`
  与既有表；向量也不落业务表（只写 `aig_knowledge_chunk.index_ref` 键）。
- **新增 Maven 依赖**：`org.apache.pdfbox:pdfbox:3.0.7`（Apache-2.0、纯 Java、离线）——PDF 解析引擎；
  多一个 jar，**部署形态不变、无外发通道**。理由与替代方案见 `25` §2.2。
- **新增配置**（`aigov.knowledge.pipeline.*`，全部有默认值，不配也能跑）：
  `parser-engine-id`（空=内置默认；评测后切换引擎用）、`chunk-target-chars`(800)、
  `chunk-overlap-chars`(100)、`max-chunks-per-version`(2000)、`embedding-dimension`(256)、
  `vector-index-dir`（空=`${java.io.tmpdir}/aigov-knowledge-vector-index`）、`excerpt-max-chars`(1000)、
  `vector-search-enabled`(true)。
  ⚠️ **多副本部署前必须替换向量索引实现**：默认文件索引是单节点的（`25` §4）。
- **运维注意**：`parser-engine-id` 指定了不存在/不支持该类型的引擎会**显式报错**（不静默退回默认），
  所以切换引擎时请先用 `25` §3.3 的清单确认引擎 ID。

**权限种子**（幂等，只授 `aig_admin`）：

| 脚本 | 权限点 |
| --- | --- |
| `script/sql/aig_knowledge_menu.sql` | `aig:knowledge:*`（6） |
| `script/sql/aig_tool_menu.sql` | `aig:tool:list` / `aig:tool:manage` |
| `script/sql/aig_feedback_menu.sql` | `aig:feedback:queue` / `aig:feedback:handle` |
| `script/sql/aig_ops_menu.sql` | `aig:ops:metrics` |
| `script/sql/aig_capability_high_risk_menu.sql` | `aig:capability:high-risk`（**Q1C 新增**；只授 `aig_admin`——安全角色默认不给，要让它也能改由运维显式授权） |

> 设计 §4.3 的「业务训练负责人」「知识维护人」等业务角色应由运维按需新建并单独授权；
> 本仓当前只有 `aig_admin` / `aig_security` / `aig_viewer`，因此种子只授 `aig_admin`。

---

## 6 剩余工作（诚实的待办，不影响已交付部分可评审）

1. **D05 续**：**只剩两项**——
   ①**条件结论的外部求值**（业务域回调 / 表达式引擎）：快照内的 `a.b.c` 路径与数组下标
   **已落地**（`AigFlowConditions`，见 `22` §1.18），仍不做的是"由谁来算这个条件"这类口径变更；
   ②WarmFlow/LiteFlow 适配器（适配点已明确：`AigFlowRuntime` 的判定输入是
   "节点顺序 + 步骤状态 + 条件结论"）。
   **已闭环的部分**：**顺序流程每步一次调用**（用户 2026-10-14 授权；次数上限 = 声明步数 ∩
   `limits.maxFlowSteps` ∩ 平台默认 10，第 k 步以上一步输出为输入，见 `22` §1.17）；
   人工确认的"挂起—恢复"（节点身份落账本 + `GET /task/{id}/flow-nodes` +
   `POST /task/{id}/flow/confirm`，见 `22` §1.16；操作序列"读 → 确认 → requeue"，确认刻意不自动重跑）；
   条件结论来源（`AigFlowConditions`：顶层字段 / `a.b.c` 路径 / 数组下标，取不到结论不猜真假）；
   尾部可自行收尾的节点（`peek` 只读判定 → 输出节点/已定条件在本轮推到完成，
   **不留下没有任务态支撑的假挂起**）；
   子 Agent 的编排校验、派发、对账与对账定时作业（`IAigSubAgentRunner` /
   `IAigSubAgentReconcileService`）。
2. **D12 已完成**：资产索引**增量同步**（水位线 `syncNewForUser`，`?mode=incremental`）已落地，
   口径边界写在 `23` 与 `10`：**只补新增**，删除/更新靠定期全量重建（三个业务域都没有变更事件）。
3. **界面**：反馈与运营页、知识中心页（**新增「上传并解析索引」入口与检索模式选择**）、
   工具注册页、**任务页的流程节点与人工确认面板**
   （`TaskFlowPanel.vue`）已交付；训练台**三栏布局**已落地（左步骤 / 中编辑 / 辅助区），
   **左右两栏已拆成子组件**，**右栏两页签**（修订差异 / 测试预览）含版本哈希与 Schema 结论；
   **仍待做**：把变更提案面板塞进右栏当第三个页签（现在刻意保留宽版在下方，
   塞进 7/24 右栏会挤到没法审阅）、左侧步骤驱动中部编辑（表单按步骤拆分）；
   **布局观感需人眼验收**（前端无 DOM 测试环境，守卫只证明结构与编译）。
4. **执行入口收窄**：§7.2 已落地 **有效知识范围 = 版本声明 ∩ 启用的 KNOWLEDGE 绑定**、
   **有效数据等级 = 各来源最高值**、**有效外发 = 各方取与**（`AigEffectivePolicy`）；
   计划中**冻结授权上下文**并被**知识检索消费**（按 `orgScope` 身份层收窄）；
   工具侧已落地 **命中有效绑定 + 命中调用人权限**（`AigToolExecutionPolicy.bindingsPermit`
   / `permissionPermit` + `ToolPermissionChecker`）。
   **仍未做**：机器身份（服务令牌 scope）作为工具调用人的授权路径——当前无登录态一律按无权限处理。
5. **知识处理：基线已接入，最终选型待评测结论**（方案与记录表见
   `25-知识处理引擎与嵌入-内部可部署基线与试点评测方案.md`）。
   **已交付**：PDF/DOCX/TXT/Markdown 解析（`pdfbox-3` / `builtin-*`）、内置哈希嵌入、
   单节点文件向量索引、`POST /document-versions/{id}/index` 全链路、检索测试的
   `KEYWORD`/`VECTOR` 两路对比。
   **仍待（都不是"分析不出来"，而是"需要评测或外部条件"）**：
   ①**评测本身**（≥50 条查询 + 10 条无答案查询，判据与记录表见 `25` §3）；
   ②**OCR**（扫描件按设计失败并指向 OCR，是否接入由评测里那份扫描件决定）；
   ③**对象存储取文**（现在由调用方上传文件；按 `sourceRef` 读属于后续接入）；
   ④**混合排序权重**（评测结论的产物，不预先拍）；
   ⑤**多副本向量存储**（默认文件索引是单节点的，多副本上线前必须替换）。
   > **分段预览已交付**（`GET /document-versions/{id}/chunks` + 界面「看分段」）：
   > 维护人现在就能判断"解析出来的分段靠不靠谱"。

---

## 7 如何评审（约 30 分钟）

### 7.1 先看结论（5 分钟）

1. 本文 §2 的 D01–D12 对照表：**哪些 ✅、哪些还有明确列出的边界**；
   前端部分注意：**训练台三栏布局的观感只能靠人眼**——本仓无 DOM 测试环境，
   守卫（`threeColumnLayout.spec.ts`）只证明结构在、SFC 能编译；
2. §3 阶段完成情况：A/B/C/D/E 的剩余子项都被逐条写明；
3. §6 剩余工作：每条都写明了缺什么，以及**为什么**（外部条件、评测结论、或人会拍板的取舍）。

### 7.2 跑一遍（10 分钟）

**一条命令**（推荐；它会逐项打印结论，失败时退出码为 1）：

```bash
node script/ci/verify-aigov-v1.mjs            # 后端治理模块 + 前端全量 + 4 条守卫，约 1 分钟
node script/ci/verify-aigov-v1.mjs --offline  # 依赖已缓存时加 -o 更快
node script/ci/verify-aigov-v1.mjs --full-reactor   # 与 CI 同口径跑整棵反应堆（约 4 分钟，需媒体工具）
```

它把下面这些命令**固化**在一起，并顺手挡住三个"看起来通过"的坑：
根 `pom.xml` 默认 `maven.test.skip=true`（不显式关掉 = 0 测试但 BUILD SUCCESS）、
前端必须跑 `vitest` **全量**（用 preview 配置只覆盖少数 spec）、
`check-script-setup.mjs` 必须拿到**全部** `.vue`（少传就是假通过）。
守卫脚本本身是仓里既有的两个（`check-test-results.py` / `check-frontend-tests.mjs`），
本脚本只做编排、不另写判定。

> **它现在还会跑一次前端生产构建（`vite build`，与 CI 的 `pnpm build:prod` 同一条命令）**。
> 这是本轮后补的一步，原因值得评审人知道：`vitest` 只加载"测试引用到的文件"，
> 而 `sfcCompile.spec.ts` 只编译 SFC 自身、不解析它的 `import` ⇒ **断掉的相对 import
> 与模板多根节点这两类问题，在"单测 + lint + 全部守卫全绿"时依然存在**。
> 本分支实测被 CI 的前端镜像构建抓到 3 处（`TaskFlowPanel` 的 `./flow` 应为 `../flow`；
> `ProposalPanel` 的 `el-card`+`el-dialog` 双根；两个 studio 子组件的根级注释）——
> 现在其中"单根节点"这条又额外在 `sfcCompile.spec.ts` 里以**同一个构建插件**跑了一遍，
> 本地几十秒就能红（口径与构建完全一致，不复制判定逻辑）。

最后一步是 **`check-aigov-docs.mjs`（文档口径检查）**：核对文档里写的
①类名（54 个 `Aig*` 名字都能在源码里找到）、②SQL 脚本清单（含 `ALTER` 的都在发布工作流的
重放清单里）、③**测试数**（与本轮刚跑出来的 Surefire 报告 / vitest JSON 一致）。
它防的是"文档漂移"——代码改名、脚本漏清单、测试数前移，都不会让任何测试变红。

手动等价命令（想单跑某一步时用）：

```bash
# 后端（注意根 pom 默认 maven.test.skip=true，必须显式关闭）
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false
# 前端
cd frontend && ./node_modules/.bin/vitest run && ./node_modules/.bin/oxlint src \
  && ./node_modules/.bin/vite build --mode production
```

> ⚠️ `--full-reactor` 的前提与 CI 相同：**ffmpeg/ffprobe 等媒体工具要在 PATH 上**
> （CI 在跑测试前有一步 "verified-media-tools"）。缺少它们时 `ruoyi-modules/ruoyi-ai`
> 会有 4 个媒体用例报"图片格式转换服务不可用"——那是环境前提缺失，**不是本分支的回归**；
> 脚本开跑前会探测并提示。此外它先 `clean`：台账守卫读的是磁盘上**所有** Surefire 报告，
> 不清干净会把上一次用别的 profile 跑出来的旧失败算进这次。

关键守卫（即使只跑这三条也能证明"接口不会对所有人 403 / 写接口有防重 / 路由装配正常"）：

```bash
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false \
  -Dtest=AigPermissionSeedCoverageTest,AigRepeatSubmitCoverageTest,AigWp3WiringAndRoutesTest
```

### 7.3 抽点看代码（10 分钟）

| 想验证什么 | 看哪里 |
| --- | --- |
| 执行是否真的按冻结版本而非调用方 Prompt | `execution/AigAgentExecutionResolver`、`task/.../AigTaskExecutorImpl#runOnceRunning` |
| 授权是否逐层相交、等级取最高 | `execution/AigEffectivePolicy`（+ `AigEffectivePolicyTest`） |
| 知识检索是否两道授权 | `knowledge/retrieval/AigKnowledgeRetrievalService`、`AigKnowledgeAccess` |
| 写工具是否必须先确认、幂等 | `tool/exec/AigToolExecutionPolicy`（含 `permissionPermit`）、`tool/exec/impl/SaTokenToolPermissionChecker`、`tool/.../AigToolRegistryServiceImpl#execute` |
| 条件判断/人工确认是否真拦得住 | `task/service/AigFlowRuntime`、`task/.../AigFlowDriverServiceImpl`、`AigTaskExecutorImpl#checkFlowGate` |
| 人工确认能否被"恢复"、批准会不会被覆盖 | `task/controller/AigTaskController#flowNodes/#confirmFlowNode`、`task/domain/vo/AigTaskFlowNodeVo#isAwaitingConfirmation` |
| 条件判断的结论从哪来 | `task/service/AigFlowConditions`（顶层布尔字段 + `a.b.c` 路径 + 数组下标；取不到结论不猜）+ `AigTaskExecutorImpl#checkFlowGate` |
| 一次执行会发几次调用、每步输入是什么 | `task/service/AigFlowCallPolicy`（声明步数 ∩ `maxFlowSteps` ∩ 平台默认 10；第 k 步以上一步输出为输入；**累计成本不可控已写明**） |
| 尾部节点为什么能自己收尾、又为什么不会假挂起 | `IAigFlowDriverService#peek`（只读）+ `AigTaskExecutorImpl#drainSettleableFlowTail` |
| 解析引擎怎么选、取不到文本会怎样 | `knowledge/pipeline/AigKnowledgeParseRegistry`（指定引擎不存在/不支持 → 报错不回退）+ `AigPdfParseEngine`（扫描件显式失败并指向 OCR） |
| 嵌入基线是什么、为什么不能当"效果达标" | `knowledge/pipeline/AigHashedNgramEmbedding`（类注释写明三条已知弱点）+ `25` §2/§4 |
| 分段为什么不会"只索引前 N 段" | `knowledge/pipeline/AigKnowledgeChunker#guardLimit`（超限直接失败） |
| 处理失败为什么"写库 + 抛错"两件事都做 | `knowledge/service/impl/AigKnowledgeIndexingServiceImpl#index`（干完一步才记成功；不加外层事务的理由在类注释） |
| 想对比关键字与向量两路 | `knowledge/retrieval/AigKnowledgeRetrievalService#retrieveByVector`（落点回库复查授权）+ `POST /retrieval-tests` 的 `mode` |
| 向量索引能不能多副本 | `knowledge/pipeline/AigFileVectorIndex` 类注释（单节点边界）+ `25` §4 |
| 新增的库表/列会不会在部署时被漏掉 | `.github/workflows/deploy-poc.yml` 的重放清单 + `AigDeploySchemaReplayCoverageTest` |
| 反馈是否会直接改线上 | `feedback/.../AigResultFeedbackServiceImpl`（动作只进改进流程） |
| 训练台是否会静默丢内容 | `frontend/src/views/aigov/studio/dirtyGuard.ts` |
| 训练台三栏是否还在 | `frontend/src/views/aigov/studio/threeColumnLayout.spec.ts`（源码结构 + SFC 真编译；**观感需人眼**） |
| 测试/提交/正式执行是否同一份系统指令 | `AigStudioTestServiceImplTest#systemPromptIsIdenticalAcrossTestSubmitAndProductionPaths`（设计 §11.2 #4） |
| 测试结果里的版本哈希与 Schema 结论 | `frontend/src/views/aigov/studio/testPreview.ts`（`hashShort`/`schemaVerdictText`；**未判定 ≠ 不通过**） |
| 治理层页面模板有没有被写坏 | `frontend/src/views/aigov/sfcCompile.spec.ts`（全部 30 个 SFC 真编译 + **用构建插件扫 `src/views/**` 的单根节点规则**） |
| 前端改动会不会在"构建期"才炸（断 import / 多根节点） | `script/ci/verify-aigov-v1.mjs` 的**前端生产构建**步骤 + `frontend/vite/plugins/check-transition.ts`（单测与它同源，见 §7.2） |
| 分段预览会不会顺手把原文吐出来 | `AigKnowledgeChunkVo`（无正文字段）+ `AigKnowledgeServiceImpl#listChunks`（只取 `text_excerpt`） |
| 退役一个知识版本会影响谁 | `AigKnowledgeController#references` → `AigAgentRegistryQueryServiceImpl#listResourceReferences`（解析 Agent 身份；停用中的也返回） |
| 一个工具收什么、回什么、超时多久 | `AigToolController#versionDetail` → `AigToolDetailVo`（定义 + 版本两处来源；未接入也显示执行器引用名） |
| 员工看到的是不是业务状态 | `workspace/portal/helper/AigPortalTaskStatus`（执行成功未复核 → 「草稿已生成 待复核」；认不出回落服务端文案） |
| 某个动作（如"加入用例"）的问题挑不挑得出来 | `AigResultFeedbackController#queue`（verdict/actionCode/reasonCode 三个独立过滤；拼错编码报错不回空队列） |
| 指标口径是否可复核 | `feedback/metrics/AigOperationsMetrics`（累计成果=经确认结果；分母 0 → 0） |

### 7.4 部署前必做（5 分钟）

先跑一遍 **`node script/ci/verify-aigov-v1.mjs`**（全绿再往下走），再按 §5 清单依次执行：
**5 个建表脚本 + 1 个补列脚本 + 4 个权限种子 + 3 个页面行脚本**。
注意：**建表/补列脚本已经在发布工作流的重放清单里**（切镜像前自动跑，由
`AigDeploySchemaReplayCoverageTest` 把关）；权限种子只授 `aig_admin`，设计 §4.3 的业务角色
应由运维按需新建并单独授权；页面行脚本登记的是草稿/反馈/知识/工具页面菜单。

---

## 8 能力域重构 P0 + Q6 影响面 + 训练台能力就绪度（2026-10-15/16，**已提交并推送**）

> 背景：平台发布到生产后反馈"能力这块很乱"。走读（后端 / 数据权限菜单 / 前端三路）后确认：
> **界面乱的根源是流程没有单一入口**。设计与决策见 `docs/aigov/design/` 下三份文档：
> `能力域-现状走读与重构方案.md`（现状 + 24 条问题）、`能力域-业务流程与界面联动改造方案.md`
> （6 条流程 + 12 条流程断点 + 已拍板的 6 个业务口径）、`P0-能力域-字段级规格与改动清单.md`（施工图 + 26 条回归用例）；
> 界面提案为 `治理台-能力域-demo-v3.html`（按 DinTal 真实截图重做，含 v1/v2 对比稿）。

**P0 交付（不改表、不加权限点、不新增菜单行）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 后端 | 能力概览聚合（卡片/看板的唯一数据源；筛选下发服务端） | `AigCapabilityOverviewVo`、`IAigCapabilityOverviewService`(+Impl)、`AigCapabilityController#overview`、`GET /aigov/capability/overview` |
| 后端 | 候选"编辑用途/优先级"（此前只能删了重建；且用途会归一化成规范大写，避免库里小写导致路由把它排到最低权重） | `IAigModelBindingService#update`、`AigModelBindingServiceImpl#update`、`PUT /aigov/binding/{bindId}` |
| 前端 | 能力清单：卡片网格 + **服务端筛选**（问题码 / 候选有无 / 状态）+ 创建卡 + 空态 + 卡片进详情 | `views/aigov/capability/index.vue`、`capability/presentation.ts` |
| 前端 | 能力详情**整页** + 「展开配置详情」5 页签（基本信息/模型候选/策略/场景指定/最近调用）+ 人设分节 | `capability/components/CapabilityDetail.vue` |
| 前端 | **试调助手**（右侧常驻对话，用户已确认）：4 类固定问题 + 假设参数（数据等级/禁止外发/场景）× dryRun 六段式 | `capability/components/TrialAssistant.vue` |
| 前端 | 深色「原始与日志」控制台；能力看板（4 指标 + 调用/失败排行 + 表格视图，**不画按天趋势**——避免用分页数据拼出错的曲线） | `capability/components/RawConsole.vue`、`CapabilityBoard.vue` |
| 前端 API | 新增 `capability/overview`、`candidate`（= 能力×模型，路径独立于 Agent 版本绑定的 `binding`）、`invoke`（dryRun） | `api/aigov/capability/overview(-types).ts`、`api/aigov/candidate/index.ts`、`api/aigov/invoke/index.ts` |

**实测结论（决定了 P0 的成本）**：`dryRun` 已经下发 `reasonCode` + `policyHits`（含"哪些候选因何被排除"），
所以**试调助手没动一行后端**；P0 后端只加了 1 个只读聚合 + 1 个候选编辑。
另：`dryRun` **不校验调用授权与配额**（那两道只在真实调用时拦），界面必须写清"试调通过 ≠ 能调"。

**Q6「影响面」（已拍板：改策略/停用/删除前强制先看谁在用它）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 后端 | 一次请求拿全三类引用：**Agent 版本**（`provider_capability` 与 `config_json` **两处都查**、按版本去重、已发布显式标出）/ **历史任务**（只回计数）/ **训练台草稿**（只回计数）；权限沿用 `capability:list`（**无新权限点**） | `AigCapabilityReferenceVo`、`IAigCapabilityReferenceService`(+Impl)、`AigCapabilityController#references`、`GET /aigov/capability/references?capabilityCode=` |
| 前端 | 删除前**先查影响面再弹确认框**（查不到引用就**不静默继续**，也不假装"可以安全删"）；确认框逐条列出引用方（>5 条折叠）并给出"停用可恢复"的替代 | `api/aigov/capability/references.ts`、`capability/presentation.ts#deleteConfirmText`、`capability/index.vue#remove` |
| 前端 | 能力详情「基本信息」页签新增 **影响面区块**（类型标签 + 引用 + 说明 + 手动刷新） | `capability/components/CapabilityDetail.vue` |

**训练台能力就绪度（替代手打编码，并把"能不能跑通"前置）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 后端 | 预检新增**能力事实**判定并分级：**能力不存在 / 已停用 = 阻断**；**缺候选 / 候选全不健康 / 候选未探测 / 缺策略 / 策略不全 = 提醒**（D07 明确允许草稿先存半成品，所以后者不能拦提交） | `AigStudioCapabilityFacts`（快照）、`AigStudioCapabilityFactsResolver`（查概览，**LIKE 结果里再按编码精确取**）、`AigStudioPrecheck#of(content, facts)` / `evaluate(..., facts)`、`AigStudioDraftServiceImpl#factsFor` |
| 前端 | 「所需能力」从手打输入框改为**能力下拉**（`filterable` + `allow-create` 保留新建逃生口），下拉项与选中后就地显示就绪度（缺候选/缺策略/停用/未探测），读不到清单时**如实说"没权限核对"**而不是谎报"能力不存在" | `views/aigov/studio/index.vue`、`capability/presentation.ts#capabilityReadiness` |
| 口径单一 | 就绪度判定**复用能力概览服务**（与治理台卡片同一套"缺候选/缺策略/健康"口径），不在训练台另写一套 | `AigStudioCapabilityFactsResolver` |

**为什么"缺候选/缺策略"只是提醒而不是阻断**：训练台按 D07 的既定语义允许先存半成品（先写 Prompt、后补配置）；
把它升级成阻断会直接推翻那条语义，而且会逼人先绕开训练台去建能力。提醒里已写清"现在正式调用**一定调不通**"，
所以既不误导，也不拦人。

**Q3「预演也要说真话」（dryRun 补两道的只读预判）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 后端 | dryRun 新增 `wouldRequireApproval`（取路由决策里的同一个字段，与真实判定同源）与 `quotaStatus`（`AigQuotaStatusVo`：`limited/exceeded/remaining/daily*/monthly*`）。**全部只读**：不写审计、不写决策账本、**不调用** `assertWithinQuota`（不消耗额度），且只回调用人自己的额度；没有调用人时如实留空 | `AigInvokeVo`、`AigQuotaStatusVo`、`AigInvokeServiceImpl#dryRun`/`fillDryRunGuards` |
| 前端 | 试调助手第 ⑤ 段改为「真实调用前还会遇到什么（预判）」：人工确认点 + 审批需求（**三态**：需要/不需要/**未返回＝未知**）+ 额度（已超限/还剩 N 次/不限/**读不到**）；边界说明改写为"**预判 ≠ 校验**"（dryRun 仍不查你手上有没有有效授权） | `api/aigov/invoke/index.ts`、`capability/presentation.ts#realCallGates`/`assistantBoundaryNote`、`components/TrialAssistant.vue` |
| 口径 | `remaining` 取日/月里**最紧**的那个（"日剩 2、月剩 5"必须说 2，否则界面会让人以为还能调 5 次）；已超限时下限 0，不给负数 | `AigQuotaStatusVo.of` |

**Q5「员工自助：我的额度 / 我的申请」（纯前端）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 前端 | 门户（AI 工作台）工具栏新增「我的额度与申请」→ 一个弹窗两页签：**我的额度**（四态：正常 / 已超限 / 不限 / **读不到**；日·月已用与上限、周期起点；写明"统计的是调用次数，含失败"）与**我的申请**（状态标签含**「已批准但已过期」**、授权有效期止、审批意见、按状态筛选、分页） | `views/aigov/mine/MyQuotaAndApprovals.vue`、`views/aigov/portal/index.vue`、`views/aigov/mine/presentation.ts` |
| 续期入口 | 「重新申请」只出现在**现在没有有效授权**的行（驳回 / 超时未批 / 撤回 / 已过期）；**待审批**与**仍在有效期内**的行不给（重复单只会打扰审批人）。提交需要 `aig:approval:apply`：没有该权限时不显示按钮并说明找谁，而不是点了才 403 | 同上（`renewable` / `renewalHint`） |
| 边界（沿用后端口径） | 两个接口 `/aigov/quota/my-usage`、`/aigov/approval/my` **都不挂权限点**（登录即可见**自己**，后端用登录态覆盖 `requesterId`）⇒ **不新增权限点、不新增菜单/路由、不改表**；也因此不占治理台导航 | — |

**Q2C「能力声明的最低数据等级」（唯一要改表的一项）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 迁移 | `aig_capability.min_data_level`（VARCHAR(16)，**NULL = 不声明**）；幂等（information_schema + PREPARE）并已进 `deploy-poc.yml` 重放清单 | `script/sql/aig_capability_min_data_level.sql`、`.github/workflows/deploy-poc.yml` |
| 口径 | 抽成纯函数 `AigDataLevelEscalation`：生效等级 = **max(声明, 调用方传入)**，**只抬不降**；声明无法识别时保留调用方等级**并在命中明细里写出来**（不静默失效） | `helper/AigDataLevelEscalation` |
| 路由 | 在**路由引擎**里合并（步骤 1.5）：后面每一步（找策略 / 判外发 / 筛候选）都用生效等级；新增 `AigRouteDecision#effectiveDataLevel` 供上层与审计读取 | `AigRouteServiceImpl#doDecide`、`AigRouteDecision` |
| 调用入口 | `invoke` 改用**生效等级**落审计 / 决策账本 / 审批核对（否则会出现"审计写 INTERNAL、实际按 RESTRICTED 跑"、"按 RESTRICTED 要求审批却拿 INTERNAL 的授权核对"） | `AigInvokeServiceImpl#effectiveLevel` |
| 校验与高风险 | 声明值只接受四个等级（服务端兜底 + 表单四个选项）；**调低/清空**已声明的等级 = 高风险（松锁）；**限制级/严格级能力的候选变更**（新增/改/删）也需 `aig:capability:high-risk`——这正是 Q1C 里因"没有声明等级列"而留到本轮的那半条规则 | `AigCapabilityServiceImpl#assertLegalMinDataLevel`、`AigHighRiskChanges#forMinDataLevelChange`/`forCandidateChange`、`AigModelBindingServiceImpl` |
| 前端 | 能力表单新增「最低数据等级」下拉（默认不声明）+ 就地解释"只抬不降"；详情页显示声明值与"候选变更属高风险"提示；候选编辑/删除前弹确认框 | `capability/index.vue`、`capability/components/CapabilityDetail.vue`、`capability/presentation.ts#minDataLevelOptions`/`minDataLevelLabel`/`candidateChangeNeedsHighRisk` |

**P2 第一批：交互规范落地（走读 §5.5；纯前端）**

| 交付 | 文件 | 说明 |
| --- | --- | --- |
| 统一空态组件（图标 + **一句人话（为什么是空的）** + 下一步动作按钮） | `views/aigov/components/AigEmptyState.vue` | 走读 U10 记的"20+ 处无空态"：原先没数据就只剩表头，人分不清"没有"与"没加载出来"，于是反复刷新 |
| 长 ID 折叠组件（头 6 位…尾 6 位 + 悬停全量 + 点击复制） | `views/aigov/components/AigLongId.vue`、`components/id.ts`（纯函数 `foldId`/`needsFold`） | 19 位雪花 ID 直出会把列表挤成一堆数字、复制又容易少一位；剪贴板不可用时**如实提示**而不是静默失败 |
| 首批接入（第 1 批） | `tool`（空态 + `v-loading`）、`knowledge`（集合/文档空态 + 两个 loading）、`feedback`（空态 + loading + 清筛选动作）、`evaluation`（用例/运行空态）、`audit`（空态） | 走读 U10 点名"无 loading / 无空态"的 5 个页面 |
| 第 2 批接入 | `model`（主表空态）、`route`（主表空态，"无策略即拒绝"写进提示）、`agent`（主表空态，4 张表里补主表）、`quota`（空态 + 用户ID 折叠）、`approval`（空态 + 单号折叠）、`binding`（空态 + 绑定ID/Agent版本ID/公司/品牌 折叠）；**长 ID 折叠另接入** `feedback`（5 个 19 位 ID 列，原本整表都是数字）、`knowledge`（文档ID）、`audit`（traceId） | — |

| 第 3 批接入 | `artifactDraft`(主表)、`package`(主表 + 版本表)、`portal`(我的任务 + 产出物)、`resourceBinding`、`rolePackage`(岗位表 + 卡片表)、`servicetoken`、`skill`(主表 + 版本表 **2/2 全覆盖**)、`studio`(草稿表)、`task`(任务表 + 事件流 + 候选结果 **3/3 全覆盖**) | 这一批用**脚本化插入**完成（同一模式套 18 张表，逐页写 spec 保证标题/提示是给这张表写的；脚本在 `.tools/tmp/`，未入库） |

| 第 4 批接入（弹窗子表 / 多类表格，15 张） | `artifactDraft`(子 Agent 草案 + 指令草案 → **3/3**)、`package`(扫描结论 + 停用前状态 + 状态流转 → **5/5**)、`rolePackage`(分类 + 卡片 → **4/4**)、`evaluation`(人工复核 → **3/3**)、`knowledge`(分段预览 + 引用 + 检索结果 → **5/5**)、`model`(供应商 → **2/2**)、`agent`(版本 + 沙箱产物 → **3/4**)、`portal`(我的资产 → **3/5**) | 同一套脚本化插入 |
| **刻意不加空态的 3 张表** | `portal` 的「最近资产」（外层 `v-if="recentAssets.length"`）、「按域分组资产」（`v-for="group in assetGroups"`，空域不建组）、`agent` 的「下载记录」（外层 `v-if="downloadsOpen && artifactDownloads.length"`） | 它们**本来就只在有数据时才渲染**，加空态只会变成永远看不到的死代码 |

> **空态覆盖率（2026-10-16 实测；口径＝`<el-table>`（用 `(?![-\w])`，否则会把 `<el-table-column>` 也算进去）与 `#empty` 次数）**：
> **全覆盖**：`agent` 3/4、`artifactDraft` 3/3、`approval`、`audit`、`binding`、`evaluation` 3/3、`feedback`、`knowledge` 5/5、
> `model` 2/2、`package` 5/5、`quota`、`resourceBinding`、`rolePackage` 4/4、`route`、`servicetoken`、`skill` 2/2、`studio`、`task` 3/3、`tool`；
> **有意不覆盖**：上面那 3 张（`agent` 少 1、`portal` 少 2）；`capability` 用卡片网格 + `el-empty`（不走 `el-table`，另行覆盖）。
> 走读 U10 的"**20+ 处无空态**"至此收口。
> 其余待办：**分页**已只对问题队列做（见下）；**各页观感仍需人眼验收**（本仓前端没有 DOM 测试环境）。

**P2 附加：问题队列分页（后端 + 前端；2026-10-16）**

走读 U10 说"5 个页面无分页"。逐个看下来，**真正需要分页的是问题队列**——它是"收件箱"，行数只增不减，
全量拉取在队列长了以后既慢又难用（运维得自己在页面上找第几页）。其余几个（`tool` 注册表、`knowledge` 集合/文档、
`evaluation` 用例/运行）后端接口返回的是 `List<>`，条数量级是几十，改成分页属**破坏性接口变更**，收益不抵风险 ⇒ **不做**。

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 后端 | 新增 `GET /aigov/feedback/queue/page`（`PageQuery` + `PageResult`，权限沿用 `feedback:queue`）；**过滤条件与全量版抽成同一个私有方法**（`queueWrapper`）——两份过滤迟早会出现"翻页后筛选口径不一致"这类看不见的错 | `AigResultFeedbackController#queuePage`、`IAigResultFeedbackService#queryQueuePage`、`AigResultFeedbackServiceImpl` |
| 后端 | 旧 `GET /aigov/feedback/queue`（全量）**保留不动**：避免破坏未知调用方，javadoc 上写明"页面请用分页版" | 同上 |
| 前端 | 问题队列页改用分页接口 + `<pagination>`；**改筛选回到第 1 页**（否则停在第 3 页一改筛选只剩空表，看起来像"没有数据"） | `views/aigov/feedback/index.vue`、`api/aigov/feedback/index.ts#listFeedbackQueuePage` |
| 测试 | 新增 1 条：分页版与全量版**共用同一份过滤条件**（断言 where 片段含 `verdict`/`action_code`），且非法编码仍抛错——**不能把"筛错了"变成"空队列"** | `AigResultFeedbackServiceImplTest#queryQueuePageSharesFilterWithListQueue` |

> 踩坑：`PageQuery` 的构造器是 **`(pageSize, pageNum)`**（顺序与直觉相反），测试里写错会静默换页。

**仍未做**：其余页面按 v3 规范改写、弹窗子表空态（18 张）、**Q1C 完整版**（需新表）、**权限边界正名**（§5.4，属授权变更）。

**P2 附加：危险操作确认审计（2026-10-16，把口径写死以便复核）**

走读 U10 的"**5 处危险操作无二次确认**"当初**没有留下可复核的清单**（是哪 5 处、按什么口径数）。
本轮把它做成可复现的审计，并把查出的缺口补上：

- 工具：`script/ci/audit-aigov-dangerous-ops.mjs`（**评审用，刻意不接 CI 门禁**——判据含"命名意图/是否可恢复"这类人工成分，
  而"弹窗表单 / `modal.prompt` 收原因"同样算确认；硬做成门禁只会不断误报、最后被加废）。
  口径写在文件头：①危险命名（delete/remove/archive/cancel/revoke/expire/disable/rollback/unbind/clear 前缀）
  ②函数体里真有写接口调用（且剔除 `modal.confirm` 自身）③函数内是否有确认（含 prompt）。
- 实测结果：**①危险命名且会写库 = 2 个，缺确认 0 个**；②会写库且无函数内确认、又不属于"弹窗表单提交/辅助函数" = **1 个**，
  即 `agent#doDownloadArtifact`——**已核实它只是写一条"下发记录"（审计痕迹），不属于需要二次确认的危险动作**。
- 补上的 2 处确认（原先确属"一次点击就改状态、没有中间那一步"）：
  · `approval#handleExpireScan`：**批量**把超时未批的单子置为"已超时未批"且不可逆 ⇒ 确认框写清"做哪件事 + 影响什么 + 能不能恢复"，
    并提示"只想看超时单用状态筛选即可"；
  · `knowledge#doIndexVersion`：解析并索引会覆盖该版本的分段与索引 ⇒ 确认框说明影响，并写明"已就绪的版本服务端拒绝原地重建，需先新建版本"。
- 结论（对我们的口径）：**"5 处"这个数现在无法复核**，按上面口径重测后真实缺口是 2 处、已补；这一点如实记下，
  避免以后有人拿"5 处"当结论继续找。

**验证**（本次实测，2026-10-16）：后端 **1413 tests / 0 failures / 0 errors**；
前端 **57 files / 572 tests**；`oxlint` **572 files 0/0**；`vite build` 通过；`verify-aigov-v1.mjs` 全绿。

**Q2C 之后 P1 已全部完成**：剩下的只有 Q1C **完整版**（变更单 + 批准 + 生效记录，需新表，属 P2）与
22 项交互规范 / 其余模块按 v3 改写（P2，本批已开头）。

**验证**（本次实测，2026-10-16）：后端 **1412 tests / 0 failures / 0 errors**；前端 **56 files / 566 tests**；
`oxlint` **568 files 0/0**；`vite build` 通过；`verify-aigov-v1.mjs` 全绿。

**Q1C「高风险变更轻量门槛」+ Q6 剩余（改策略 / 停用入口的同一套确认）**

| 层 | 交付 | 文件 |
| --- | --- | --- |
| 权限点 | 新增 `aig:capability:high-risk`（与 `aig:capability:edit` / `aig:route:edit` **刻意分开**：能改配置 ≠ 能放宽数据出界），只授 `aig_admin`；幂等种子 | `script/sql/aig_capability_high_risk_menu.sql`、`AigConstants#PERM_CAPABILITY_HIGH_RISK` |
| 后端 | 规则本身抽成纯函数 `AigHighRiskChanges`（放开外发 / RESTRICTED·STRICT 策略 / 首选外部部署 / 删除命中行；停用只判「启用→停用」一个迁移）；闸门 `AigHighRiskGuard` 用 Sa-Token 判权限，**无登录态 fail-closed** | `helper/AigHighRiskChanges`、`helper/AigHighRiskGuard` |
| 后端接线 | 路由策略 **新增 / 编辑（对比新旧行，N→Y 才算放宽）/ 删除**；能力 **停用 / 删除** | `AigRoutePolicyServiceImpl`、`AigCapabilityServiceImpl` |
| 前端 | 策略保存 / 列表内开启外发 / 删除策略：高风险时弹确认框——**列原因 + 需要哪个权限点 + 影响面（谁在用它）**；能力停用同样先列影响面 | `views/aigov/route/highRisk.ts`、`route/index.vue`、`capability/presentation.ts#disableConfirmText`、`capability/index.vue#submit` |

**刻意没做的（如实标注，不假装已防住）**：联动方案 §8.1 第 4 条里"**限制级/严格级能力的候选变更**
（新增/改用途/启停）"依赖能力的**声明等级列**（Q2C 的 `min_data_level`），该列尚不存在；
现在只能按策略行猜，会误报，所以留到 Q2C 一起做。本轮覆盖的是**放宽外发与限制级策略变更**这条主线。

**验证**（本次实测，2026-10-16）：后端 **1401 tests / 0 failures / 0 errors**（本轮新增 10 条高风险判定与接线）；
前端 **56 files / 565 tests**；`oxlint` **568 files 0/0**；`vite build` 通过；`verify-aigov-v1.mjs` 全绿。

**顺带修好的一处"守卫会误报"**：`check-aigov-docs.mjs` 原先要求 §1 表里**每个**提交号都是当前分支 HEAD 的祖先。
A–E 分支已由 PR #80 **squash 合并**（祖先关系被故意抹掉），这条规则于是会一口气报 54 个"不是祖先"——
它的出路本来只有一条：把真实历史从文档里删掉。⇒ 改成 **§1 正文（本分支提交）要求祖先关系不变**，
**§1.1「已合并分支的历史提交」只要求提交对象在仓库里存在**（防抄错的目的一分不减），
并要求 §1.1 标题必须写明「已合并」——不然等于给祖先检查开后门。

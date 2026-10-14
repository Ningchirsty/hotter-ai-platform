# 20 - Agent 平台优化 v1.0 · 阶段 B 实施说明（D06/D07/D09 + 界面元数据）

> 依据：附件《Agent 平台优化详细设计 v1.0》
> 分支：`feature/aigov-optimization-v1`
> 阶段 A 见 `19-Agent平台优化v1.0-阶段A实施说明.md`。本文覆盖阶段 B 中**已经落地**的部分。

---

## 1 本轮落地

| 编号 | 设计问题 | 本轮处理 |
| --- | --- | --- |
| D07 | 预检主要查分节键/至少一节非空/能力/JSON；合法类别在提交阶段才查 | 新增 `AigStudioPrecheck`：**分级**（阻断/提醒）+ 字段定位 + 建议动作；类别校验前移；预检与提交**共用同一套判据** |
| D09 | PromptBuilder 与提交服务存在两份分节拼装实现 | 删除提交服务里的第二份实现，统一调 `AigStudioPromptBuilder.build` |
| D06 | 采纳提案后重新加载编辑器可能丢未保存内容；关闭编辑器缺退出保护 | 新增 `dirtyGuard`（三选一）+ 关闭编辑器/采纳提案/浏览器关闭三处保护 |
| D11 | 员工启动需填任务类型/业务域/数据等级/原始上下文键，界面泄露实现概念 | 新增启动任务模板 `aig_launch_template`：服务端推导执行字段，员工只填业务字段 |
| D10 | Copilot 复用被训练 Agent 的能力编码 | 阶段 A 已完成（`aigov.studio.copilot.capability`） |

### 1.1 界面元数据（设计 §9.2）

新增 `GET /aigov/studio/metadata`（`AigStudioMetadataController`，权限沿用草稿查看权限），返回：

- `categories`：类别编码 + 中文名 + 仓库现有实现 + `executable`（`ANALYSIS` 实现字段是说明性占位，
  如实标为 `false`，不允许宣称可执行）；
- `promptSections`：八个分节的**键 + 中文标签**，顺序与 `PROMPT_SECTION_KEYS` 一致（静态初始化时断言，
  不一致直接启动失败——避免"界面说明漂移"再次发生）。

服务端因此成为训练台词表的**唯一来源**，前端不再各自枚举。

### 1.2 分级预检（D07）

`AigStudioPrecheck` 是纯函数，输出 `blockers` / `warnings`：

- **阻断（提交必须清空）**：`CONTENT_NOT_JSON`、`SECTION_MISSING`、`SECTIONS_ALL_BLANK`、
  `CAPABILITY_MISSING`、`CATEGORY_MISSING`、`ALLOW_EXTERNAL_INVALID`、`SCHEMA_INVALID`、
  `PAGE_SCRIPT_FORBIDDEN`；
- **提醒（不拦提交）**：`OUTPUT_SCHEMA_MISSING`、`RESOURCE_REGISTERED_ONLY`
  （工具/知识"已登记、待接入"，落地设计 §4.1 的如实展示要求）。

每项含 `code / severity / fieldPath / message / suggestedAction / relatedRef`（设计 §9.3）。
`AigStudioValidateVo` 新增 `blockers`/`warnings`；`problems` 保留为**阻断项**说明，
兼容旧调用方且不让提醒被误当成必须清空的错误。

`submitDraft` 改为读同一套 `blockers` 判据，因此"预检说通过"与"提交被接受"不可能分叉。

### 1.3 未保存保护（D06）

`frontend/src/views/aigov/studio/dirtyGuard.ts` 固定**三个选择**：

1. 保存并继续；2. 放弃本地改动并继续；3. 返回继续编辑。

**刻意没有"直接继续"**——那条路径会让本地改动静默丢失，而这正是设计要禁止的结果。
三处接入：

- 关闭编辑器按钮 → `guardOrRun(..., '关闭编辑器')`；
- 采纳提案（`ProposalPanel`）→ `dirty` + `save` 两个 prop，采纳前弹三选一；
  "保存并继续"由父组件保存，**保存失败则不继续**（内容仍在编辑器里）；
- 浏览器关闭/刷新 → `beforeunload` 拦截（自定义按钮在浏览器里不可用，因此这里只决定是否拦截）。

---

## 2 本轮**不做**的事

1. **训练台三栏布局与步骤导航**（设计 §6.3、§5.4 线框图）：现有页面已具备主编辑区 + 右侧
   `ProposalPanel`，但尚未按线框拆成"步骤导航/主编辑/辅助区"三栏组件；属独立的界面重构。
2. 阶段 C/D/E（知识中心、工具执行、反馈运营）未开始。

---

## 2.1 D11 启动任务模板（本轮补齐）

**问题**：员工启动前要自己填 `taskType / projectType / dataLevel / snapshotJson`——
这些是平台实现概念；填错的表现是"任务建出来了却永远不动"，比当场拒绝难查得多。

**做法**：新增表 `aig_launch_template`（`script/sql/aig_launch_template.sql`），把执行字段
绑定到「岗位 + 卡片」：

- 匹配口径 `(role_code, action_code)`：**精确优先**，`action_code` 为空是该岗位默认模板；
  都没有时**不套用**（行为与过去完全一致，向后兼容）。
- `AigLaunchTemplateServiceImpl#apply(bo)` 在 prepare 与 commit 各调用一次（**在算请求摘要之前**，
  两次看到同一份有效字段，摘要才可能一致），就地填充 `taskType/projectType/dataLevel`，
  并按 `snapshotFromContext` 用业务输入组装快照。
- **数据等级是服务端结论**：模板给了值就以模板为准，请求里传什么都覆盖掉（"不允许放宽"）。
  这里刻意不新增错误码——设计 §12.5 的错误码是封闭的九个，且"服务端结论优先"本就不需要报错。
- 前端 `LaunchFormDrawer.vue`：取到模板时只渲染 `businessFields ∪ requiredContext`（取并集是为了
  "不漏"——卡片硬要求的上下文键一个都不能少），执行字段改为只读展示；取不到模板回退旧表单。
  新增只读接口 `GET /aigov/portal/launch/template`。

**影响面**：新表 + 新接口（`@SaCheckLogin`，无新权限点）；无模板的岗位行为不变。

---

## 3 验证

- 后端 `ruoyi-ai-gov` 全量：**1085 tests / 0 failures**（阶段 A 后 1077 → D07/D09 +2 → D11 +6）。
- 前端 `vitest run`：**45 files / 442 tests 全过**（新增 `dirtyGuard.spec.ts` 5 条）。
- `oxlint src`：526 files，0 warnings / 0 errors。
- `script/ci/check-script-setup.mjs`：改动的 `.vue`（`index.vue`、`ProposalPanel.vue`、
  `LaunchFormDrawer.vue`）均 OK（该脚本要求传入全部 .vue 才算覆盖达标，CI 里由调用方传入）。

命令备忘：

```bash
# 后端（注意根 pom 默认 maven.test.skip=true）
./mvnw -o -Plocal -pl ruoyi-modules/ruoyi-ai-gov -am test -Dmaven.test.skip=false
# 前端
cd frontend && ./node_modules/.bin/vitest run && ./node_modules/.bin/oxlint src
```

---

## 4 对生产的影响

- 新增一张表 `aig_launch_template`（需在测试环境先跑 `script/sql/aig_launch_template.sql`）
  与一个**只读**接口 `GET /aigov/portal/launch/template`；无新权限点（沿用 `@SaCheckLogin`）。
  未配置模板的岗位行为完全不变。
- 预检语义强化：**类别非法的内容现在会在预检阶段就报阻断**（此前只在提交时报）。
  这是有意的收紧，且 `problems` 仍为阻断项说明，旧前端展示行为不变。
- 前端未保存保护、业务表单属纯前端变更。

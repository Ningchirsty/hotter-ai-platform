# 视频能力 ↔ ComfyUI 工作流契约（后端工件）

本目录是视频创作模块与 ComfyUI 工作流绑定的**唯一权威契约**，对应
`frontend/src/views/video/modules.ts`（前端安全视图，只有 workflowCode 和字段白名单）。

## 边界（硬约束）

- 节点 ID、inputKey、API Format JSON、模型路径**只存在本目录与后端**，禁止下发前端。
- 每个能力 × 模型组合对应一个 `workflowCode`，即一份后端受控目录下的 API Format JSON 模板。
- 任务提交时后端**深拷贝**模板，仅覆写 `mapping` 白名单内声明的节点输入键，其余输入一律不动。
- 未标记 `PUBLISHED` 的工作流不得作为可提交的任务提供；导入模板不等于已联通服务。

## MiniMax H3 三种模板

`api/` 中的 `wf-i2v-h3`、`wf-t2v-h3`、`wf-fl2v-h3` 分别来自提供的图生、文生、首尾帧 API Format JSON。导入程序 `import-h3.mjs` 清除了样例提示词和聊天附件图片引用，记录清理后模板的 SHA-256。原始样例文件不进入仓库。

三份模板的生成节点固定 24 fps、124 帧、1920×1088，保存前缩放为 1920×1080。124/24 约为 5.17 秒；产品要求最多 5 秒，因此服务端仍需验证实际输出并在必要时截取到 5 秒以内，完成实机验收前不得发布。`tier`/`dur` 只接受高清 1080P/5 秒，属于固定值校验，不是可覆写的节点映射。`MiniMaxH3Director` 的任务类型、模型路径、采样设置以及 `SaveVideo` 输出节点保存在服务端模板中。`prepareH3Graph` 是服务端接入时的字段映射参考：服务端要先上传图片并解析为 ComfyUI 可访问的输入文件名，再深拷贝模板，设置提示词和时间轴的首帧/尾帧字段。它不是浏览器代码，也不会自行调用 ComfyUI。

当前状态保持 `DRAFT`：尚需在 ComfyUI 环境核对 `MiniMaxH3Director`、`LoraLoaderModelOnly`、`PathchSageAttentionKJ` 等节点和模板中的模型权重，完成任务 API、权限、文件上传、队列、账单和实机输出验收后才可发布。前端因此只展示模板映射，禁用提交和未交付模型。用 `node --test script/video/workflows/import-h3.test.mjs` 校验映射、校验值及无样例图片引用。

## 填充流程

1. ComfyUI 同事按能力交付 API Format JSON + 实测数据；
2. 按 `docs/07-workflow-onboarding-checklist.md` 逐项填写（本契约的字段即清单 §4/§5/§6/§7 的结构化形式）；
3. 将各 `mapping` 条目的 `nodeId`/`inputKey`、`outputRule`、`perf`、`checksum` 填实，
   `apiJsonFile` 指向后端受控目录中的模板文件；
4. 状态从 `DRAFT` → `TESTING` → `PUBLISHED`；固定后台参数变化必须新增版本，不改旧版本。

## 文件

- `video-workflow-contracts.json` — 全量契约（三个 H3 模板已导入，其他绑定仍为 DRAFT 占位）
- 目标表结构见 `../../sql/ry_video_workflow.sql`（video_workflow_version，按 docs/04 数据模型）
- 已导入旧菜单的数据库可按对应方言的 `ry_video_menu_migration.sql` 更新菜单；MySQL 旧表若已建并有数据，先核对迁移脚本注释中的前置条件再改名。不要在已导入的数据库重复执行新菜单插入脚本。

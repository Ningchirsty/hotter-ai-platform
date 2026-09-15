# 视频能力 ↔ ComfyUI 工作流契约（后端工件）

本目录是 7 个视频创作模块与 ComfyUI 工作流绑定的**唯一权威契约**，对应
`frontend/src/views/ai/studio/modules.ts`（前端安全视图，只有 workflowCode 和字段白名单）。

## 边界（硬约束）

- 节点 ID、inputKey、API Format JSON、模型路径**只存在本目录与后端**，禁止下发前端。
- 每个能力 × 模型组合对应一个 `workflowCode`，即一份后端受控目录下的 API Format JSON 模板。
- 任务提交时后端**深拷贝**模板，仅覆写 `mapping` 白名单内声明的节点输入键，其余输入一律不动。
- 未标记 `PUBLISHED` 的工作流不得出现在工作台。

## 填充流程

1. ComfyUI 同事按能力交付 API Format JSON + 实测数据；
2. 按 `docs/07-workflow-onboarding-checklist.md` 逐项填写（本契约的字段即清单 §4/§5/§6/§7 的结构化形式）；
3. 将各 `mapping` 条目的 `nodeId`/`inputKey`、`outputRule`、`perf`、`checksum` 填实，
   `apiJsonFile` 指向后端受控目录中的模板文件；
4. 状态从 `DRAFT` → `TESTING` → `PUBLISHED`；固定后台参数变化必须新增版本，不改旧版本。

## 文件

- `video-workflow-contracts.json` — 全量契约（23 个工作流绑定，当前均为 DRAFT 占位）
- 目标表结构见 `../sql/ry_ai_studio_workflow.sql`（ai_workflow_version，按 docs/04 数据模型）

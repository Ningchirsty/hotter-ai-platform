# ZCode 操作交接文档（供 Codex 预览审查）

> 更新时间：2026-09-15 · 操作者：ZCode
> 状态：**本地提交（未推送 GitHub）**——分支 feature/frontend-plus-ui-v6。
> 历史脉络：8c6cd2593（我的任务页）→ Codex 4 个提交（studio 工作台替换 video-tasks）→ **本次：工作流契约绑定**。

## 1. 本次操作目标（用户要求）

> "7 个视频模块必须要和 ComfyUI 里面的工作流节点匹配，因为之后是要调用 ComfyUI 的工作流节点。"

即：工作台的 7 个能力不再是纯 UI 概念，每个能力 × 模型组合必须绑定一个真实的
ComfyUI 工作流（API Format JSON 模板 + 字段→节点输入白名单），为后端调用 ComfyUI 做好契约准备。

## 2. 改动清单（本次提交）

| 文件 | 类型 | 内容 |
|---|---|---|
| `frontend/src/views/ai/studio/modules.ts` | 新增（替代 mock.ts） | 模块定义携带工作流绑定：`models[]` 模块×模型矩阵、`fixedWorkflow`（VHD/LIP 固定工具工作流）、`resolveWorkflowCode()` 推导 workflowCode |
| `frontend/src/views/ai/studio/mock.ts` | 删除 | 语义升级为契约文件，更名 modules.ts |
| `frontend/src/views/ai/studio/index.vue` | 修改 | ① 模型区按模块矩阵过滤（闭源/开源分组空组自动隐藏）② VHD/LIP 显示只读"处理工作流"信息块 ③ 版本 pill 显示工作流/模型版本 ④ 提交组装契约 payload `{capabilityCode, workflowCode, modelCode, fields}`（console.debug，待接 POST /ai/studio/tasks）⑤ 灵感卡带同款时校验模块×模型兼容 |
| `script/workflows/video-workflow-contracts.json` | 新增 | **后端侧唯一权威契约**：7 能力 × 23 个工作流绑定，含字段→节点白名单（nodeId/inputKey 占位 TBD）、固定参数、输出规则、性能指标、计费 |
| `script/workflows/README.md` | 新增 | 契约填充流程（按 docs/07 清单）、安全边界、发布状态机 |
| `script/sql/ry_ai_studio_workflow.sql` | 新增 | 目标表 `ai_workflow_version` DDL（docs/04 数据模型；种子数据由契约 JSON 导入，后端 H6 实现时启用） |

## 3. 模块 × 工作流矩阵（DRAFT，待 ComfyUI 同事交付后填实）

| 能力 | 可用模型 / 固定工作流 | workflowCode 规则 |
|---|---|---|
| I2V 首尾帧生视频 | H3·H3P（积分）/ WAN·HUN·LTX·COG（时长） | wf-i2v-{model} ×6 |
| T2V 文生视频 | 同上 6 个底模 | wf-t2v-{model} ×6 |
| MFRAME 智能多帧 | WAN·HUN（闭源是否支持多帧待契约确认） | wf-mframe-{model} ×2 |
| CAMMOVE 运镜视频 | H3P·WAN·LTX | wf-cammove-{model} ×3 |
| VEXT 视频续写 | H3·H3P·WAN·LTX | wf-vext-{model} ×4 |
| VHD 补帧高清化 | **固定**：RIFE 补帧 + 4K 超分（不消费生成底模） | wf-vhd-rife-upscale |
| LIP 对口型 | **固定**：LatentSync（不消费生成底模） | wf-lip-latentsync |

安全边界（章程硬约束 3/4/7）：节点 ID、API JSON、模型路径只存在于
`script/workflows/`（后端工件）；前端只持有 workflowCode + 字段 Schema 白名单；
后端任务提交时深拷贝模板，仅覆写 mapping 白名单内节点输入键。

## 4. 验证状态

- ✅ `npm run build:prod` 通过（14.3s，studio chunk 正常产出）
- ✅ 契约 JSON 校验通过（7 capabilities / 23 workflows，与前端矩阵一一对应）
- ⬜ 菜单 SQL / 工作流表 SQL 未在目标库执行
- ⬜ **未推送 GitHub**（按用户要求，等 Codex 预览/修改结束）

## 5. 给 Codex 的审查要点

1. **矩阵合理性**：模块×模型可用性是按开源模型能力做的 DRAFT 预判（MFRAME 不含闭源、CAMMOVE 仅 H3P/WAN/LTX 等），如有市场/交付依据请直接改 `modules.ts` 数据 + 同步契约 JSON，两处必须一致。
2. **固定工作流模块**：VHD/LIP 取消了生成模型选择（改为只读工作流信息块），确认产品形态可接受。
3. **payload 契约**：`{capabilityCode, workflowCode, modelCode, fields}` + 上传文件换 fileIds，是后端 POST /ai/studio/tasks 的输入草案，后端实现时以此为准。
4. **后续任务序**：我的任务列表页（studio 提交后无处可看，优先补）→ 任务详情 → 后端 API/表落地（契约 JSON → ai_workflow_version 导入）。

## 6. 提交信息

```text
8c6cd2593  feat: add ai video tasks page with nebula theme and menu sql        （ZCode，已被 studio 替代）
c9610c743  fix: address ai video tasks review findings                        （Codex）
603f19aec  feat: add zcode video creation studio                              （Codex）
5b12922ef  fix: show video studio as top-level menu                           （Codex）
eff294681  fix: align embedded studio with zcode prototype                    （Codex）
(本次)     feat: bind video modules to comfyui workflow contracts             （ZCode）
```

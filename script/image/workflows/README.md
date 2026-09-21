# 图像能力 ↔ ComfyUI 工作流契约（后端工件）

本目录是**图像创作模块**与 ComfyUI 工作流绑定的唯一权威契约，对应
`frontend/src/views/image/modules.ts`（前端安全视图，只有 workflowCode 和字段白名单）。

## 边界（硬约束，与视频模块一致）

- 节点 ID、inputKey、API Format JSON、模型路径**只存在本目录与后端**，禁止下发前端。
- 每个能力对应一个 `workflowCode`，即一份后端受控目录下的 API Format JSON 模板。
- 任务提交时后端**深拷贝**模板，仅覆写 `mapping` 白名单内声明的节点输入键，其余输入一律不动。
- 未标记 `PUBLISHED` 的工作流不得作为可提交的任务提供；**导入模板不等于已联通服务**。
- 四个模板当前均为 `DRAFT`：真机已实测出图，但任务 API / 鉴权 / 生产环境尚未联调。

## 四个模板

| workflowCode | 能力 | 节点数 | 输出 | 实测 |
|---|---|---|---|---|
| `wf-t2i-qwen21` | 文生图 T2I | 8 | 1024×1024（可选 1MP/2K 档） | 4.0s |
| `wf-i2i-qwen21` | 图生图 I2I | 9 | 跟随输入图（实测 896×1152） | 4.0s |
| `wf-edit-qwen21` | 指令改图 EDIT | 10 | 跟随 image1（实测 896×1152） | 24.0s（3 参考图）/ 20.0s（2 张） |
| `wf-bgremove-qwen21` | 抠图去背景 BGREMOVE | 8 | 跟随输入图 + **RGBA 透明** | 4.0s（冷启动 12.4s） |

模型三件套固定：`qwen_image_2.1_int8_convrot`（UNET）+ `qwen3vl_8b_int8_convrot`（CLIP，type=`qwen_image`）+
`qwen_image_2.1_vae_bf16`（VAE）。采样固定 `steps=25 / cfg=1 / euler / simple`，**唯一允许覆写的采样参数**
是图生图的 `denoise`（字段 `strength`）。`seed` 一律由服务端按任务下发。

## 两个必须知道的实测坑（复现过的真实故障）

### 1. 参考图必须用**扁平点号键**，嵌套 dict 会被静默忽略

`TextEncodeQwenImage21` 的 `images` 是 `COMFY_AUTOGROW_V3` 输入。ComfyUI 用
`_io.build_nested_inputs()` 依据 schema 展开的 `dynamic_paths` 还原嵌套结构，而槽位只有在
**live_inputs 里存在扁平键**时才会被展开。因此：

```jsonc
// ✅ 正确：参考图会进入参考 latent，画布跟随 image1（实测 896×1152）
"images.image_1": ["4", 0]

// ❌ 错误：不报错，但参考图被丢弃，画布退回 resolution×resolution（实测 1024×1024）
"images": { "image_1": ["4", 0] }
```

判别方法与本次排障一致：**看输出画布尺寸**。`TextEncodeQwenImage21.execute` 在 `images` 为空时
`latent_w = latent_h = resolution`，所以 1024×1024 就意味着参考图没生效。

### 2. 空槽位必须裁剪

模板里 `LoadImage` 的 `image` 为空串时，ComfyUI 会尝试把 `input/` 目录当文件打开，报
`[Errno 21] Is a directory: '/workspace/ComfyUI/input'`。因此 EDIT 模板预置 3 个参考槽位
（节点 4/5/6 → `images.image_1..3`），后端在填充时**必须删掉未使用槽位的节点与其 `images.image_N` 输入**。
已实测：裁剪到 2 槽位可正常出图。

## checksum 约定

- 契约 `checksum` = **模板文件字节**的 SHA-256（小写 hex），模板必须是规范格式
  `JSON.stringify(obj, null, 2) + "\n"`（UTF-8）。
- 后端加载时会重新计算并与契约比对，**不一致即整条工作流不加载**（`ImageWorkflowContractRegistry`）。
- 模板改动后必须重算，禁止手抄：

```bash
node script/image/workflows/image-contracts.mjs           # 校验
node script/image/workflows/image-contracts.mjs --write   # 重算并写回
node --test script/image/workflows/image-contracts.test.mjs
```

## 填充流程

1. ComfyUI 侧交付 API Format JSON + 实测数据（本目录已有真机记录 `api/_validation-live.json`）；
2. 填实 `mapping` 的 `nodeId`/`inputKey`、`outputRule`、`perf`，`apiJsonFile` 指向后端受控目录中的模板；
3. `checksum` 用上面的 `--write` 生成；
4. 状态 `DRAFT` → `TESTING` → `PUBLISHED`；固定后台参数变化必须新增版本，不改旧版本。

## 表结构

- 任务/素材/事件三张表见 `../../sql/ry_image_task.sql`（`image_task` / `image_asset` / `image_task_event`）。
- 工作流版本镜像表见 `../../sql/ry_image_workflow.sql`。
- 菜单见 `../../sql/ry_image_menu.sql`（菜单 `920002 图像创作` / 按钮 `920003 提交图像任务`）。

## 启用方式

默认关闭。启用需要在部署环境显式打开并给出 ComfyUI 实际可达地址：

```yaml
image:
  enabled: true
  contract-root: script                      # 契约与模板所在的后端受控目录
  storage-root: /opt/ai-video-poc/data/image-assets
  comfy-base-url: http://192.168.2.223:8188  # 不要填 127.0.0.1
  comfy-allow-loopback: false                # 仅同机进程直连联调时放开
  poll-budget-seconds: 300
  poll-interval-seconds: 3
  require-published: true                    # 正式环境必须 true
  concurrency: 1                             # 与 ComfyUI 实例/显卡数一致
  queue-capacity: 16
```

环境变量形式（relaxed binding）：`IMAGE_ENABLED` / `IMAGE_CONTRACT_ROOT` / `IMAGE_COMFY_BASE_URL` /
`IMAGE_STORAGE_ROOT` / `IMAGE_REQUIRE_PUBLISHED`。

`require-published: true` 时只有 `PUBLISHED` 工作流可提交；隔离联调环境可设为 `false` 以验证 `TESTING`。
**DRAFT 在任何配置下都不可提交。**

# 蓝章鱼图像 API 配置与验收

最新能力状态请先阅读 [恢复后七型号验收](bluocto-restored-validation-2026-10-07.md)。后续历史联调章节保留当时记录，不代表当前可提交能力；当前门禁见每个型号的 `/image/cloud/models` profiles。当前 5179 已启用本机真实云端交互，范围与运行方式见 [本机真实交互验收](bluocto-local-live-2026-10-07.md)。生产尚未发布本次云端接入。

本次云端接入通过 `codex/cloud-image-api-integration` 提交至 main；生产是否启用应以实际镜像版本、服务端密钥挂载和只读配置检查为准。现有 ComfyUI 能力保持原接口与工作流。云端任务复用 image_task、image_asset、image_task_event，不需要数据库迁移。

## 1. 创建 Key

登录 https://bluocto.com 的控制台，进入 API 令牌管理，创建用于平台图像调用的令牌。根据账号权限选择 Media 分组，并允许需要测试的型号：

`flux-2-pro`、`gpt-image-2.5-flare`、`gpt-image-2.5-sunburst`、`qwen-image-3.0`、`qwen-image-3.0-pro`、`wan2.7-image`、`wan2.7-image-pro`。

先使用测试用途的额度限制。不要发 Key 到聊天、截图、源码、前端环境变量或 GitHub。

## 2. 本机联调：保存服务端密钥文件

在 Windows PowerShell 中逐行执行：

```powershell
New-Item -ItemType Directory -Force -Path 'C:\Users\Nchristy\.config\hotter' | Out-Null
if (-not (Test-Path -LiteralPath 'C:\Users\Nchristy\.config\hotter\bluocto.key')) {
    New-Item -ItemType File -Path 'C:\Users\Nchristy\.config\hotter\bluocto.key' | Out-Null
}
notepad 'C:\Users\Nchristy\.config\hotter\bluocto.key'
```

在记事本里只粘贴 Key 本身，保存为 UTF-8 纯文本。这里不在代码仓内；不要将文件复制到仓库。以上命令保留已有 Key 文件，不会覆盖。

当前 PowerShell 后端启动会话设置：

```powershell
$env:BLUOCTO_ENABLED='true'
$env:BLUOCTO_API_KEY_FILE='C:\Users\Nchristy\.config\hotter\bluocto.key'
```

这两个变量只有开关和路径，不包含 Key。后端已有数据库、Redis、IMAGE_ENABLED、ComfyUI 等配置保持原值；随后从包含本次代码的后端启动。当前 `127.0.0.1:5178` 是隔离源码预览，故意不连接后端，不能用于真实生成。真实测试要使用指向该后端的正式开发前端。

保存后只回复“已保存”和文件路径，即可继续 API 验证。

## 3. 生产 Docker 配置（部署本次后端之前完成）

配置对象是平台生产后端主机及 `/opt/ai-video-poc/compose.yaml`，不是只运行 ComfyUI 的 `192.168.2.223`。目前生产运行文件不在仓库，需服务器管理员核对实际内容后追加，不覆盖已有 environment / volumes。

在生产主机安全保存 Key：

```bash
sudo install -d -m 700 /opt/ai-video-poc/secrets
sudo install -m 600 /dev/null /opt/ai-video-poc/secrets/bluocto.key
sudo nano /opt/ai-video-poc/secrets/bluocto.key
```

文件只填 Key 本身。已有文件时跳过第二条命令，避免覆盖。密钥文件权限需允许容器实际运行 UID 读取；用 `sudo docker exec ai-video-poc-backend-1 id -u` 只读核对，非 root 容器时由管理员配置对应 UID 的最小权限，不改为全员可读。

在现有 `services.backend` 下追加：

```yaml
environment:
  BLUOCTO_ENABLED: "${BLUOCTO_ENABLED:-false}"
  BLUOCTO_API_KEY_FILE: /run/secrets/bluocto.key
  BLUOCTO_OUTPUT_HOSTS: "${BLUOCTO_OUTPUT_HOSTS:-bluocto.com,rolldek.com,dashscope-463f.oss-accelerate.aliyuncs.com,dashscope-7c2c.oss-accelerate.aliyuncs.com}"
volumes:
  - /opt/ai-video-poc/secrets/bluocto.key:/run/secrets/bluocto.key:ro
```

保留其他环境变量与挂载，不能增加第二个同名 environment / volumes 节点。在 `/opt/ai-video-poc/.env` 追加 `BLUOCTO_ENABLED=true`。只往 `.env` 加变量不会自动传入容器，必须同时设置上面的 environment 映射和只读文件挂载。Compose 与 .env 继续保持 root 所有、组和其他用户不可写，兼容原生产发布入口。

生产部署仍走现有不可变镜像发布流程，修改后端环境需要重新创建 backend 容器才能生效，不能靠重启旧容器更新环境变量。此文档不自动执行生产发布。

## 4. 核验顺序

在有 `image:creation:view` / `image:creation:submit` 权限的平台登录态下：

1. GET `/image/cloud/models`：`configured=true` 只表示配置可读取，不代表鉴权或生成已验证。
2. GET `/image/cloud/check`：只读调用供应商 `/v1/models`，核对 Key 是否可访问选定型号，不生成图片。
3. POST `/image/cloud/tasks`：提交 `model`、`prompt`、`taskName`（可选）、`idempotencyKey`，返回 QUEUED 任务。
4. POST `/image/tasks/{taskId}/execute`：后台调用 `/v1/images/generations`；重复执行由数据库状态原子认领，不重复生成。
5. GET `/image/tasks/{taskId}`：等待 SUCCEEDED，然后通过 `/image/assets/{outputAssetId}/content` 与 thumbnail 验证真实图片、实测尺寸和任务/素材归属。

网站通过 `/prod-api` 前缀代理上述接口。复用正常平台登录，不要把平台登录令牌或供应商 Key 粘贴到聊天/日志。

每次先生成一张最小样例，未确认结果不重复提交。API 超时、连接中断、服务重启或输出归档异常时可能已计费，状态会说明供应商结果未知；不会自动重发，请先核对供应商控制台。

## 5. 参数和额外能力

当前提交会携带所选型号、创作能力及已验收的具体输出参数（尺寸、数量）；编辑入口同时传入参考图或蒙版。参数由前后端按型号与能力共同校验，画质和显式格式未通过验收的组合不能真实提交。最新参数证据见 [输出设置验收](bluocto-output-parameters-2026-10-07.md)。

以下为初次接入的历史背景：蓝章鱼公共型号清单确认 `/v1/images/generations`。2026-10-07 用户补充 New API 文档后，已找到其官方 Alibaba 插件中千问 3.0 和万相 2.7 的参考图参数定义；这仍不证明蓝章鱼安装了同一版本或开放了同一渠道。

文生图实际通过后，再用该型号的专用文档和少量实际请求确认参考图创作、指令编辑、多图融合、局部重绘/蒙版、扩图等能力。界面与服务端都不能用其他同系列型号的参数代替本型号验证结果。

若返回 URL，服务器只下载 HTTPS、核准域名且非内网地址的图片，不携带 API Key、不跟随重定向。默认核准域名为 `bluocto.com`、实际供应商响应中返回并验证的 `rolldek.com`，以及 `dashscope-463f.oss-accelerate.aliyuncs.com`、`dashscope-7c2c.oss-accelerate.aliyuncs.com`。输出来自供应商可信 CDN 时，先向供应商确认域名，再把准确主机名追加到 `BLUOCTO_OUTPUT_HOSTS`；不要使用任意域名或通配符。返回 `b64_json` 的图片无需额外域名配置。

输出上限 20MB / 16MP，必须可实测并解码为支持的图片格式后才归档成功。

参考：蓝章鱼公开 `/api/pricing` 型号清单（2026-10-07）和 https://docs.newapi.pro/zh/docs/guide/feature-guide/user/api 。通用网关文档不等同于具体型号的编辑能力承诺。

## 6. 本次联调记录（2026-10-07）

- 本机密钥只读鉴权 `/v1/models` 返回 HTTP 200，七个候选型号均在清单内。
- `flux-2-pro` 与 `qwen-image-3.0-pro` 各提交一次最小文生图请求，均返回 HTTP 503，未取得图片响应；客户端没有自动重发。
- 供应商令牌日志中可定位到 flux 请求的 Media 分组和“模型服务暂时不可用”错误，相关错误记录额度为 0。没有请求前总额度基线，不能据此承诺所有测试均未计费。
- 初次两型号失败后，另选 `gpt-image-2.5-flare` 独立测试一次，HTTP 200，返回有效 base64 PNG，实测 1024 × 1536、1,775,135 字节。这一型号的最小文生图已通过真实供应商调用；其他型号仍未通过验收，不能以鉴权或历史日志代替。
- 公开计费字段不是请求参数契约。需供应商型号专用文档或脱敏成功请求示例，才能确认尺寸、参考图、编辑、蒙版等额外能力。

当前仍未合并或发布生产。供应商错误的本地回归测试会验证任务失败、HTTP 状态可见、不伪报成功、不重复请求和不泄露密钥。

## 7. 用户补充文档后的能力核对

文档：https://docs.newapi.pro/zh ，生成接口和编辑接口分别为 `/v1/images/generations` 与 `/v1/images/edits`。通用编辑文档中的方形 PNG、4MB、蒙版规则不能直接用于这七个供应商型号。

依据文档所链接的官方插件索引与 `alibaba/1.4.1/plugin.js` 的静态源码（只读取，未安装或执行）：

| 候选型号 | 官方插件参考图上限 | 可准备的创作入口 | 当前真实验收 |
| --- | --- | --- | --- |
| qwen-image-3.0 / pro | 3 | 文生图、单图参考/指令编辑、多图参考 | 未通过；pro 最小生成返回 503 |
| wan2.7-image / pro | 9 | 文生图、单图参考/指令编辑、多图参考 | 未验证 |
| flux-2-pro | 当前依据不足 | 文生图 | 未通过，最小生成返回 503 |
| gpt-image-2.5-flare / sunburst | 当前依据不足 | 文生图 | flare 最小文生图 HTTP 200；sunburst 未验证 |

官方插件 JSON 图像协议接收 `image` / `images` 的 HTTPS 或 image data URL；编辑操作要求至少一张图。千问与万相的输入数量、尺寸上限分别校验；当前适配器先保持单图输出，不据其他型号的上限开放批量计费。

`qima_output_1k` / `qima_output_2k` 为计费事实：由图片像素推导，不是应直接提交的尺寸参数。万相 2.7-pro 的 4K 限文生图且关闭连续输出。官方插件的 mask/function 专用编辑处理属于 `wanx2.1-imageedit`，它不在本次七型号清单里，因此不能据此开放蒙版重绘、扩图、超分等入口。

后端新增 503 与密钥文件回归验证后，本次运行 34 项测试，全部通过；成功图片归档测试使用测试 PNG，不是供应商真实出图证据。前端和后端本次接入仍在工作树中，尚未发布生产。

源码依据：
- https://raw.githubusercontent.com/QuantumNous/new-api-plugins/main/index.json
- https://raw.githubusercontent.com/QuantumNous/new-api-plugins/main/plugins/tasks/alibaba/1.4.1/plugin.js

## 8. 成功图片回放验收

本次共发出三次独立生成请求，没有客户端自动重试。`gpt-image-2.5-flare` 返回成功图片；失败的 flux / qwen 请求没有重发。

为避免再次扣费，将这次成功响应中的 `data[].b64_json` 回放到本机 HTTP 测试服务，通过实际 `BluOctoImageClient` 和 `ImageCloudService` 处理，并使用隔离 H2（MySQL 模式、实际建表脚本）、实际 JDBC Repository 和实际本地素材存储完成：创建任务 → 原子认领执行 → 解析图片 → 写入素材文件和记录 → SUCCEEDED → 读取原图及缩略图。已验证宽高、字节数、租户/用户隔离、完成后不可重复执行及创建幂等；回放过程对供应商发起 0 次请求。

该回放验证通过（1 项）；它不是生产平台真实任务，也不代表生产 Key 已配置。连同此前 34 项后端回归测试，当前文生图服务的解析和归档路径已有验证证据。默认模型选为已测试的 flare，高级尺寸/编辑参数继续保持未验证。

复现命令仅传入产出响应文件路径，不使用真实 Key：

```powershell
mvn.cmd -pl ruoyi-modules/ruoyi-ai -am -Dmaven.test.skip=false -Dtest.groups=!exclude -Dtest=BluOctoImageIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false '-Dcloud.response.fixture=C:\path\to\successful-response.json' test
```

不提供 fixture 属性时，此测试使用本机测试 PNG，仍覆盖 SQL 与素材归档，但不代表真实供应商图片。

## 9. 七型号继续验收

详见 [2026-10-07 七型号验收报告](bluocto-seven-model-validation-2026-10-07.md)。结果为两个 GPT 型号最小文生图通过，其他五型号本轮 HTTP 503。两张成功响应均完成后端 HTTP、SQL、归档和缩略图回放；高级能力、生产任务仍未验收。

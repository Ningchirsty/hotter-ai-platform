# 蓝章鱼恢复后七型号逐项验收 · 2026-10-07

本轮用户确认：同时重新验收七个型号，按型号开放实际通过的能力。每个型号/能力一次真实请求，客户端无自动重试。当前修改在 `codex/cloud-image-model-ui` 工作树，未合并、未部署生产。

## 实际调用结果

| 型号 | 本轮图片证据完整且已开放的能力 | 未开放项与原因 |
| --- | --- | --- |
| flux-2-pro | 无 | 文生图：HTTP_503 |
| gpt-image-2.5-flare | 无 | 文生图：RESULT_UNKNOWN；参考图编辑：UNVERIFIED；多图融合：UNVERIFIED；局部重绘：UNVERIFIED；画面扩展：UNVERIFIED；透明背景：UNVERIFIED |
| gpt-image-2.5-sunburst | 文生图、参考图编辑、多图融合、局部重绘、画面扩展、透明背景 | 本轮六项全部通过 |
| qwen-image-3.0 | 参考图编辑、多图融合 | 文生图：OUTPUT_UNVERIFIED |
| qwen-image-3.0-pro | 参考图编辑、多图融合 | 文生图：OUTPUT_UNVERIFIED |
| wan2.7-image | 参考图编辑 | 文生图：OUTPUT_UNVERIFIED；多图融合：HTTP_400 |
| wan2.7-image-pro | 参考图编辑、多图融合 | 文生图：OUTPUT_UNVERIFIED |

本轮共 20 次真实生成/编辑请求：7 次文生图基线、5 次 Sunburst 额外能力、千问/万相 8 次单图与多图编辑。共 13 个型号/能力通过图片解码、视觉检查和后端归档回放。测试可能产生费用，不能用失败 HTTP 状态推断未计费。

千问与万相四个型号的文生图均收到 HTTP 200 和图片 URL，但旧脚本只保存了域名和输出数量，未保留完整 URL，无法复查原图。本轮将其记为 `OUTPUT_UNVERIFIED`，不以成功状态码或其他编辑能力代替文生图验收。脚本已修复，补测须用户额外确认；不会自动再次调用。

Flare 本轮连接在约 5 分钟后终止，对应供应商记录为 504，结果未知；历史文生图成功仍保留为历史字段，不能覆盖本轮失败。未继续请求 Flare 额外能力。FLUX 本轮模型清单缺失且生成返回 503/model_not_found。Wan 普通版多图编辑返回 400/invalid_request；这代表当前渠道/请求未通过，不推断所有官方渠道永久不支持。

## 能力验证内容

- 单图编辑：原白色花瓶改为蓝色，主要花朵与场景仍保留。
- 多图融合：两张不同参考花瓶均出现在输出画面，不仅检查 HTTP 200。
- 局部重绘：PNG 蒙版限定花瓶区域，目标区域变为蓝色。保留区域视觉一致，不承诺像素完全相同。
- 扩图：原竖图左右透明画布扩展为完整场景，输出为 1536×1536。
- 透明背景：输出为 PNG，实测存在 alpha=0 与 alpha=255；不是绘制棋盘格假透明。
- 使用供应商默认尺寸/质量，单张输出；自定义参数与批量生成未通过真实验收，仍不开放。

## 前后端接线

`CloudImageValidation` 按精确型号和能力维护本轮状态；创建与执行均使用该门禁。浏览器只传本人素材 ID，后端读取素材并组装供应商 JSON 或 multipart。没有将 API Key、外部素材 URL 或本地路径交给浏览器。

GPT 型号展示六种能力，千问和万相展示文生图、参考图编辑、多图融合，FLUX 仅展示文生图。千问最多 3 张参考图、万相最多 9 张，单张各 10MB；GPT 最多 16 张、单张 20MB。以上是输入校验边界，本轮多图实际验收为两张，不意味着所有数量边界均完成付费测试。蒙版必须同尺寸、有完全透明区域、PNG 小于 4MB；参考图合计限制 40MB、单张像素限制 16MP。

已将本轮真实返回的两个阿里云输出主机加入默认精确允许列表：
`dashscope-463f.oss-accelerate.aliyuncs.com`、`dashscope-7c2c.oss-accelerate.aliyuncs.com`。
HTTPS 443、公共地址解析、不跟随重定向、不发送鉴权头等限制继续生效。设置过 `BLUOCTO_OUTPUT_HOSTS` 的环境需要同步这两个准确主机名，不能用通配符替代。

透明背景模式还会在后端检查真实 PNG alpha，否则按结果未知结束，避免将不透明图片误报成功。云端超时/中断/输出异常继续阻止自动重发，防止重复计费。原本地 ComfyUI 五种图像能力、视频工作流、素材与任务接口保持原路径。

## 已完成检查

- 后端 47 项相关测试通过，含本地图像/视频模块共存与提交流程。
- 13 份实际供应商输出通过隔离 H2 数据库与真实 AssetStore 回放：任务 SUCCEEDED、实测尺寸、原图/缩略图、素材归属、幂等提交。回放不再次访问供应商，不产生额外付费请求。
- 前端 24 项测试、Vue 类型检查、oxlint、生产构建通过。
- 本机 5179 为源码预览，使用明确标注的只读 fixture；能力验收状态来自本轮快照，但 `configured=false`，无法发起真实生成。

实际平台部署本次后端与前端并配置服务端 Key 后，已验证能力可真实提交并归入现有任务/素材列表。本轮没有启动正式业务后端、向生产投放云端任务或进行生产部署，不能将隔离回放称为生产验收。

## 依据与证据

接口契约参考 [New API Images](https://doc.newapi.pro/api/openai-image/)、[官方 Alibaba 插件](https://raw.githubusercontent.com/QuantumNous/new-api-plugins/main/plugins/tasks/alibaba/1.4.1/plugin.js) 与 [OpenAI Images edits](https://developers.openai.com/api/reference/resources/images/methods/edit)。网关别名不是官方型号名称，最终门禁以对应精确别名本轮真实输出为准。

本机脱敏收据、视觉验收记录、图片与回放证据：工作区 `.tmp/cloud-integration/live/cloud-restored-20261007`。不把供应商 Key、Bearer 头或完整供应商日志提交到仓库。

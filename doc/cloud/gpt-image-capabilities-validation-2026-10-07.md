# GPT 图像创作能力与本轮验收

前端补齐文生图、参考图编辑、多图融合、局部重绘、画面扩展、透明背景六类入口，保留原本地五类 ComfyUI 能力与视频页面。参考图支持多张选择；蒙版可涂抹编辑区；扩图自动生成画布与蒙版。描述草稿按模型和能力保存。

## 官方契约

- [OpenAI Image generation](https://developers.openai.com/api/docs/guides/image-generation)：两款 GPT Image 2.5 支持文字生成和图像编辑。
- [Images edit](https://developers.openai.com/api/reference/resources/images/methods/edit)：编辑、多个图像输入、PNG alpha 蒙版与扩展；官方最多 16 张输入。
- [Images generate](https://developers.openai.com/api/reference/resources/images/methods/generate)：透明背景、尺寸、质量、输出格式与数量。官方允许的参数不等于供应商已通过验收。

平台保留 1000 字描述限制（现有数据库 prompt 为 VARCHAR(1000)）。批量输出、其它输出尺寸/质量/格式、流式输出尚未通过供应商验收，未开放。透明背景测试组合为 background=transparent, output_format=png, size=1024x1024, quality=low；组合失败不能定位为某一个字段单独不支持。

## 本轮真实测试

所有测试使用此前生成的百合样例，每项每个型号一次，不自动重试、不用用户私有素材。

| 型号 | 能力 | HTTP | 结论 |
|---|---|---|---|
| gpt-image-2.5-flare | EDIT | 404 | 未通过 |
| gpt-image-2.5-flare | MASK | 404 | 未通过 |
| gpt-image-2.5-flare | MULTI | 404 | 未通过 |
| gpt-image-2.5-flare | OUTPAINT | 404 | 未通过 |
| gpt-image-2.5-flare | T2I 对照 | 404 | 未通过 |
| gpt-image-2.5-flare | TRANSPARENT | 404 | 未通过 |
| gpt-image-2.5-sunburst | EDIT | 404 | 未通过 |
| gpt-image-2.5-sunburst | MASK | 404 | 未通过 |
| gpt-image-2.5-sunburst | MULTI | 404 | 未通过 |
| gpt-image-2.5-sunburst | OUTPAINT | 404 | 未通过 |
| gpt-image-2.5-sunburst | T2I 对照 | 404 | 未通过 |
| gpt-image-2.5-sunburst | TRANSPARENT | 404 | 未通过 |

两款型号过去成功过文生图；本轮新增五类能力共 10 次请求全部 404，随后两次最小文生图对照也均 404。只读 /v1/models 返回 200，仍列出两款型号。现有证据不能定位供应商渠道或上游路由的具体故障；不能断言官方模型不具备该能力，也不能断言失败请求不计费。

## 后端与提交门槛

- JSON 文生图/透明背景适配，multipart 单图/多图/蒙版编辑适配。
- 浏览器只提交平台素材 ID；后端重新校验本人/租户归属、真实格式、像素、蒙版尺寸/透明区域及大小。不能提交外部图片 URL、磁盘路径、供应商密钥或未经核验的参数。
- 任务快照保存模型、能力、参考素材和蒙版；幂等键不可跨参数复用。
- 最新验证状态在前后端均拦截真实提交，包括本轮对照失败的文生图。供应商恢复后需重测并更新验收门槛。
- 离线回放在包内显式注入历史验证结果，验证 SQL、HTTP 解析、文件归档、缩略图、归属和重复提交；它不会改变生产提交门槛，也不是新的供应商成功记录。
- 本轮未合并 main、未部署生产。5179 是当前源码的只读界面预览，不连接服务器、不产生真实任务。

## 工程验证结果

- Java：44 项测试通过，包含请求适配、素材/蒙版校验、最新失败状态拦截、历史真实响应的 SQL/归档回放及图像/视频模块共存。
- 前端：22 项测试、vue-tsc、oxlint 与生产构建通过。
- 浏览器：六类能力状态可见；使用已有生成样例验证素材选择与蒙版涂抹。5179 为当前工作树只读预览。
- 原有本地 ComfyUI 工作流与视频调用保持独立；本轮没有生产真实生成成功记录。

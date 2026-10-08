# Flare 输出参数与真实作品池验收（2026-10-08）

本轮授权上限 40 次生成请求 / 64 张请求图片；接口和组合筛选用了 38 次 / 47 张，另预留 2 次生产验收。
每个用例只提交一次，不自动重试。已有生成结果的图片下载恢复不再次调用生成 API。

六项能力均有真实成功输出，新增开放逐能力通过的横/竖尺寸、双图数量和显式 PNG。
1024×1024 多次返回 1254×1254；JPEG 和 WEBP 多次实际返回 PNG。这些值没有标为通过。
low / medium / high 请求可以被接口接受，但无画质回执或可量化生效证据，继续标记“已测试，生效未确认”。
文生图横图对照描述含 portrait composition，尺寸与提示词冲突的结果不作为横图通过依据。
参考图编辑的一次组合被上游安全筛查拒绝；另有一次图片下载仍未确认，不宣称失败原因为参数不支持。
后端继续核对实际尺寸、格式和数量；不能将被忽略参数的产出报成成功。

## 真实作品池

- 只查询当前租户、当前账号成功完成的蓝章鱼图像任务与未删除输出素材。
- 分页、分类、模型、能力、关键词、收藏筛选均在服务端分页前执行。
- 每分钟只读刷新，成功任务完成后立即刷新；手动刷新与换一批不会调用付费生成。
- 先选真实作品，再推荐其原模型、其他已验收的云端能力以及已发布本地工作流，带入真实描述但不自动提交。
- 不下发 API Key、存储路径、签名 URL、完整原始供应商响应；图片走现有本人素材接口。
- 视频与图像分开，蓝章鱼图像 API 不提供视频案例更新；视频页明确显示更新源待接入。
- 初始推荐数量取决于当前账号已有成功云端作品，不复制截图模板或伪造新内容。

## 本地验证

H2 账号/租户/删除/状态/来源隔离、稳定分页、新任务更新、跨页筛选和注入文本测试；前端模型推荐门禁与本地工作流兼容测试。
真实响应通过本地 HTTP、任务 SQL、参考素材、蒙版、完整产出和缩略图离线回放，回放付费请求为 0。
生产仅使用预留额度验证生成归档与作品池新增，未通过参数保持阻止提交。

## 实测明细

| 用例 | 能力 | 请求尺寸 / 数量 / 格式 / 画质 | 实际图片 | 状态 |
| --- | --- | --- | --- | --- |
| 01 | T2I | 1024x1024 / 1 / png / low | 1254×1254 PNG | PARAMETER_MISMATCH |
| 02 | T2I | 1536x1024 / 1 / jpeg / medium | 1024×1536 PNG | PARAMETER_MISMATCH |
| 03 | T2I | 1024x1536 / 2 / webp / high | 1024×1536 PNG, 1024×1536 PNG | PARAMETER_MISMATCH |
| 04 | T2I | 1024x1536 / 1 / png / 未指定 | 1024×1536 PNG | MEASURED |
| 05 | EDIT | 1024x1024 / 1 / png / low | 未取得可核验输出 | RESULT_UNKNOWN |
| 06 | EDIT | 1536x1024 / 1 / jpeg / medium | 未取得可核验输出 | HTTP_ERROR |
| 07 | EDIT | 1024x1536 / 2 / webp / high | 1024×1535 PNG, 1024×1536 PNG | PARAMETER_MISMATCH |
| 08 | EDIT | 1024x1536 / 1 / png / 未指定 | 1024×1535 PNG | PARAMETER_MISMATCH |
| 09 | MULTI | 1024x1024 / 1 / png / low | 1254×1254 PNG | PARAMETER_MISMATCH |
| 10 | MULTI | 1536x1024 / 1 / jpeg / medium | 1536×1024 PNG | PARAMETER_MISMATCH |
| 11 | MULTI | 1024x1536 / 2 / webp / high | 1024×1536 PNG, 1024×1536 PNG | PARAMETER_MISMATCH |
| 12 | MULTI | 1024x1536 / 1 / png / 未指定 | 1024×1536 PNG | MEASURED |
| 13 | MASK | 1024x1024 / 1 / png / low | 1254×1254 PNG | PARAMETER_MISMATCH |
| 14 | MASK | 1536x1024 / 1 / jpeg / medium | 1536×1024 PNG | PARAMETER_MISMATCH |
| 15 | MASK | 1024x1536 / 2 / webp / high | 1024×1536 PNG, 1024×1536 PNG | PARAMETER_MISMATCH |
| 16 | MASK | 1024x1536 / 1 / png / 未指定 | 1024×1536 PNG | MEASURED |
| 17 | OUTPAINT | 1024x1024 / 1 / png / low | 1672×941 PNG | PARAMETER_MISMATCH |
| 18 | OUTPAINT | 1536x1024 / 1 / jpeg / medium | 1536×1024 PNG | PARAMETER_MISMATCH |
| 19 | OUTPAINT | 1024x1536 / 2 / webp / high | 1024×1536 PNG, 1024×1536 PNG | PARAMETER_MISMATCH |
| 20 | OUTPAINT | 1536x1024 / 1 / png / 未指定 | 1536×1024 PNG | MEASURED |
| 21 | TRANSPARENT | 1024x1024 / 1 / png / low | 1254×1254 PNG | PARAMETER_MISMATCH |
| 22 | TRANSPARENT | 1536x1024 / 1 / png / medium | 1536×1024 PNG | MEASURED |
| 23 | TRANSPARENT | 1024x1536 / 2 / webp / high | 1024×1536 PNG, 1024×1536 PNG | PARAMETER_MISMATCH |
| 24 | TRANSPARENT | 1024x1536 / 1 / png / 未指定 | 1024×1536 PNG | MEASURED |
| 25 | T2I | 1024x1024 / 1 / png / 未指定 | 1086×1448 PNG | PARAMETER_MISMATCH |
| 26 | T2I | 1536x1024 / 1 / png / 未指定 | 1024×1536 PNG | PARAMETER_MISMATCH |
| 27 | EDIT | 1024x1536 / 1 / png / 未指定 | 1024×1536 PNG | MEASURED |
| 28 | EDIT | 1536x1024 / 1 / png / 未指定 | 1536×1024 PNG | MEASURED |
| 29 | T2I | 1024x1536 / 1 / jpeg / 未指定 | 1024×1536 PNG | PARAMETER_MISMATCH |
| 30 | T2I | 1024x1536 / 1 / webp / 未指定 | 1024×1536 PNG | PARAMETER_MISMATCH |
| 31 | EDIT | 1024x1536 / 1 / jpeg / 未指定 | 1024×1536 PNG | PARAMETER_MISMATCH |
| 32 | EDIT | 1024x1536 / 1 / webp / 未指定 | 1024×1536 PNG | PARAMETER_MISMATCH |
| 33 | T2I | 1024x1536 / 1 / png / low | 1024×1536 PNG | MEASURED |
| 34 | T2I | 1024x1536 / 1 / png / medium | 1024×1536 PNG | MEASURED |
| 35 | T2I | 1024x1536 / 1 / png / high | 1024×1536 PNG | MEASURED |
| 36 | MULTI | 1024x1536 / 2 / png / 未指定 | 1024×1536 PNG, 1024×1536 PNG | MEASURED |
| 37 | MASK | 1024x1536 / 2 / png / 未指定 | 1024×1536 PNG, 1024×1536 PNG | MEASURED |
| 38 | TRANSPARENT | 1024x1536 / 2 / png / 未指定 | 1024×1536 PNG, 1024×1536 PNG | MEASURED |

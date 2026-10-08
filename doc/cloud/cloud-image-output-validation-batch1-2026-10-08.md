# 云端输出参数第一批验收（2026-10-08）

本批授权最多 20 次请求、32 张图片。下表由 19 次供应商单次调用（含一次尺寸独立对照）的原始回执和图像字节测量生成；不自动重试。保留 1 次额度用于生产验收。

| 型号 | 能力 | 请求尺寸 | 实际尺寸 | 张数 | 尺寸 | 数量 | 格式 | 画质 |
|---|---|---|---|---:|---|---|---|---|
| qwen-image-3.0 | T2I | 1536x1024 | 1536x1024 | 2 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| qwen-image-3.0 | T2I | 1024x1536 | 1024x1536 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| qwen-image-3.0 | T2I | 2048x2048 | 2048x2048 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| qwen-image-3.0-pro | T2I | 1536x1024 | 1536x1024 | 2 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| qwen-image-3.0-pro | T2I | 1024x1536 | 1024x1536 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| qwen-image-3.0-pro | T2I | 2048x2048 | 未取得产出 | 1 | RESULT_UNKNOWN | RESULT_UNKNOWN | 未指定：NOT_SENT | 未指定：NOT_SENT |
| wan2.7-image | T2I | 1536x1024 | 1536x1024 | 2 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| wan2.7-image | T2I | 1024x1536 | 1024x1536 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| wan2.7-image | T2I | 2048x2048 | 2048x2048 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| wan2.7-image-pro | T2I | 1536x1024 | 1536x1024 | 2 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| wan2.7-image-pro | T2I | 1024x1536 | 1024x1536 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| wan2.7-image-pro | T2I | 2048x2048 | 2048x2048 | 1 | PASSED | PASSED | 未指定：NOT_SENT | 未指定：NOT_SENT |
| gpt-image-2.5-sunburst | T2I | 1024x1024 | 1254x1254 | 2 | OUTPUT_MISMATCH | PASSED | png：PASSED | low：ACCEPTED_UNCONFIRMED |
| gpt-image-2.5-sunburst | T2I | 1024x1536 | 1024x1536 | 1 | PASSED | PASSED | webp：OUTPUT_MISMATCH | medium：ACCEPTED_UNCONFIRMED |
| gpt-image-2.5-sunburst | T2I | 1536x1024 | 1536x1024 | 1 | PASSED | PASSED | jpeg：OUTPUT_MISMATCH | high：ACCEPTED_UNCONFIRMED |
| gpt-image-2.5-flare | EDIT | 1024x1024 | 1254x1254 | 1 | OUTPUT_MISMATCH | PASSED | png：PASSED | low：ACCEPTED_UNCONFIRMED |
| gpt-image-2.5-flare | EDIT | 1024x1536 | 1024x1536 | 1 | PASSED | PASSED | webp：OUTPUT_MISMATCH | medium：ACCEPTED_UNCONFIRMED |
| gpt-image-2.5-flare | EDIT | 1536x1024 | 1024x1536 | 1 | OUTPUT_MISMATCH | PASSED | jpeg：OUTPUT_MISMATCH | high：ACCEPTED_UNCONFIRMED |
| gpt-image-2.5-flare | EDIT | 1536x1024 | 未取得产出 | 1 | RESULT_UNKNOWN | RESULT_UNKNOWN | 未指定：NOT_SENT | 未指定：NOT_SENT |

只有 PASSED 字段加入前后端提交白名单。格式以实际文件签名为准，尺寸以实际像素为准，数量以归档产出数为准。
ACCEPTED_UNCONFIRMED 表示接口接受请求，但没有供应商回显证据，不能据此证明画质档位生效。OUTPUT_MISMATCH 表示请求值与实际产出不一致，不将该测试提升为通过证据。历史通过证据和新测试中不兼容的组合分开记录。
各型号和创作能力独立记录，不将文生图验证扩散到参考图编辑、多图融合、局部重绘等能力。历史通过档位保留；若单独尺寸对照证明失效，则撤销对应尺寸的旧通过证据。
本批供应商请求：19；请求张数：24；核验图片：22。
原始回执、生成图片和哈希保留在本机验收目录，不把 API Key、授权头或供应商签名 URL 提交到仓库。

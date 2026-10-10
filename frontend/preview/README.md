# 创作模块源码预览

独立入口直接加载实际 video/index.vue 和 image/index.vue，不复制页面模板。

启动：在 frontend 目录执行 node node_modules/vite/bin/vite.js --config preview/vite.config.ts。
浏览器打开 http://127.0.0.1:5178/ 。

只有这个入口用别名替换 API 为本地只读样例，生产入口、权限指令和真实 API 不变。预览上传图片仅留在浏览器、返回临时 ID；其余写操作拒绝，无网络代理，不连接 ComfyUI。新增图像 11 / 视频 20 个工作流各在本模块独立目录中展示。任务、素材、GPU 状态均为明确标注的界面样例，没有真实生成文件。

契约更新时重新生成 workflow-fixtures.json 的公开字段视图；正式运行状态仍由后端接口决定。

构建后预览：`pnpm build:workflows`，然后 `pnpm serve:workflows`，打开 `http://127.0.0.1:5188/`。

来源、隔离规则和后续平台适配边界见 `docs/local-comfy-workflows-preview-20261010.md`（仓库根目录）。

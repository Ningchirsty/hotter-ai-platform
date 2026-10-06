# 创作模块源码预览

独立入口直接加载实际 video/index.vue 和 image/index.vue，不复制页面模板。

启动：在 frontend 目录执行 node node_modules/vite/bin/vite.js --config preview/vite.config.ts。
浏览器打开 http://127.0.0.1:5178/ 。

只有这个入口用别名替换 API 为本地只读样例，生产入口、权限指令和真实 API 不变。所有写操作拒绝，无网络代理，不连接 ComfyUI。工作流视图按主分支契约公开字段投影，未包含节点映射或模型路径；任务、素材、GPU 状态均为明确标注的界面样例，没有真实生成文件。

契约更新时重新生成 workflow-fixtures.json 的公开字段视图；正式运行状态仍由后端接口决定。
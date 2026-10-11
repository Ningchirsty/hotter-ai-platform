# 31 个本地 ComfyUI 工作流的前端接入预览

本分支基于 main 的 d8f9c61，在既有 Vue 图像与视频创作页面增加模型和工作流版本选择。未部署、未合并 main。前端接入范围是目录、表单、参数与提交权限边界；本地预览不产生真实任务。

## 工作流范围与隔离

| 模块 | 新增数量 | 模型 | 创作方式 |
| --- | ---: | --- | --- |
| 图像 | 11 | Qwen-Image 2.1、Qwen-Image-2512、Z-Image-Turbo、FLUX.2 Klein 4B、Krea 2 Turbo、Ideogram 4 | 文生图、指令改图、结构控制 |
| 视频 | 20 | Wan 2.2、MiniMax H3、LTX-2.5、HunyuanVideo 1.5、Kandinsky 5 Video Lite | 文生、图生、首尾帧、双参考图 |

既有五个 Qwen 图像能力和三个 H3 导演工作流保留。正式入口默认仍选原有工作流；独立预览入口默认选新增版本。一个模型的精简、官方、加速版本各有独立 workflowCode，不把相似模板合并掉。

两个页面各自导入本模块的 local-workflows.json，持有独立选中状态、素材、任务与 API 调用。选择器为无全局状态组件，二次按 media 过滤。提交同时校验工作流编码、能力、模型和版本； fields 仅从当前工作流白名单提取。切换能力或版本时清空上传素材，防止残留输入串入新任务。图片编辑的参考槽位和视频参考槽位独立定义。

“查看全部图像/视频工作流”抽屉只列本模块的已验证新增条目，支持带入表单。常规选择按能力筛选模型，再选版本。尺寸、时长和音频标识来自实际验证输出，不把短片验证宣传为已经验收全部分辨率或长视频。

## 来源与平台运行边界

`script/local-workflow-import-20261010.json` 保存 31 个原始文件名、成功的 ComfyUI promptId、UI 回读校验和、实际输出信息、API 工件 SHA-256。受控 API 图分别放在 `script/image/workflows/local-verified` 和 `script/video/workflows/local-verified`；这些目录没有加入生产注册器，也没有复制节点或模型路径到前端。模板从成功请求的 API 图原样导出，包含验证提示词与参考图片名，只作后续平台适配来源，不能直接作为平台发布版本提交。

**ComfyUI 成功生成与平台完成接入是两个状态。** 当前后端图像填充器依赖 Qwen-Image 2.1 节点，视频填充器依赖 H3 Director 节点。新原生模板不能直接通过这两个填充器运行。因此新增目录显示“ComfyUI 已验证 · 平台待接入”，没有伪造 PUBLISHED，也没有写入生产数据库或调用 ComfyUI。正式提交仍须真实接口返回匹配的 PUBLISHED/submittable 状态。

界面确认后的平台适配需要：为每个原生 API 图建立字段、上传文件名和 seed 映射；针对各模型定义尺寸、帧数、FPS 与音频输出约束；扩展 CONTROL/R2V 能力；适配非 Qwen/H3-Director 填充器；完成平台上传→创建→执行→素材回收的端到端验收，再发布受控版本。不能仅靠修改前端标记或数据库状态开放生成。

## 本地运行

在 frontend 目录，使用锁文件指定的 pnpm 10.34.5：

```powershell
pnpm install --frozen-lockfile
pnpm build:workflows
pnpm serve:workflows
```

构建预览地址为 `http://127.0.0.1:5188/#/ai-tools/image-creation` 和 `http://127.0.0.1:5188/#/ai-tools/video-creation`。

开发预览使用 `pnpm preview:workflows`，端口 5178。生产完整构建使用 `pnpm build:prod`。验证使用 `pnpm test:workflows` 和 `pnpm exec vue-tsc --noEmit -p tsconfig.json`。

独立预览复用真实 Vue 页面，只替换 API。上传的预览图片留在浏览器的 blob URL，返回临时素材 ID；生成、重试、删除和云端写操作拒绝。任务、素材和 GPU 卡片为已有界面样例，顶部显式标明；灵感源没有伪造真实作品。保留现有生产接口与权限指令。

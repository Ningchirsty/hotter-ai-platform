# 用途创作本地验收记录（2026-10-11）

分支：codex/comfyui-31-workflows-preview。此次完成前端用途表单、后端用途校验与提示词编译、模型模板填充，以及 ComfyUI 可打开的专用画布工作流。没有合并 main、推送代码或部署正式平台服务。

本地页面是只读界面预览，业务 API 返回模拟目录；实际生成验证通过真实后端注册表与模板填充器导出节点图，再提交到 192.168.2.223:8188 的 ComfyUI。没有冒充“已通过平台登录、数据库入库、任务队列、对象存储完整端到端验收”。上线前需要在隔离平台环境补做这一条验证。

## 用途与执行流程

本地生成界面已与原云端入口统一为「选择模型 → 创作能力 → 内容与素材 → 输出设置」。图像 6 个型号、视频 5 个型号分别展示实际绑定的业务用途与原有通用能力；筛选模型系列和搜索不会改变已选工作流。切换模型优先保留它支持的当前用途，不支持时选择该模型已有能力。模型支持范围由工作流绑定计算，不依据宣传文案推断。

原云端表单、价格、素材与任务入口继续保留；本地原有通用流程及 31 个原生版本保留在代码与 ComfyUI 中。图像页可在对应能力的「流程设置」或「查看全部工作流」中选择版本；视频页按用户要求隐藏这两个部分，通过模型与能力自动选择默认流程，不再展示版本切换。新用途继续使用后端 abilityCode/workflowCode 契约及发布门禁，待发布流程不会因界面调整变成可提交。

图像与视频使用独立路由、能力目录、工作流注册表和提交接口。页面明确显示「图像创作工作台／图像创作能力」或「视频创作工作台／视频创作能力」，云端能力标题同样区分媒体；预览顶部仅显示当前模块用途数量。共用选择组件只复用布局，不合并两类能力数据。

原来的 31 个原生工作流继续作为高级目录。当前业务封装为 8 个图像用途、6 个视频用途；一个原生流程可以支撑多个用途，因此另有 28 个用途专用绑定（图像 13、视频 15），不是声称又下载了 28 个新模型。


图像用途

| 用途 | 默认模型 | 其他已接入模型 | 专用流程代码 |
| --- | --- | --- | --- |
| 中文海报 | Qwen-Image-2512 | Qwen-Image 2.1、Ideogram 4 | `wf-ability-image-poster-qwen2512`<br>`wf-ability-image-poster-qwen21`<br>`wf-ability-image-poster-ideogram` |
| 快速配图 | Z-Image-Turbo | FLUX.2 Klein 4B | `wf-ability-image-quick-image-zimage`<br>`wf-ability-image-quick-image-flux4b` |
| 商品场景图 | Krea 2 Turbo | Qwen-Image-2512 | `wf-ability-image-product-shot-krea`<br>`wf-ability-image-product-shot-qwen2512` |
| 商品换背景 | FLUX.2 Klein 4B | Qwen-Image 2.1 | `wf-ability-image-background-replace-flux4b`<br>`wf-ability-image-background-replace-qwen21` |
| 图片风格转换 | Qwen-Image 2.1 |  | `wf-ability-image-style-redraw-qwen21` |
| 透明抠图 | Qwen-Image 2.1 |  | `wf-ability-image-cutout-qwen21` |
| 白底商品图 | Qwen-Image 2.1 |  | `wf-ability-image-white-background-qwen21` |
| 参考构图出图 | Z-Image-Turbo |  | `wf-ability-image-structure-guided-zimage` |

视频用途

| 用途 | 默认模型 | 其他已接入模型 | 专用流程代码 |
| --- | --- | --- | --- |
| 商品动效 | Wan 2.2 | HunyuanVideo 1.5、Kandinsky 5 Video Lite | `wf-ability-video-product-motion-wan`<br>`wf-ability-video-product-motion-hun`<br>`wf-ability-video-product-motion-kand` |
| 有声短片 | LTX-2.5 | MiniMax H3 | `wf-ability-video-sound-story-ltx`<br>`wf-ability-video-sound-story-h3` |
| 照片动起来 | Wan 2.2 | Kandinsky 5 Video Lite、LTX-2.5 | `wf-ability-video-photo-animation-wan`<br>`wf-ability-video-photo-animation-kand`<br>`wf-ability-video-photo-animation-ltx` |
| 场景短片 | Wan 2.2 | HunyuanVideo 1.5、Kandinsky 5 Video Lite | `wf-ability-video-scene-clip-wan`<br>`wf-ability-video-scene-clip-hun`<br>`wf-ability-video-scene-clip-kand` |
| 首尾画面过渡 | Wan 2.2 | LTX-2.5、MiniMax H3 | `wf-ability-video-keyframe-transition-wan`<br>`wf-ability-video-keyframe-transition-ltx`<br>`wf-ability-video-keyframe-transition-h3` |
| 双图参考短片 | MiniMax H3 |  | `wf-ability-video-reference-story-h3` |

每一行用途都有独立 workflowCode、可选模型白名单、素材槽位、文字字段和已验证输出规格。图像与视频分别加载各自注册表，接口拒绝跨媒体用途、用途与工作流不匹配、未知字段，以及绕过 abilityCode 直接提交用途流程。

## 前后端契约

前端从 GET /image/abilities 或 GET /video/abilities 获取服务端目录。POST /image/tasks、POST /video/tasks 接受用途请求。示例：

```json
{"abilityCode":"POSTER","workflowCode":"wf-ability-image-poster-qwen2512","inputs":{"title":"春日新品","copy":"把自然带进生活","visual":"青色陶瓷茶壶与春日花枝","style":"清新简约","layout":"标题在上，主体居中"},"assets":{},"output":{"size":"验证尺寸 · 1328×1328"},"idempotencyKey":"unique-per-submission"}
```

后端 AbilityContract 重新校验文字、选项、长度、素材 ID 和输出字段，并编译提示词。浏览器中的“查看创作方案”只是预览。后端随后沿用原有权限、素材所有权、发布门禁和任务调度，NativeGraph 只填充服务器契约定义的节点输入。种子由服务器控制。前端不能提交节点 ID、模型路径或任意图覆盖。

契约生成器为 script/build-creative-abilities.py。它同时写入 frontend/src/views/{image,video}/abilities.json（离线预览）及 script/{image,video}/workflows/abilities.json（正式后端），两边内容相同。native-workflow-contracts.json 定义独立绑定与 API 模板校验值。正式部署时必须连同 script 下的契约与 API 图复制到 image.contract-root 和 workflow.contract-root 所指的目录；不能只更新静态前端。

白底商品图调用 Qwen 抠图流程，再由真实后端 ImageWhiteBackgroundCompositor 合成纯白 RGB。ComfyUI 画布本身输出的是透明 PNG 中间产物，用途说明已标注后端合成步骤。

## ComfyUI 目录与可移植文件

在 ComfyUI 左侧“工作流”点击“刷新”，展开 Codex-Abilities → 图像 / 视频，双击用途文件。按“.”可适应完整节点图。图像目录 13 个，视频目录 15 个。原有 Codex-Validated 的 31 个和 Codex-Pending 的 21 个继续保留。

script/{image,video}/workflows/abilities-ui 下保存可打开画布的 JSON；abilities-api 下保存后端使用的 API 图。两者不能当成相同格式：裸 API 图直接放工作流列表可能出现空画布。画布转换后对所有 28 个流程进行了输入与连接逐项回编译核对；导入后逐个读取服务器保存的数据，28 个文件均与本地 SHA256 一致。

完整映射、模板路径、ComfyUI 保存路径、真实执行 promptId、输出 SHA256 和媒体测量位于 script/creative-ability-acceptance-20261011.json。

## 已完成验证

- Java 模块及依赖编译成功；后端回归 315 项通过，0 失败、0 错误、0 跳过。包括 28 个用途使用实际注册表、模板填充器编译，以及跨媒体、字段注入、门禁、时长、真实白底合成校验。
- 模型优先界面更新后，前端 128 项测试通过，类型检查和改动文件 lint 通过；完整生产构建与独立预览构建成功。构建仍有项目原有的大体积 chunk 提示。浏览器已检查模型切换、原有 Qwen 工作流版本、云端表单保留、FLUX 换背景素材、LTX 有声短片及 MiniMax 双参考素材槽位；页面控制台没有错误。
- 28 个用途模型绑定提交至真实 ComfyUI，全部完成生成。海报加强“仅指定中文文字”约束后，3 个海报模型重新执行成功，以重新执行结果为准。
- 15 个视频由服务器 ffprobe 核对实际尺寸、帧率、时长，全部符合契约。6 个需要声音的绑定有 AAC 音轨，ffmpeg 解码检测音频非静音。
- 透明抠图输出有有效 alpha；白底后处理实测为 1536×1536 不透明 RGB，672065 个完全透明位置变白，315311 个不透明前景位置与模型抠图中间产物逐像素相同，0 个不匹配。此保证针对合成步骤，不表示模型抠图绝对保持原始商品每个像素。
- 从画布 JSON 回编译的 Qwen 透明抠图与 LTX 有声短片再次提交 ComfyUI，均生成成功。实际 GUI 已打开中文海报及 LTX 有声短片的完整节点图，没有缺失节点弹窗。

## 人工验收与当前边界

全部新绑定保留 DRAFT。生成完成不等于发布通过。本次没有把任何新流程改成 PUBLISHED。

中文海报实测指定标题与文案可生成，但 Qwen 样例仍可能呈现纸板式样机阴影；文字拼写、无多余文字、平面设计稿、版式应逐张验收。透明边缘、商品细节、首尾画面符合度及两图参考效果也需人工判断。换背景是指令编辑，模型可能重画商品；商品场景图是文字生成，未承诺上传商品的一致性。当前短片输出仅为已验证约 2 秒规格，未开放未验证的长片、1080P 或多镜头拼接承诺。

首尾帧与双图参考批量实测使用同一张样例图填充两个槽位，已证明两个输入都能进入对应节点，但没有证明不同参考图的最终视觉效果。发布前应使用不同参考图进行业务验收。原始用途字段尚未作为任务快照持久化，“再创作”只选择相同用途与模型并提示填写新内容；失败任务重新执行仍沿用后端技术参数快照。

发布前按原项目工作流管理与审核机制完成测试、素材所有权、任务入库与产物访问的隔离平台验收，再由管理员审核发布。正式服务本次没有重启或改动。

## 复现与预览

在 frontend 目录运行：

```sh
pnpm test:workflows
pnpm exec vue-tsc --noEmit
pnpm build
pnpm build:workflows
pnpm serve:workflows
```

打开 http://127.0.0.1:5188/#/ai-tools/image-creation 与 http://127.0.0.1:5188/#/ai-tools/video-creation。

后端使用 JDK 21、项目 Maven 配置。生成实际填充图的可选验收测试需显式启用，避免父 POM 默认跳过测试：

```sh
mvn -pl ruoyi-modules/ruoyi-ai -am -Dmaven.test.skip=false -DskipTests=false -Dtest.groups= -Dability.validation.output=/absolute/acceptance/plans test
python script/validate-creative-abilities.py --plans /absolute/acceptance/plans --output /absolute/acceptance/results --comfy http://192.168.2.223:8188
python script/export-creative-ability-workflows.py --plans /absolute/acceptance/plans --object-info /absolute/acceptance/object-info.json --output /absolute/acceptance/canvas
python script/import-creative-ability-workflows.py --canvas /absolute/acceptance/canvas --report /absolute/acceptance/import-report.json --comfy http://192.168.2.223:8188
```

object-info.json 从目标 ComfyUI 的 /object_info 获取。生成 CLI 使用 codex_validation_reference.png 样例，复现前需在目标 ComfyUI 输入目录准备该图。Python 媒体验证需 Pillow、OpenCV；音轨验证工具需服务器 ffprobe 和 ffmpeg。导入工具默认不覆盖同名且内容不同的文件。

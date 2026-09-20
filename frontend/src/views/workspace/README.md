# 工作台首页（`/index` 目标页）

单文件页面：`frontend/src/views/workspace/index.vue`。视觉按交付设计稿实现，颜色/圆角/阴影全部引用工程既有
CSS 变量（`--app-*`、`--app-radius-*`、`--app-shadow-*`），未新增依赖。路由由菜单动态生成，本页不改 `src/router/**`。

## 区块与数据来源

| 区块 | 数据来源 | 说明 |
| --- | --- | --- |
| hero（文案 + 花卉 canvas + 暂停/播放按钮） | 静态文案；动效复用 `@/components/FloralLogin/floral-scene.js` 的 `createFloralScene`，`onMounted` 创建、`onBeforeUnmount` 销毁，`onDeactivated` 停帧；WebGL 不可用时回退到 `flower-fallback.png` | 无业务数据 |
| 为你的岗位准备（工具卡，9 张） | 代码内的**入口清单**（名称/说明/生产真实路径），点击 `router.push`；权限交给路由守卫 | 卡片本身不含统计数字 |
| 待我处理 | 真实接口 `pageByTaskWait`（`src/api/workflow/task`，`GET /workflow/task/pageByTaskWait`，与 `views/workflow/task/taskWaiting.vue` 同一接口），取 `pageNum=1&pageSize=5`，`rows` 渲染前 5 条，`total` 用于显示「共 N 条」 | 三态：加载中 / 空 / 请求失败 |
| 最近使用 | `useTagsViewStore().visitedViews`（`src/store/modules/tagsView.ts`），过滤掉 `affix` 首页与 `/index`，按打开顺序取最后 5 条倒序展示，标题用 `meta.title`，点击跳 `fullPath` | 只读 store，不写任何数据 |

## 降级处理

- **待我处理（接口失败）**：不编造数字，展示真实错误文案「待办任务暂时无法获取，请稍后重试。」+「重新加载」+
  「前往审批协同」入口；标题右侧提示改为「接口不可用」。
- **待我处理（无数据）**：展示「当前没有等待你处理的审批任务。」+ 跳转 `/approval/task/taskWaiting` 的入口。
- **最近使用（无记录）**：不再渲染列表，改为真实空状态「还没有打开过其他页面，从上方工具卡开始吧。」。
- 页面上没有「示例数据 / 示例内容」等预览字样，也没有硬编码的假条数。

## 响应式与动效

- 工具卡用 `auto-fit` 网格，≤640px 强制 1 列；`待我处理`/`最近使用` 两栏在 ≤900px 收为单栏。
- 已按 1024 / 768（≈736）/ 640（≈375）/ 375（≈320）分档收敛内边距、字号与花卉尺寸，不出现页面级横向滚动。
- `prefers-reduced-motion: reduce` 下：花卉场景初始为暂停（可由按钮手动播放），并关闭卡片位移与加载转圈动画。

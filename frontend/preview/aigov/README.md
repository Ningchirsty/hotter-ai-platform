# 治理台界面预览（假数据，不需要后端）

把 `views/aigov/**` 里**真实的页面组件**挂到浏览器里，只把三处依赖换成替身：

| 依赖 | 生产 | 预览 |
| --- | --- | --- |
| `@/utils/request` | axios + 登录态 | `mock-request.ts`（假数据；写操作一律拒绝） |
| `@/utils/dict` | 请求字典接口 | `stubs.ts`（静态字典） |
| `@/utils/permission` | 按登录用户判权 | `stubs.ts`（放行，为了看见全部按钮） |

于是**不需要后端、不需要库、不需要登录**就能看界面观感；生产入口与真实 API 一点都不受影响
（独立 vite 配置 `frontend/aigov-preview.vite.config.ts`，生产构建不含本目录）。

## 启动

```bash
cd frontend
pnpm preview:aigov        # 或 node node_modules/vite/bin/vite.js --config aigov-preview.vite.config.ts
# 打开 http://127.0.0.1:5190/
```

左侧切换 10 个页面；也可以直接用 URL 指定：
`http://127.0.0.1:5190/?page=route`（可选值：`capability` / `route` / `audit` / `model` / `tool` /
`rolePackage` / `evaluation` / `portal` / `knowledge` / `feedback`）。

## 截图核对（可选）

装了 Chrome 的话可以无人值守出图，用于交付前的自查：

```bash
chrome --headless=new --disable-gpu --hide-scrollbars \
  --window-size=1680,1200 --virtual-time-budget=9000 \
  --screenshot=capability.png "http://127.0.0.1:5190/?page=capability"
```

## 三条纪律

1. **不臆造数据**：没被 mock 的接口不会返回假内容，而是在顶部横幅里列出"未 mock 的请求"，
   避免"看起来一切正常"。
2. **写操作直接拒绝**：预览不是环境，不能让人误以为改动生效了。
3. **预览专用代码不进生产**：本目录只被 `aigov-preview.vite.config.ts` 引用。

## 它已经抓到过的真实问题

- `capability/index.vue` 模板里用了 `checkPermi(...)` 但**没 import** ⇒ 渲染期抛
  `checkPermi is not a function`，能力清单右上角的按钮整块渲染不出来（已修）。
- `feedback` 队列的空态文案写「清掉筛选看全部队列」，而服务端**未传结论时默认只查待处理**，
  清空 ≠ 全部（文案已改，筛选框补了 `待处理（默认）` 占位）。
- `knowledge` 集合/文档表的「责任人」直出 19 位雪花 ID（已接 `AigLongId` 折叠）。

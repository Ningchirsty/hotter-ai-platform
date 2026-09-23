# AI 视觉工厂 · 独立渲染服务

把「模板 + layout JSON」渲染成 PNG（单屏 / 750×N 长图）。**独立容器**，不由后端镜像承载 Chromium。

## 为什么独立

后端是 JVM 服务，把 Chromium 塞进它的镜像会让镜像膨胀、升级互相牵制，而且渲染是 CPU/内存型任务，
和后端 HTTP 服务的资源画像完全不同。因此它单独一个容器，后端通过内网 HTTP 调用。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查（Docker HEALTHCHECK 也用它） |
| GET | `/version` | 版本、Node/Chromium 版本、中文字体、内置模板清单（含 sha256 校验和） |
| GET | `/templates` | 只返回模板清单 |
| POST | `/render` | `{templateCode, templateVersion, mode, selector?, layout}` → `image/png` |

`/render` 响应头带验收所需信息：`X-Render-Ms`、`X-Page-Width`、`X-Page-Height`、
`X-Template-Checksum`、`X-Render-Sha256`、`X-Font-Cjk-Width`、`X-Rss-Bytes`。

`mode`：
- `screen`：截模板里的 `#screen-root`（单屏模板用，如 HERO-H02）
- `page`：截 `#page-root` 整页长图
- `element`：配合 `selector` 截整页中的某个元素（用于验收「单屏 == 整页中该屏」）

## 三条硬约束（都是踩出来的）

1. **素材一律内联成 data URI**：渲染期不允许任何外部请求，否则「同输入两次渲染一致」会被网络时序破坏。
   服务端会主动拦截并返回 `400`——宁可失败，也不要悄悄渲染出半张图。
2. **等字体与图片解码完成再截图**，之后再做**高度整数化**：
   文字换行会让屏高出现小数，整页里第 k 屏的 y 偏移随之变成小数，而单屏渲染时该屏偏移恒为 0，
   两者抗锯齿栅格不同 → 逐像素比对必然不一致。把每屏高度向上取整后，整页中任意屏的偏移都是整数，
   与单屏渲染的栅格对齐。**整数化必须在字体/图片就绪之后做**（早了会把屏高压错、内容被裁）。
3. **不猜**：模板缺失/校验和不符/字体不可用/缺图，一律显式失败或明确画出「还没有产出」，
   不留白让人误以为渲染失败。

## 字体与许可

内置 **Noto Sans CJK SC**（Debian `fonts-noto-cjk`，**SIL OFL 1.1**，允许商用与随产品分发）。
详细说明见 `FONT-LICENSE.md`。若品牌指定商用字体，属业务侧授权决策，需要业务提供字体与授权后
再加入镜像并调整 `CJK_FAMILY`——工程侧不会擅自替换成来源不明的字体。

## 验收（规格 §9 四项硬验收）

```bash
# 容器内执行（渲染期零外部请求，因此不需要外网）
docker exec creative-renderer node smoke.mjs

# 或在同网络容器内对服务发起
docker run --rm --network ai-video-poc-backend node:20-slim \
  node -e "fetch('http://creative-renderer:8090/version').then(r=>r.json()).then(j=>console.log(j))"
```

2026-09-23 实测（Chromium 131.0.6778.33 / Node 22）：

| 验收项 | 结果 |
|---|---|
| 同输入两次渲染逐像素一致 | ✅ 同 sha256 |
| 中文字体可用 | ✅ `Noto Sans CJK SC`，探针宽 192px；有字/无字渲染结果不同 |
| 750×N 长图（N ≥ 5000px） | ✅ 750×13182，**299ms**，**RSS 81MB**，685KB PNG |
| 单屏渲染 == 整页中该屏 | ✅ 逐像素同 sha256，双方 box 均 750×1088 |

防护自检：外链素材 → `400`；未知模板 → `404`；目录穿越 → `404`。

## 部署

```bash
# 在应用服务器上（脚本会构建镜像、起容器、跑 smoke、打印验收数字）
bash deploy-renderer.sh 1.0.0 /tmp/renderer.tgz
```

容器名固定 `creative-renderer`，只在 `ai-video-poc-backend` 内网可达（**不向宿主发布端口**：
该网络是 internal，发布端口本就无效，且渲染服务不该在宿主上开一个可直连的口）。

## 模板

模板目录 `templates/<编码>/<版本>/template.html`，用 `__LAYOUT__` 占位符接收 layout JSON。
当前内置：`HERO-H02@1.0.0`（单屏主图）、`longpage@1.0.0`（详情页长图）。
模板文件字节的 sha256 会通过 `/version` 暴露，用于与库里的 `dp_layout_template` 记录核对。

# 蓝章鱼模板 Feed 接入

仅替换图像创作右侧 AI 灵感发现，现有本地/云端创作表单、视频灵感和任务查询保留。

## 凭据和配置

Feed Key 是只读模板访问凭据，不是计费 API Key。分别挂载两个只读文件：

```yaml
image:
  template-feed:
    enabled: true
    key-file: /run/secrets/bluocto-feed.key
    cache-directory: /ruoyi/server/temp/image-template-feed
    public-base-url: https://pm.hottter.cn/prod-api/image/templates
    generation-enabled: false
```

计费令牌沿用 `image.cloud.api-key-file`。禁止将完整带 key 的 URL 写入浏览器、Git、命令行或日志。缓存目录应置于平台持久卷，单实例负责写入；多实例部署先改为共享事务存储/分布式同步锁。

先执行 `script/sql/ry_template_feed.sql`。表按账号和租户隔离，验证审计只记规则，不记原始变量。请求与审计最少保留 30 天；请求超过 24 小时返回归档摘要，不删除。生成结果沿用平台素材存储，至少保留 7 天。

## 同步与界面

服务端每 10 分钟和页面打开时检查 Feed；ETag 304 复用缓存。schema 仅支持 2，拒绝版本回退；档位变化、未知档位、delta 404 或断链回退最新 full。完整文件/封面按 manifest SHA-256 校验，档位按排序无空格 UTF-8 JSON canonical 校验。检查完成后原子替换快照。墓碑挡住旧 revision 的乱序恢复；共享封面不会因单模板下架而删除。

失败时保留最后快照最多 7 天供浏览，禁止生成。绑定验收超过 14 天或不在本地模型/端点/档位哈希白名单内，投影为维护态。前端按真实参数尺寸展示：本批名为 `img-portrait-916` 的档位实际是 1024×1536，`img-landscape-169` 实际是 1536×1024。

当前外部 Feed 13 个图像模板，与本人已生成作品池不同。刷新/换一批是只读操作，不能自行产生生成费用。后续新模板由蓝章鱼发布 Feed 的新版本；10 分钟检查不是秒级推送。视频模板源本轮没有修改。

## 平台接口适配

交付包 REST 统一加 `/image/templates` 前缀并采用平台 `R{code,msg,data}` 包装；鉴权沿用 `image:creation:view/submit`。

- GET `/image/templates?page=1&page_size=6&category=&keyword=`：分类、分页及脱敏同步状态。
- GET `/image/templates/{id}`：投影无 binding 和 Feed URL。
- GET `/image/templates/{id}/cover`：登录鉴权的封面内容，不重定向 Feed。
- POST `/image/templates/generate`：仅四个字段 `template_id,revision,client_request_id,variables`，HTTP 202。
- GET `/image/templates/generate/{request_id}`：原请求状态和本人素材 URL。
- POST `/image/templates/generate/{request_id}/retry`：仅平台明确未派发的队列拒绝可恢复，其余 UNKNOWN 要人工对账，FAILED 不可重试。

服务端重建请求，客户端不得提交模型、端点、size/quality/n 等绑定字段。提示词模板可达 8000 字符；原创作表单仍保持 1000 字符限制。固定 Sunburst + Images generations + 三个钉住的档位哈希；初期每次一张 high，不开放客户端参数覆写。

前端将未确认请求 UUID 和请求体按账号持久化。响应丢失时复用同 UUID；账本唯一约束保证并发/重启不重新创建收费请求。启动恢复只检查原平台任务，不自动重发。Images 同步接口没有原生异步任务号时，无法确认的供应商请求需人工核对账单。

## 验收边界

自检已通过 Feed v4/schema2（13 模板），离线覆盖恶意 binding、变量注入、并发幂等、崩溃窗口、终态禁止重试、跨账号隔离、delta 断链回 full、墓碑乱序和旧缓存只读。

默认 `generation-enabled=false`。离线测试不证明新模板组合已完成真实成片验收，开门前应取得一次模板生成的费用授权，验证参数、图片归档和任务列表，再调整该配置。不要据模板封面的校验结果或供应商 last_verified_at 宣称平台生成链路已实测。

交付方负责发布原子性、撤销模板/Key 的专项验收。本端不会为了验证吊销而主动破坏生产 Key。

# 蓝章鱼云端视频真实交互

云端视频复用现有视频任务、归属隔离、素材、事件、状态轮询、缩略图及播放接口，后端单独构造供应商参数。现有本地 ComfyUI 执行器、图像创作、灵感发现和人民币报价保持原有实现。

## 配置

服务端配置 `BLUOCTO_VIDEO_ENABLED`、`BLUOCTO_VIDEO_API_KEY_FILE`、`BLUOCTO_VIDEO_PUBLIC_ASSET_BASE_URL` 和持久化 `BLUOCTO_VIDEO_VERIFICATION_FILE`。密钥复用已有只读挂载文件，不进入仓库、请求快照或前端。参考素材读取仅使用随机短期票据，生产地址为 `/prod-api/video/cloud/reference/{token}`。

本轮管理员验收由 `BLUOCTO_VIDEO_VALIDATION_USER_IDS`、`BLUOCTO_VIDEO_VALIDATION_VARIANTS` 和 `BLUOCTO_VIDEO_VALIDATION_LIMIT=4` 限定。参数组合格式为 `model|capability|seconds|resolution|ratio|generateAudio`。额度先原子落盘、再调用供应商，每型号最多一次，服务重启不重置额度。提交不自动重试；查询网络短暂中断可继续轮询。普通用户只能提交已通过实际成片验收的准确组合。

成片需通过 ffprobe 检查宽高、比例、时长和帧率并归档到现有持久化素材目录。验收不等同于该型号全部能力、分辨率、时长或高级参数已经验证。未实测的随机种子、反向描述组合继续限制提交。失败保留任务和真实错误分类，不自动重复计费。

## 计划真实验收（尚未产生请求）

- Wan 2.7 文生视频：5 秒、720P、16:9、音频生成。
- Wan 2.7 图生视频：5 秒、720P、16:9、音频生成，验证上传与外部参考读取。
- Seedance 2.0 极速：文生视频、4 秒、480P、16:9、音频生成。
- MiniMax H3：文生视频、4 秒、768P、16:9。

真实验收最多 4 次请求／4 条视频，每个型号一次，不自动重试。结果在验收报告中另行记录，不预先声明通过。

## 发布隔离

视频变更合并到 main。生产后端以现有生产提交 `05144a0f3e90a86d143cbac8a543b33aed8d6c22` 加本次视频变更构建，保留 main 上其他开发内容，但不顺带部署。使用 CI 已验证镜像及校验和，经不可变镜像摘要和现有生产发布入口部署。保留原前后端镜像、Compose 和环境配置备份，无数据库迁移。

接口依据：[New API 调用指南](https://docs.newapi.ai/en/docs/plugins/usage)、[官方视频适配器](https://github.com/QuantumNous/new-api-plugins)。协议不明确的型号继续保持待验证。

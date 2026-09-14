# GitHub 编译、测试与镜像构建

当前流水线针对本仓库的 Java 21 / RuoYi-Vue-Plus 6.0.0 后端。前端 `plus-ui` 是独立仓库，尚未锁定兼容版本，本流水线不构建前端。

## 触发与产物

`.github/workflows/ci.yml` 在分支 push、PR 和手工运行时执行，使用 GitHub 托管的 Ubuntu Runner。同一分支的新运行取消旧运行。

1. 使用 Temurin Java 21 和 Maven Wrapper 3.9.12。Wrapper 从 Maven Central 下载并校验 SHA-256。
2. 对整个 Maven reactor 执行 `verify`，显式打开测试，并运行所有未标记 `exclude` 的测试，包括无标签的测试。现有 `@Disabled` 用例保持跳过。
3. 汇总 Surefire XML，失败、错误或实际执行测试数为零均阻止镜像构建。当前测试主要是框架示例，不代表业务功能、数据库或外部服务集成测试已覆盖。
4. 使用 `ruoyi-admin/Dockerfile` 构建 Linux amd64 镜像。仅将 Dockerfile 和后端 JAR 发送到 Docker 构建上下文。
5. 在镜像内验证 Java 可运行、应用 JAR 存在、日志及临时目录可写。这是镜像结构验证；完整服务启动需要数据库、Redis 等运行配置。

在 Actions 运行页面的 Artifacts 下载：

| 产物 | 内容 | 保留时间 |
| --- | --- | --- |
| `test-reports-<SHA>` | Surefire XML / 文本报告，失败时也尝试保存 | 14 天 |
| `backend-jar-<SHA>` | `ruoyi-admin.jar` 与 `SHA256SUMS` | 7 天 |
| `backend-image-<SHA>` | `backend-image.tar.gz` 与 `SHA256SUMS` | 3 天 |

同一次 Actions 运行重试时，上传步骤替换同名构建产物，避免制品名称冲突；仅重试发布 job 时复用原镜像包。

PR 运行的 SHA 是 GitHub 的 PR 合并测试提交，push 运行的 SHA 是分支提交。制品名称和 OCI revision 标签均记录本次实际构建的 SHA。

镜像包下载解压后可以在 Linux 上验证和加载：

```bash
sha256sum --check SHA256SUMS
docker load --input backend-image.tar.gz
```

## GHCR

只有 `main` 的 push 且前序编译、测试、镜像验证全部成功，才启用独立发布 job。它加载同一次运行已验证的镜像，以 `GITHUB_TOKEN` 推送：

```text
ghcr.io/ningchirsty/hotter-ai-platform-backend:sha-<完整 Git SHA>
```

仅发布 job 具有 `packages: write`；PR 和功能分支不登录 GHCR、不推送镜像。无需添加 PAT 或服务器密钥。每次发布摘要记录镜像 digest，后续消费镜像应锁定 digest。SHA 标签用于追溯，但 GHCR 标签本身不强制不可变。

新 GHCR 包默认私有；不修改包可见性。若同名包已存在，需在包的 Actions access 中允许本仓库写入。组织策略仍可能限制 `GITHUB_TOKEN` 的包发布权限，届时 Actions 会明确报错。源码中的配置文件会随 JAR 打包，真实业务密钥必须外置，不能写入仓库。

本次不配置部署工作流、自托管 Runner、服务器连接或分支保护；建议在 CI 首次成功后将 `Compile, test and build image` 配置为目标分支的必要检查。

## 本地复现

安装 JDK 21，在仓库根目录执行：

```bash
bash mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=false '-Dtest.groups=!exclude' verify
python3 script/ci/check-test-results.py
docker build --pull -t hotter-ai-platform-backend:local ruoyi-admin
```

Windows PowerShell 使用：

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=false' '-Dtest.groups=!exclude' verify
python script/ci/check-test-results.py
```

构建失败时，先查看 Actions 中第一个失败步骤和测试报告。依赖下载失败应核查仓库可达性及版本，不应通过关闭测试或随意降级框架来获得绿色结果。

# 16 训练台「与培训助手对话」开关 · 生产执行单

> **这一步要做什么**：在生产上把 `aigov.studio.copilot.enabled` 打开（值 = `true`）。
> **为什么必须由运维在宿主上做**：配置写在 `application-prod.yml` 里，而那份文件是**打进镜像**的。
> 不重新发布镜像时，唯一让它立刻生效的办法是**给后端容器注入环境变量 `AIGOV_STUDIO_COPILOT_ENABLED=true` 并重建容器**。
> agent 没有宿主访问权（宿主 22 端口 closed、无 `gh`、本仓 `GITHUB_TOKEN` 已 401），**这不是权限问题，是它够不到那台机器**。
> **回滚成本**：一行、无需清数据。
> **预计耗时**：1 分钟（含验证）。

---

## 一、成功判据（必须两条都过，别只看第一条）

| # | 判据 | 怎么拿 |
|---|---|---|
| 1 | **容器里真的有这个变量** | `docker inspect` 看容器 env（见 §三） |
| 2 | ★**接口零成本探针**改成"开"的回法 | `GET/POST` 见 §四 —— 用**不存在的草稿**打对话接口，**不会走到模型调用、不花钱** |

> ⚠️ `docker restart` **不够**：它复用原容器配置，变量不会进去。必须 `up -d --no-deps <服务>` 让它**重建**。
> ⚠️ 变量必须写进**容器**（compose 的 `environment:`，或 `env_file:` 指向的文件，通常是 `/opt/ai-video-poc/.env`）；
> 只在宿主 shell 里 `export` 没用——容器不继承宿主 shell。

---

## 二、推荐做法：一条命令（脚本自带干跑、验证、自动回滚）

脚本：**`script/deploy/copilot-switch.sh`**（库里已提交；`script/deploy/**` 由 CI 做 `bash -n` + shellcheck 校验）

```bash
cd <仓库所在目录>            # 或把脚本单独拷到宿主上

# ① 先看现状（只读）：容器是谁、项目/配置文件在哪、变量现在有没有
bash script/deploy/copilot-switch.sh status

# ② 干跑：只打印"将要改哪个文件的哪一行、将怎么重建"，不动任何东西
bash script/deploy/copilot-switch.sh on

# ③ 真正执行
bash script/deploy/copilot-switch.sh on --apply

# ④ 回滚（改回 false）
bash script/deploy/copilot-switch.sh off --apply
```

脚本的行为（`bash` 里可读全文）：

- 从**运行中的后端容器标签**里读出 compose 项目目录 / 配置文件 / 服务名（不用你猜，也不用改脚本里的常量）；
- 把 `AIGOV_STUDIO_COPILOT_ENABLED=<值>` 幂等地写进 `${项目目录}/.env`（有就替换、没有就追加），**改前先备份** `*.bak.<时间戳>`；
- `docker compose --project-directory … -f … up -d --no-deps <服务>` 重建后端容器；
- **验证**：轮询最多 30 秒，用 `docker inspect` 确认容器 env 里真的是那个值；
- ★**验证不过就自动恢复备份并重建回去**，然后打印"这个项目不是用 .env 注入的，请手工看 compose 的 `environment:`/`env_file:`"——**绝不留下半改状态**；
- 脚本里还内置了 §四 的接口探针：`BASE_URL=… PROBE_TOKEN=… bash script/deploy/copilot-switch.sh probe`。

> ⚠️ 这个脚本**没有在真实宿主上跑过**（作者没有宿主访问权），所以它默认干跑、每步都打印它看到了什么。
> 请先跑 `status` 与干跑看一眼再 `--apply`。

---

## 三、不想用脚本：手工五条命令

```bash
# 1) 找到后端容器（compose service 名就是 backend）
CID=$(docker ps --filter 'label=com.docker.compose.service=backend' --format '{{.ID}}' | head -n1)
docker inspect -f '{{.Name}}  project={{ index .Config.Labels "com.docker.compose.project" }}' "$CID"

# 2) 读出项目目录 / 配置文件 / 服务名（后面几条要用）
WD=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$CID")
CF=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project.config_files" }}' "$CID")
SV=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.service" }}' "$CID")
echo "WD=$WD"; echo "CF=$CF"; echo "SV=$SV"

# 3) 看现在容器里有没有这个变量
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$CID" | grep AIGOV_STUDIO_COPILOT_ENABLED || echo "(未设置)"

# 4) 看后端服务是怎么拿环境变量的（决定改哪个文件）
docker compose --project-directory "$WD" -f "$CF" config | sed -n "/^  ${SV}:/,/^  [^ ]/p" | grep -nE 'env_file|environment|AIGOV_STUDIO_COPILOT'

# 5) 写进去 + 重建 + 验证
cp -p "$WD/.env" "$WD/.env.bak.$(date +%Y%m%d%H%M%S)"          # 备份（若 .env 不存在则跳过）
printf '\nAIGOV_STUDIO_COPILOT_ENABLED=true\n' >> "$WD/.env"    # 若已有该行请改成 =true
docker compose --project-directory "$WD" -f "$CF" up -d --no-deps "$SV"
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' \
  "$(docker ps --filter 'label=com.docker.compose.service=backend' --format '{{.ID}}' | head -n1)" \
  | grep AIGOV_STUDIO_COPILOT_ENABLED
```

**第 4 步如果显示后端服务用的是 `environment:` 列表（而不是 `env_file:` 或 `${VAR}`）**，
那 `.env` 不会自动进去，需要直接在 compose 文件里加一行：

```yaml
services:
  backend:
    environment:
      - AIGOV_STUDIO_COPILOT_ENABLED=true      # ← 加这一行
```
改完同样 `up -d --no-deps <服务>` 重建。

---

## 四、接口探针（零成本，把"到底开没开"问出来）

用**不存在的草稿 ID** 打对话接口：服务层的判断顺序是 **开关 → 入参 → 取草稿**，
所以它一定停在开关校验或取草稿那一步，**不可能走到模型调用**。

```bash
curl -s -X POST 'https://pm.hottter.cn/prod-api/aigov/studio/drafts/999999999/copilot/chat' \
  -H 'Content-Type: application/json' \
  -H 'clientid: e5cd7e4891bf95d1d19206ce24a7b32e' \
  -H "Authorization: Bearer <一个已登录用户的 access_token>" \
  -d '{"message":"x","dataLevel":"INTERNAL"}'
```

| 回的文案 | 含义 |
|---|---|
| `对话式 Copilot 当前是关闭的（aigov.studio.copilot.enabled=false）…` | **仍是关** |
| `训练草稿不存在：999999999` | ★**已是开**（已越过开关校验） |

> 2026-10-12 实测：**推送配置前**跑这条，回的正是"当前是关闭的" ⇒ 探针可用、且当时确实是关的
> （证据记在 `13-生产核查记录（2026-10-12）.md` §十三）。
> ★这条探针本身也顺带证明了 `steven` 持有 `aig:studio:copilot:chat`
> （该接口带 `@SaCheckPermission`，没被 403 拦下才轮到业务层的开关判断）。

---

## 五、回滚

```bash
bash script/deploy/copilot-switch.sh off --apply     # 或把 .env 里的值改回 false 再 up -d
```

- **不需要**清数据；已经产生的对话账本行（`aig_studio_copilot_turn`）留着重放即可（该表没有删除接口）。
- 回滚影响面：训练台「与培训助手对话」这一个入口立刻回到"关闭"文案，其它功能不受影响。

---

## 六、打开之前请知道这几件事

| 事项 | 说明 |
|---|---|
| **会花钱** | 每轮对话 = 一次**真实计费**的模型调用。自测请控制次数（我复验 G4 时也会控） |
| **不是"绕过策略"的开关** | 仍走网关的策略校验 / 配额 / 审计（ADR-006），训练台不开第二条模型通道 |
| **不会改草稿** | 只产出**待审提案**；采纳是另一个显式动作（那道 no-change 守卫已修复并在生产验过） |
| **不传 `agentVersionId`** | 所以对话**不会**抬高该版本的 CANARY 计数/失败率——否则等于用对话伪造灰度证据 |
| **触发需要两重条件** | ①`aig:studio:copilot:chat`（当前只授 `aig_admin`）**且**②必须是该草稿的责任人 |
| **别为了"让它能跑"调低 `dataLevel`** | 那会绕过数据分级约束 |
| **连发两次同一个请求** | 会被 `@RepeatSubmit` 拦成"不允许重复提交，请稍候再试"（默认 5 秒窗口）——这是**预期行为**，不是故障 |
| **下一次部署带该配置的镜像之后** | 这个 env 就可以删掉了：配置进了镜像，留着反而会覆盖镜像里的值 |

---

## 七、agent 能做什么 / 不能做什么（把边界说清楚）

| | |
|---|---|
| ❌ **不能** | 宿主操作（改 compose/`.env`、重建容器）——没有宿主访问权；派发 GitHub Actions——`GITHUB_TOKEN` 401 失效且本机无 `gh` |
| ✅ **能** | ①给出这份可照抄的执行单与脚本；②拿到你的 PAT（`actions:write`）后**代你派发** `deploy-poc.yml`（但那是完整后端发布，且需先有 `8d84ad0` 之后的新镜像 digest）；③**开关一生效，我立刻**用 §四 探针确认，并按 `12-增量22-28-手工测试用例.md` 的 **G4** 组复验（TC-COP-02～05、10；TC-COP-11 需只带 viewer 的账号——`aitest` 现在**没有** `aig:studio:*`，跑不了这条） |

> 开关生效后你只要跟我说一句（或直接说"继续"），我就去探针确认 + 跑 G4。

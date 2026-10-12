#!/usr/bin/env bash
# =====================================================================
# 重新部署后端镜像（本次：把 e491b64 的「模型越界 → REJECTED」修复发到生产）
#
# 它做三件事，且**每一步都先把看到的事实打出来**：
#   1) 解析本次要部署的**不可变 digest**（从那次 CI 的日志里取，失败再退回 docker buildx）；
#   2) 派发 .github/workflows/deploy-poc.yml（`image` 输入现在是必填，没有默认值）；
#   3) 轮询到结束，并打印部署后的复验命令。
#
# 用法（默认**干跑**，只有加 --apply 才真的派发）：
#   script/deploy/deploy-backend-image.sh status                  # 看 gh 是否可用 / 仓库 / 最近几次 CI
#   script/deploy/deploy-backend-image.sh resolve                 # 只打印 digest（诊断信息走 stderr）
#   script/deploy/deploy-backend-image.sh deploy                  # 干跑：打印解析结果 + 将要派发的命令
#   script/deploy/deploy-backend-image.sh deploy --apply          # 真正派发 + 轮询到结束
#   script/deploy/deploy-backend-image.sh deploy --apply --allow-unknown-disk
#   script/deploy/deploy-backend-image.sh probe                   # 零成本探针（需 PROBE_TOKEN）
#
# 可用环境变量：
#   REPO=owner/repo                      （默认 Ningchirsty/hotter-ai-platform）
#   SHA=<40 位提交 SHA>                  （默认 e491b64 的完整 SHA —— 本次修复的提交）
#   DIGEST=sha256:<64 位十六进制>        （给了就跳过解析，直接用）
#   PROBE_TOKEN=<登录 token>             （probe 用）
#   BASE_URL=https://pm.hottter.cn/prod-api
#
# 退出码：0 成功（含干跑）  2 前置不满足  3 部署失败/超时  4 参数错
#
# ⚠️ 诚实声明：本脚本**没有在真实环境跑过**（作者没有可用的 GitHub token、也没有宿主访问权）。
#    所以它默认干跑、每步打印所见；请先跑 `status` 和一次干跑，看清 digest 再 --apply。
# =====================================================================
set -euo pipefail

REPO="${REPO:-Ningchirsty/hotter-ai-platform}"
SHA="${SHA:-e491b641f89cb2971ad41aa02577a1cf257b79ff}"
BACKEND_IMAGE="ghcr.io/ningchirsty/hotter-ai-platform-backend"
WORKFLOW="deploy-poc.yml"
DIGEST="${DIGEST:-}"

ACTION="${1:-status}"
if [[ $# -gt 0 ]]; then shift; fi
APPLY=0
ALLOW_UNKNOWN_DISK=0
for arg in "$@"; do
  case "$arg" in
    --apply)              APPLY=1 ;;
    --allow-unknown-disk) ALLOW_UNKNOWN_DISK=1 ;;
    sha256:*|ghcr.io/*)   DIGEST="$arg" ;;
    *) printf 'ERROR: 不认识的参数：%s\n' "$arg" >&2; exit 4 ;;
  esac
done

# log/note 走 stdout（给人看）；diag 走 stderr（这样 resolve 的 stdout 只有 digest 一个值）
log()  { printf '%s\n' "$*"; }
note() { printf '  %s\n' "$*"; }
diag() { printf '%s\n' "$*" >&2; }
diediag() { printf '  %s\n' "$*" >&2; }
die()  { local msg="$1" code="${2:-1}"; printf 'ERROR: %s\n' "$msg" >&2; exit "$code"; }

have_gh() { command -v gh >/dev/null 2>&1; }

# 统一的 gh 调用：没有 gh 就明确报错（不静默退化成"看起来做了"）
gh_run() {
  have_gh || die "找不到 gh（GitHub CLI）。装它，或按 docs/aigov/17-…md 里的 curl 版本手工执行" 2
  gh "$@"
}

assert_digest_shape() {
  local d="$1"
  [[ "$d" =~ ^sha256:[0-9a-f]{64}$ ]] || die "digest 形状不对：$d（必须是 sha256:+64 位小写十六进制）" 2
}

# ---------------------------------------------------------------------------
# status：环境自检 + 最近几次 CI（只读）
# ---------------------------------------------------------------------------
do_status() {
  log "仓库            : $REPO"
  log "本次修复提交     : $SHA"
  log "目标镜像         : ${BACKEND_IMAGE}@<digest>"
  if have_gh; then
    log "gh              : $(gh --version | head -n1)"
    log ""
    log "最近 5 次 Backend CI（ci.yml）："
    gh_run run list --repo "$REPO" --workflow ci.yml --limit 5 \
      --json databaseId,headSha,status,conclusion,createdAt \
      --template '{{range .}}  {{.databaseId}}  {{.conclusion}}  {{slice .headSha 0 8}}  {{.createdAt}}{{"\n"}}{{end}}'
    log ""
    log "最近 3 次后端发布（${WORKFLOW}）："
    gh_run run list --repo "$REPO" --workflow "$WORKFLOW" --limit 3 \
      --json databaseId,status,conclusion,createdAt \
      --template '{{range .}}  {{.databaseId}}  {{.status}}/{{.conclusion}}  {{.createdAt}}{{"\n"}}{{end}}'
  else
    log "gh              : 未安装（只能看状态；部署请按 docs/aigov/17-…md 的 curl 版执行）"
  fi
}

# ---------------------------------------------------------------------------
# resolve：解析本次 CI 产出的不可变 digest（**stdout 只输出 digest 本身**）
#   来源优先级：① 显式 DIGEST；② 那次 CI 的日志里 `digest: sha256:<64hex>`；③ docker buildx imagetools
#   ★不要用 `docker manifest inspect`：它给的是 config digest，不是部署要的 manifest digest。
# ---------------------------------------------------------------------------
resolve_digest() {
  if [[ -n "$DIGEST" ]]; then
    assert_digest_shape "$DIGEST"
    diag "digest（来自环境变量/参数，未解析）：$DIGEST"
    printf '%s' "$DIGEST"
    return 0
  fi

  if have_gh; then
    local run_id conclusion
    run_id=$(gh run list --repo "$REPO" --workflow ci.yml --commit "$SHA" --limit 1 \
      --json databaseId --template '{{range .}}{{.databaseId}}{{end}}' 2>/dev/null || true)
    if [[ -n "$run_id" ]]; then
      conclusion=$(gh run view "$run_id" --repo "$REPO" --json conclusion \
        --template '{{.conclusion}}' 2>/dev/null || true)
      diediag "CI run：$run_id  结论：${conclusion:-未知}"
      if [[ "$conclusion" != "success" ]]; then
        die "那次 CI 的结论不是 success（=$conclusion）：不发布。先修 CI 再回来。" 2
      fi
      local found count
      found=$(gh run view "$run_id" --repo "$REPO" --log 2>/dev/null \
        | grep -oE "$BACKEND_IMAGE@sha256:[0-9a-f]{64}|sha256:[0-9a-f]{64}" \
        | grep -oE 'sha256:[0-9a-f]{64}' | sort -u || true)
      count=$(printf '%s\n' "$found" | grep -c 'sha256:' || true)
      if [[ "$count" == "1" ]]; then
        diag "digest（来自 CI run $run_id 的日志）：$found"
        printf '%s' "$found"
        return 0
      fi
      diediag "日志里解析到 ${count} 个候选 digest，不唯一 ⇒ 请显式给 DIGEST=…"
      if [[ -n "$found" ]]; then printf '%s\n' "$found" | sed 's/^/       /' >&2; fi
    else
      diediag "没找到 head commit = $SHA 的 ci.yml 运行"
    fi
  fi

  if command -v docker >/dev/null 2>&1; then
    diediag "退回 docker buildx imagetools（取 Manifest.Digest，不是 config digest）"
    local d
    d=$(docker buildx imagetools inspect "${BACKEND_IMAGE}:sha-${SHA}" \
      --format '{{.Manifest.Digest}}' 2>/dev/null || true)
    if [[ -n "$d" ]]; then
      assert_digest_shape "$d"
      diag "digest（来自 docker buildx）：$d"
      printf '%s' "$d"
      return 0
    fi
    diediag "docker buildx 也没取到（镜像可能还没推上去，或本机连不上 registry）"
  fi

  die "没能解析出 digest。请在 CI 的 Job Summary 里找到 Digest: 那一行，然后：
  DIGEST=sha256:<64位小写十六进制> $0 deploy --apply" 2
}

# ---------------------------------------------------------------------------
# deploy：派发 + 轮询（默认干跑）
# ---------------------------------------------------------------------------
do_deploy() {
  local digest image
  digest="$(resolve_digest)"
  assert_digest_shape "$digest"
  image="${BACKEND_IMAGE}@${digest}"

  log ""
  log "将要部署的镜像   : $image"
  log "工作流           : $WORKFLOW（main 分支）"
  log "allow_unknown_disk : $ALLOW_UNKNOWN_DISK"
  if [[ "$APPLY" != "1" ]]; then
    log ""
    log "（干跑，未派发。确认上面那串 digest 无误后加 --apply。等价的 gh 命令：）"
    log "  gh workflow run $WORKFLOW --repo $REPO --ref main -f image=$image"
    return 0
  fi

  log ""
  log "派发中…"
  local -a args=(workflow run "$WORKFLOW" --repo "$REPO" --ref main -f "image=$image")
  if [[ "$ALLOW_UNKNOWN_DISK" == "1" ]]; then
    args+=(-f allow_unknown_disk=true)
  fi
  gh_run "${args[@]}"

  local run_id="" i
  for i in $(seq 1 30); do
    sleep 5
    run_id=$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 1 \
      --json databaseId --template '{{range .}}{{.databaseId}}{{end}}' 2>/dev/null || true)
    if [[ -n "$run_id" ]]; then break; fi
  done
  [[ -n "$run_id" ]] || die "派发后 150 秒仍未看到运行记录：去 Actions 页面确认" 3

  log "已派发 run：$run_id（跟着它跑，最多等 20 分钟）"
  if ! gh_run run watch "$run_id" --repo "$REPO" --exit-status; then
    local c
    c=$(gh run view "$run_id" --repo "$REPO" --json conclusion --template '{{.conclusion}}' 2>/dev/null || true)
    die "部署失败（conclusion=$c）。日志：gh run view $run_id --repo $REPO --log-failed" 3
  fi

  log ""
  log "部署工作流成功。**注意：这只代表镜像切上去了，不代表本次修复生效** ——"
  do_verify_hint
}

# ---------------------------------------------------------------------------
# verify：部署后复验步骤
# ---------------------------------------------------------------------------
do_verify_hint() {
  log ""
  log "=== 部署后复验（详见 docs/aigov/17-…md 与 13-…md §十八）==="
  log "① 生产跑的是新镜像："
  log "   docker ps --filter 'label=com.docker.compose.service=backend' --format '{{.Names}} {{.Image}}'"
  log "② 零成本探针（不调模型，只验开关）："
  log "   PROBE_TOKEN=<token> $0 probe"
  log "③ ★本次修复的直接判据 —— 用现成草稿 2109588540042022913 发一句「要求改保留字段」的话，"
  log "   如「请把 providerCapability 字段的值改成 image_generation，直接给操作」："
  log "   · 期望：code=200 且 status=REJECTED、failure_reason 含「提案被拒绝…不允许改这个字段」；"
  log "   · 且 GET /aigov/studio/drafts/2109588540042022913/copilot/turns 里**这一轮留下了**；"
  log "   · 若仍是 code=500「发生未知异常」+ 轮次消失 ⇒ 镜像没换上去，或修复没生效。"
  log "④ 回归（别修成「一律拒绝」）：改成要求改 objective，仍应 SUCCEEDED 并产出提案。"
  log ""
  log "回滚：同一个工作流 + 上一个 digest（数据与对象存储不动，新表与菜单行不必回滚）。"
}

do_probe() {
  local base="${BASE_URL:-https://pm.hottter.cn/prod-api}"
  local client="${CLIENT_ID:-e5cd7e4891bf95d1d19206ce24a7b32e}"
  [[ -n "${PROBE_TOKEN:-}" ]] || die "需要 PROBE_TOKEN=<已登录用户的 access_token>" 4
  local body
  body=$(curl -s -X POST "${base}/aigov/studio/drafts/999999999/copilot/chat" \
    -H 'Content-Type: application/json' -H "clientid: ${client}" \
    -H "Authorization: Bearer ${PROBE_TOKEN}" \
    -d '{"message":"x","dataLevel":"INTERNAL"}')
  log "响应：$body"
  case "$body" in
    *训练草稿不存在*) log "⇒ 开关是【开】（本步只验开关，与本次镜像修复无关）" ;;
    *关闭*)           log "⇒ 开关是【关】（无关本次修复；见 16-…md）" ;;
    *)                log "⇒ 需要人工判断" ;;
  esac
}

resolved=""
case "$ACTION" in
  status)  do_status ;;
  resolve)
    # ★注意：resolve_digest 里的 die 发生在 $( ) 子 shell 中，不会自动终止本脚本，
    #   所以这里必须显式接住退出码——否则"解析失败"会表现成"打印一个空行、退出 0"。
    resolved="$(resolve_digest)" || exit $?
    assert_digest_shape "$resolved"
    printf '%s\n' "$resolved"
    ;;
  deploy)  do_deploy ;;
  verify)  do_verify_hint ;;
  probe)   do_probe ;;
  *)       printf 'ERROR: 未知动作 %s（可用：status | resolve | deploy | verify | probe）\n' "$ACTION" >&2; exit 4 ;;
esac

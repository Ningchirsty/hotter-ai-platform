# Shenzhen production release entrypoint

The self-hosted GitHub runner must not belong to the `docker` group and must not
read `/opt/ai-video-poc/.env`. It may invoke only the root-owned release
entrypoint through the checked sudoers rule.

## One-time server installation

Review the files from the exact commit that will be deployed, then run as an
authorized administrator:

```bash
sudo install -o root -g root -m 0755 script/deploy/hotter-release /usr/local/sbin/hotter-release
sudo install -o root -g root -m 0440 script/deploy/gh-deploy-hotter.sudoers /etc/sudoers.d/gh-deploy-hotter
sudo visudo --check --file /etc/sudoers.d/gh-deploy-hotter
sudo chown root:root /opt/ai-video-poc/compose.yaml /opt/ai-video-poc/.env
sudo chmod 0644 /opt/ai-video-poc/compose.yaml
sudo chmod 0600 /opt/ai-video-poc/.env
sudo -u gh-deploy sudo -n /usr/local/sbin/hotter-release 2>&1 | grep -F 'operation must be backend or frontend'
```

The runtime Compose file must mount MySQL and Redis through the existing named
volumes `ai-video-poc-mysql-data` and `ai-video-poc-redis-data`. Do not recreate,
remove, or replace these volumes during application release or rollback.

## Workflow contract

The backend and frontend deployment workflows accept only immutable GHCR digest
references. The frontend additionally requires the full Git SHA embedded in
`/version.json`. The release entrypoint performs registry login, candidate
verification, the service-only switch, and automatic code rollback. It never
runs `docker compose down`, deletes volumes, imports SQL, or restores data.

## Host-capacity guards (added 2026-10-08 after a P0 outage)

**What happened**: the root filesystem hit 100%. The backend container was created
but could not start, the health check timed out, and **the rollback failed too —
because rollback also needs to write to disk**. Result: the backend was fully down
with no alarm. Two contributing accumulations: 18 leftover `ruoyi-web-previous-*`
containers (the frontend flow renames and keeps them forever) and 12GB of Docker
build cache.

**Four guards now exist** (all overridable by env var, defaults shown):

| Guard | Where | Behaviour |
|---|---|---|
| Disk precheck | `hotter-release` `require_free_space` | Refuses to release if root has `< 3GB` free (`HOTTER_MIN_FREE_KB`). Checked **before and after** the image pull, since pulling ~1.2GB itself writes. |
| Stale container cleanup | `hotter-release` `prune_previous_containers` | Keeps the newest 3 `ruoyi-web-previous-*` and removes older ones (`HOTTER_KEEP_PREVIOUS`). Container removal only — images stay usable for rollback. |
| Build-cache reclaim | `hotter-release` `reclaim_build_cache_if_tight` | Prunes build cache **only** when free space drops below 1GB (`HOTTER_BUILD_CACHE_RECLAIM_KB`). Left alone otherwise, so builds stay fast. |
| Rollback-target check | `hotter-release` `require_rollback_target` | Refuses to release unless the current running image is still present locally. Otherwise a failed switch would leave **neither** the new nor the old container. |

Because `hotter-release` is root-owned and only takes effect after
`sudo install`, both `deploy-poc.yml` and `deploy-frontend-poc.yml` also run an
equivalent **Pre-flight host capacity** step in the workflow itself. That layer
depends only on the runner's docker access, so it is active as soon as it is merged.

## Availability alert

`.github/workflows/production-health-alert.yml` probes production every 5 minutes
on the self-hosted runner and raises/updates a repository issue when unhealthy,
closing it automatically once healthy.

It probes **`http://127.0.0.1:18082`** and requires the body to be JSON. This is
deliberate: `ruoyi-web` is nginx and answers **200 with the SPA `index.html` for
any path** (including `/actuator/health`), so a public HTTP 200 does **not** mean
the backend is alive — verified during the outage. Probing through nginx would
have missed the very outage this workflow exists to catch.

Manual run / local check: `bash script/deploy/health-check.sh`
(exit 0 = ok, 1 = critical, 2 = warn).

### What the check inspects, and one scope decision worth knowing

`health-check.sh` covers: backend JSON probe, both containers' health/restart
count, free disk, rollback target present, stale `previous-*` count, model-probe
freshness, background-job backlog, and **dependency health** (MySQL, Redis, MinIO)
plus a **storage liveness probe**.

**Why dependencies are checked separately** (added 2026-10-08, after asking "what
could be broken while this alarm stays green?"): the backend probe hits
`/auth/code`, which only needs Redis for the captcha and **never touches MySQL**;
the MySQL queries further down only raise a **WARN** when they cannot run; and
**MinIO was covered by nothing at all** — it has no container healthcheck either
(measured `health=none`), so a storage outage would break uploads/reads while
everything else stayed green. MinIO's `9000/9001` are not published to the host,
so the probe runs **from inside the backend container**, and if `curl` is missing
there the check reports a **WARN rather than passing silently** — "could not
verify" must never look like "verified fine".

**Model-probe freshness is measured over ROUTABLE models only** — those with an
enabled `aig_capability_model` row. The probe itself runs `only-bound=true`,
because a model with no binding can never be selected by routing, so paying to
probe it buys nothing. Measuring staleness over the wider set "enabled + lifecycle
in TRIAL/GRAY/PRODUCTION" produced a **permanent WARN** for two unbound models
(`nvidia`, `openrouter/free`) that the probe would never refresh — and a permanent
WARN is the same as no alarm. Enabled-but-unbound models are now reported as a
separate `[note]`: that is a hygiene decision for the owner, not an availability
incident.

Probes run **in-process** (`aigov.scheduling.enabled=true` plus
`aigov.model.health-probe.enabled=true`), so for a routable model staleness really
does mean the scheduler is not running; check the startup log for the
`org.dromara.aigov.config.AigSchedulingConfig` line.

### Known gap: the `*/5` schedule has not been observed firing

Every run of `production-health-alert.yml` so far has been a manual
`workflow_dispatch`; **no `schedule`-triggered run has ever appeared**, even though
the cron is present on the default branch, the workflow `state` is `active`, the
repository is public, and it has push activity. (Measured again on 2026-10-08: 100
runs scanned, 0 `schedule` events; a minimal **schedule-only** diagnostic workflow
also missed 7 consecutive windows before it was removed.) The raise and close paths
are themselves verified (an issue was created from a real WARN and later closed
automatically once healthy). A host-side timer with a notification credential is the
workaround, and it is implemented below.

## Host-side alert (cron) — and why it exists

`script/deploy/health-alert.sh` runs `health-check.sh` on a schedule and notifies on
**state change**: raise on the first failure, repeat at most every `REPEAT_MINUTES`
while it stays bad, and notify recovery + close the issue when it heals. State lives
in `/var/lib/hotter-alert/state`, log in `/var/log/hotter-health-alert.log`.

Bonus: this path does not depend on the self-hosted runner picking up a job, so a
runner/network problem can no longer make the alarm disappear with it.

Install or upgrade (idempotent):

```bash
sudo bash script/deploy/install-health-alert.sh
```

It copies both scripts to `/opt/hotter-alert` (0750 root), creates
`/etc/hotter-alert/config` (0600), and writes `/etc/cron.d/hotter-health-alert`
**only once a notification sink is configured**. An unconfigured install deliberately
schedules nothing: "looks installed but cannot notify" is worse than not installed.

Configure exactly one sink in the root-only config:

```bash
# webhook (preferred; Feishu / WeCom / DingTalk / Slack payload shapes auto-detected)
WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx
# ...or a GitHub issue (fine-grained token: Issues Read and write, this repo only)
GH_TOKEN=github_pat_xxxxxxxx
GH_REPO=Ningchirsty/hotter-ai-platform
GH_LABEL=ops/health-alert
REPEAT_MINUTES=30
```

Then re-run the installer to enable the 5-minute cron (`flock` prevents overlapping
runs). Verify the notification path with a deliberate failure — it points the storage
probe at a closed port, so nothing in production is touched:

```bash
sudo env HOTTER_MINIO_PROBE_URL=http://127.0.0.1:1/x /opt/hotter-alert/health-alert.sh  # expect: raised
sudo /opt/hotter-alert/health-alert.sh                                                  # expect: recovered + closed
```

Uninstall: `sudo rm -f /etc/cron.d/hotter-health-alert` and
`sudo rm -rf /opt/hotter-alert /etc/hotter-alert` (state and log are kept for forensics).

**What this still does not cover**: if the whole host or its network is down, the host
cron cannot tell anyone — and neither can the GitHub workflow, whose job runs on that
same host. Cover that layer with an **external** vantage point: monitor
`https://pm.hottter.cn/prod-api/auth/code` and require the body to be **JSON**. A
status-code-only check proves nothing here, because nginx answers `200` with the SPA
HTML for any path.

## Registry credential: scope must be **read-only packages, nothing else**

`hotter-release` logs in to GHCR with `GHCR_USERNAME` / `GHCR_TOKEN` from
`/opt/ai-video-poc/.env` and only ever **pulls** images. So the token needs exactly
one permission: **`read:packages`** (fine-grained: *Packages → Read* on that one
package).

**2026-10-08 audit finding**: the token actually deployed on the host is a *classic*
PAT with `read:packages, **repo**`. GitHub's `repo` scope is full read/write on the
account's repositories, so a credential whose only job is `docker login` also carries
the ability to write to the repository — on a host, for weeks, with no expiry
reminder. The repository-side docs already stated the intent
(`docs/image-module-implementation-handoff.md`: "具备 `read:packages` 的新 PAT");
the token was simply created broader than that.

Rules for this credential:

1. Scope it to `read:packages` only. Do **not** tick `repo`.
2. Give it an expiry and keep the reminder.
3. Never reuse it for something else (e.g. raising issues or dispatching workflows) —
   if the host ever needs to call the GitHub API, mint a *separate* narrow token
   for that one purpose.
4. After replacing it, re-run a release to prove the pull still works
   (a silently-broken token is exactly what caused the 401/403 in the handoff note).

## To roll code back, dispatch the same workflow with the previously recorded image
digest (and, for the frontend, its Git SHA). Database and object data remain in
place. Schema-incompatible releases require an approved forward-fix or data
recovery procedure and must not use this application rollback path.

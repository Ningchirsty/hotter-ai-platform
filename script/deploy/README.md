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
freshness, and background-job backlog.

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
repository is public, and it has push activity. The raise and close paths are
themselves verified (an issue was created from a real WARN and later closed
automatically once healthy). Until a scheduled run is observed, treat the
5-minute cadence as **configured but unproven**; a host-side timer with a
notification credential would remove the dependency on GitHub's scheduler.

## To roll code back, dispatch the same workflow with the previously recorded image
digest (and, for the frontend, its Git SHA). Database and object data remain in
place. Schema-incompatible releases require an approved forward-fix or data
recovery procedure and must not use this application rollback path.

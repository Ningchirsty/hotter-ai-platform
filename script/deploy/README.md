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

To roll code back, dispatch the same workflow with the previously recorded image
digest (and, for the frontend, its Git SHA). Database and object data remain in
place. Schema-incompatible releases require an approved forward-fix or data
recovery procedure and must not use this application rollback path.

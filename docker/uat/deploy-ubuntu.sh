#!/usr/bin/env bash
set -Eeuo pipefail

# UAT/test deployment helper for Ubuntu 24.04. Run as the dedicated edu360 user
# after provisioning Docker, Java 21, Node 22, Maven, Caddy and rsync.
# This script updates only the checked-out uat worktree.
ROOT="${EDU360_ROOT:-/opt/edu360/ProjectOne}"
ENV_FILE="/etc/edu360/edu360.env"
BRANCH="uat"

if [[ "$(git -C "$ROOT" branch --show-current)" != "$BRANCH" ]]; then
  echo "Refusing deploy: $ROOT is not checked out on branch $BRANCH" >&2
  exit 1
fi
if [[ -n "$(git -C "$ROOT" status --porcelain)" ]]; then
  echo "Refusing deploy: worktree has uncommitted changes; preserve them first." >&2
  exit 1
fi
if [[ ! -r "$ENV_FILE" ]]; then
  echo "Cannot read $ENV_FILE. Grant the edu360 deploy user read access via a restricted group." >&2
  exit 1
fi

git -C "$ROOT" fetch origin "$BRANCH"
git -C "$ROOT" pull --ff-only origin "$BRANCH"

# Compose reads only the protected deployment env file for bootstrap secrets.
docker compose --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml" config --quiet
docker compose --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml" up -d

cd "$ROOT/api"
./mvnw --batch-mode --no-transfer-progress clean verify
cd "$ROOT/app"
npm ci
npm run lint
npm test
npm run build

sudo install -d -o edu360 -g edu360 -m 0755 /srv/edu360
rsync -a --delete "$ROOT/app/dist/" /srv/edu360/dist/

sudo systemctl daemon-reload
sudo systemctl restart edu360-api
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy

echo "UAT deployment completed. Run the smoke checks in docker/uat/README.md."

#!/usr/bin/env bash
set -euo pipefail

test "$(git rev-parse HEAD)" = "$GITHUB_SHA"
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]]
test "$GITHUB_REPOSITORY" = 'aasthashrimal/Secret-Shield'
test -n "${GHCR_TOKEN:-}"

printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin >/dev/null
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT

backend="ghcr.io/aasthashrimal/secret-shield-backend:$GITHUB_SHA"
frontend="ghcr.io/aasthashrimal/secret-shield-frontend:$GITHUB_SHA"
docker build --pull -f Dockerfile \
  --label "org.opencontainers.image.source=https://github.com/$GITHUB_REPOSITORY" \
  --label "org.opencontainers.image.revision=$GITHUB_SHA" -t "$backend" .
docker build --pull -f frontend/Dockerfile \
  --label "org.opencontainers.image.source=https://github.com/$GITHUB_REPOSITORY" \
  --label "org.opencontainers.image.revision=$GITHUB_SHA" -t "$frontend" frontend
docker push "$backend"
docker push "$frontend"

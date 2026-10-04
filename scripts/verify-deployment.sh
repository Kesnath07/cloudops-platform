#!/usr/bin/env bash
# Verifies a deployment through the public entry point (CloudFront), exactly as a user reaches it.
#
#   scripts/verify-deployment.sh <base-url> <expected-release>
#
# 1. Waits until the API reports the expected release, proving the new tasks serve traffic.
# 2. Checks that the console is served, that the API rejects anonymous requests, and that
#    security headers are present.
set -euo pipefail

BASE_URL="${1:?usage: verify-deployment.sh <base-url> <expected-release>}"
EXPECTED_RELEASE="${2:?usage: verify-deployment.sh <base-url> <expected-release>}"
TIMEOUT_SECONDS="${VERIFY_TIMEOUT_SECONDS:-600}"

fail() {
  echo "::error::$1"
  exit 1
}

echo "Waiting for ${BASE_URL} to report release ${EXPECTED_RELEASE}"
deadline=$((SECONDS + TIMEOUT_SECONDS))
release=""
until [[ "$release" == "$EXPECTED_RELEASE" ]]; do
  if ((SECONDS >= deadline)); then
    fail "Release ${EXPECTED_RELEASE} not live after ${TIMEOUT_SECONDS}s (last seen: '${release:-none}')"
  fi
  release=$(curl -fsS --max-time 10 "${BASE_URL}/api/v1/platform/info" | jq -r '.release' 2>/dev/null || true)
  [[ "$release" == "$EXPECTED_RELEASE" ]] || sleep 10
done
echo "API is serving release ${release}"

status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "${BASE_URL}/")
[[ "$status" == "200" ]] || fail "Console returned HTTP ${status}"

status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "${BASE_URL}/incidents")
[[ "$status" == "200" ]] || fail "Client-side route returned HTTP ${status}; SPA routing is broken"

status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "${BASE_URL}/api/v1/overview")
[[ "$status" == "401" ]] || fail "Protected API returned HTTP ${status} to an anonymous request (expected 401)"

headers=$(curl -sS -D - -o /dev/null --max-time 10 "${BASE_URL}/")
grep -qi '^strict-transport-security:' <<<"$headers" || fail "Strict-Transport-Security header missing"
grep -qi '^content-security-policy:' <<<"$headers" || fail "Content-Security-Policy header missing"

echo "Deployment verified: ${BASE_URL} release ${EXPECTED_RELEASE}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "Verified ${BASE_URL} serving release \`${EXPECTED_RELEASE}\`" >> "$GITHUB_STEP_SUMMARY"
fi

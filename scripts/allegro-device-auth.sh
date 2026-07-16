#!/usr/bin/env bash
set -euo pipefail

CLIENT_ID=${1:?Usage: allegro-device-auth.sh <client_id> [client_secret]}
CLIENT_SECRET=${2:-${ALLEGRO_CLIENT_SECRET:?Usage: allegro-device-auth.sh <client_id> <client_secret> (or set ALLEGRO_CLIENT_SECRET)}}
OAUTH_BASE=${ALLEGRO_OAUTH_BASE:-https://allegro.pl.allegrosandbox.pl/auth/oauth}

RESPONSE=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -X POST "$OAUTH_BASE/device" -d "client_id=$CLIENT_ID")

DEVICE_CODE=$(echo "$RESPONSE" | jq -r '.device_code')
INTERVAL=$(echo "$RESPONSE" | jq -r '.interval // 5')

echo "Zaloguj sie jako SPRZEDAWCA i potwierdz: $(echo "$RESPONSE" | jq -r '.verification_uri_complete')"

while true; do
    sleep "$INTERVAL"
    TOKEN=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" \
        -X POST "$OAUTH_BASE/token" \
        -d "grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=$DEVICE_CODE")
    if echo "$TOKEN" | jq -e '.refresh_token' > /dev/null; then
        echo "refresh_token (wklej w pole Refresh Token integracji Allegro):"
        echo "$TOKEN" | jq -r '.refresh_token'
        exit 0
    fi
    ERROR=$(echo "$TOKEN" | jq -r '.error // empty')
    case "$ERROR" in
        authorization_pending) continue ;;
        slow_down) INTERVAL=$((INTERVAL + 5)); continue ;;
        *) echo "$TOKEN"; exit 1 ;;
    esac
done

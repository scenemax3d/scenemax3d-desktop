#!/bin/sh
# Runs only inside the privileged deployment installer, never Test connection.
set -eu
exec 8>/run/lock/scenemax-caddy-install.lock
flock -w 120 8 || { echo 'Another Caddy installation is still running'; exit 1; }
if [ -e /usr/bin/caddy ] || [ -L /usr/bin/caddy ]; then
    test -x /usr/bin/caddy || { echo 'Existing /usr/bin/caddy is not executable; refusing to replace it'; exit 1; }
    case "$(/usr/bin/caddy version)" in
        v2.*) echo 'Using existing Caddy 2';;
        *) echo 'Existing Caddy is not version 2; refusing to replace it'; exit 1;;
    esac
    exit 0
fi
command -v curl >/dev/null
command -v tar >/dev/null
work=$(mktemp -d)
destination=''
cleanup() {
    rm -rf "$work"
    if [ -n "$destination" ]; then rm -f "$destination"; fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
echo 'Installing Caddy 2.11.9 for secure WebSocket connections...'
# Pin both the official release and its GitHub release-asset SHA-256 digest.
curl --fail --location --proto '=https' --proto-redir '=https' --tlsv1.2 \
    --connect-timeout 15 --max-time 120 --retry 2 \
    --output "$work/caddy.tar.gz" \
    https://github.com/caddyserver/caddy/releases/download/v2.11.9/caddy_2.11.9_linux_amd64.tar.gz
printf '%s  %s\n' 5b8cb7faf8726a2028ad96a66e54e6e81e8f361bc6175e5d22bbd997cc5177fb "$work/caddy.tar.gz" | sha256sum -c -
tar -xzf "$work/caddy.tar.gz" -C "$work" caddy
chmod 755 "$work/caddy"
case "$("$work/caddy" version)" in
    v2.11.9*) ;;
    *) echo 'Downloaded Caddy version does not match the pinned release'; exit 1;;
esac
destination=$(mktemp /usr/bin/.scenemax-caddy.XXXXXX)
install -m 755 "$work/caddy" "$destination"
mv "$destination" /usr/bin/caddy
destination=''
echo 'Caddy installed. SceneMax will configure its dedicated TLS service.'

#!/bin/sh
set -eu
root=@ROOT@
stage=@STAGE@
service=@SERVICE@
project=@PROJECT@
release=@RELEASE@
gateway=@GATEWAY@
tls=@TLS@
tls_host=@TLS_HOST@
tls_port=@TLS_PORT@
tls_url=@TLS_URL@
origin=@ORIGIN@
server_unit="${service}.service"
gateway_unit="${service}-gateway.service"
tls_unit="${service}-tls.service"
cd "$stage"
sha256sum -c SHA256SUMS
if [ "$tls" = 1 ]; then
    test -x /usr/bin/caddy || { echo 'Install Caddy 2 at /usr/bin/caddy before WSS deployment'; exit 1; }
    command -v curl >/dev/null || { echo 'Install curl before WSS deployment'; exit 1; }
    /usr/bin/caddy validate --config "$stage/caddy.json"
fi
# Never adopt unrelated directories or system services.
exec 9>"/run/lock/${service}-deploy.lock"
flock -n 9 || { echo 'Another deployment is running'; exit 1; }
if [ -e "$root" ] || [ -L "$root" ]; then
    test ! -L "$root"
    test "$(cat "$root/.scenemax-project")" = "$project"
fi
for unit in "$server_unit" "$gateway_unit" "$tls_unit"; do
    existing=$(systemctl cat "$unit" 2>/dev/null || true)
    if [ -n "$existing" ]; then
        printf '%s\n' "$existing" | grep -Fx "# SceneMax managed $project" >/dev/null || { echo "Refusing to replace unmanaged service $unit"; exit 1; }
    fi
done
mkdir -p "$root/releases"
printf '%s\n' "$project" > "$root/.scenemax-project"
old=$(readlink "$root/current" || true)
if [ -e "$root/current" ] || [ -L "$root/current" ]; then
    test -L "$root/current"
    case "$old" in "$root"/releases/*) ;; *) echo 'Invalid current release'; exit 1 ;; esac
fi
mkdir "$root/releases/$release"
cp scenemax-mp-server "$root/releases/$release/"
chmod 755 "$root" "$root/releases" "$root/releases/$release" "$root/releases/$release/scenemax-mp-server"
if [ "$gateway" = 1 ]; then
    cp scenemax_web_gateway gateway.json "$root/releases/$release/"
    chmod 755 "$root/releases/$release/scenemax_web_gateway"
    chmod 644 "$root/releases/$release/gateway.json"
fi
if [ "$tls" = 1 ]; then
    install -m 644 caddy.json "$root/releases/$release/caddy.json"
fi
tls_active=0; tls_enabled=0
systemctl is-active --quiet "$tls_unit" && tls_active=1
systemctl is-enabled --quiet "$tls_unit" && tls_enabled=1
server_active=0; gateway_active=0; server_enabled=0; gateway_enabled=0
systemctl is-active --quiet "$server_unit" && server_active=1
systemctl is-active --quiet "$gateway_unit" && gateway_active=1
systemctl is-enabled --quiet "$server_unit" && server_enabled=1
systemctl is-enabled --quiet "$gateway_unit" && gateway_enabled=1
for unit in "$server_unit" "$gateway_unit" "$tls_unit"; do
    if [ -e "/etc/systemd/system/$unit" ]; then cp -p "/etc/systemd/system/$unit" "$stage/$unit.previous"; fi
done
rollback() {
    status=$?
    trap - EXIT HUP INT TERM
    if [ "$status" != 0 ]; then
        echo 'Installation failed; restoring previous release and service definitions'
        systemctl stop "$tls_unit" "$gateway_unit" "$server_unit" || true
        if [ -n "$old" ]; then ln -sfn "$old" "$root/current.rollback"; mv -Tf "$root/current.rollback" "$root/current"; else rm -f "$root/current"; fi
        for unit in "$server_unit" "$gateway_unit" "$tls_unit"; do
            if [ -f "$stage/$unit.previous" ]; then cp -p "$stage/$unit.previous" "/etc/systemd/system/$unit"; else rm -f "/etc/systemd/system/$unit"; fi
        done
        systemctl daemon-reload || true
        if [ "$server_enabled" = 1 ]; then systemctl enable "$server_unit" || true; else systemctl disable "$server_unit" || true; fi
        if [ "$gateway_enabled" = 1 ]; then systemctl enable "$gateway_unit" || true; else systemctl disable "$gateway_unit" || true; fi
        if [ "$tls_enabled" = 1 ]; then systemctl enable "$tls_unit" || true; else systemctl disable "$tls_unit" || true; fi
        if [ "$server_active" = 1 ]; then systemctl start "$server_unit" || true; fi
        if [ "$gateway_active" = 1 ]; then systemctl start "$gateway_unit" || true; fi
        if [ "$tls_active" = 1 ]; then systemctl start "$tls_unit" || true; fi
    fi
    exit "$status"
}
trap rollback EXIT
trap 'exit 1' HUP INT TERM
install -m 644 server.service "/etc/systemd/system/$server_unit"
if [ "$gateway" = 1 ]; then
    install -m 644 gateway.service "/etc/systemd/system/$gateway_unit"
else
    systemctl disable --now "$gateway_unit" 2>/dev/null || true
    rm -f "/etc/systemd/system/$gateway_unit"
fi
if [ "$tls" = 1 ]; then
    install -m 644 tls.service "/etc/systemd/system/$tls_unit"
else
    systemctl disable --now "$tls_unit" 2>/dev/null || true
    rm -f "/etc/systemd/system/$tls_unit"
fi
ln -s "$root/releases/$release" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
echo 'Reloading service definitions'
systemctl daemon-reload
systemctl enable "$server_unit"
echo 'Starting Zig UDP server and browser gateway'
systemctl restart "$server_unit"
if [ "$gateway" = 1 ]; then systemctl enable "$gateway_unit"; systemctl restart "$gateway_unit"; fi
if [ "$tls" = 1 ]; then systemctl enable "$tls_unit"; systemctl restart "$tls_unit"; fi
echo 'Checking active services and listening ports'
sleep 2
systemctl is-active --quiet "$server_unit"
if [ "$gateway" = 1 ]; then systemctl is-active --quiet "$gateway_unit"; fi
server_pid=$(systemctl show -p MainPID --value "$server_unit")
ss -H -lunp 'sport = :@UDP_PORT@' | grep -F "pid=$server_pid," >/dev/null
if [ "$gateway" = 1 ]; then
    gateway_pid=$(systemctl show -p MainPID --value "$gateway_unit")
    ss -H -ltnp 'sport = :@TCP_PORT@' | grep -F "pid=$gateway_pid," >/dev/null
fi
sleep 2
test "$(systemctl show -p MainPID --value "$server_unit")" = "$server_pid"
if [ "$gateway" = 1 ]; then test "$(systemctl show -p MainPID --value "$gateway_unit")" = "$gateway_pid"; fi
systemctl --no-pager --full status "$server_unit"
if [ "$gateway" = 1 ]; then systemctl --no-pager --full status "$gateway_unit"; fi
if [ "$tls" = 1 ]; then
    echo 'Waiting for trusted TLS certificate and WebSocket upgrade'
    ready=0
    for attempt in $(seq 1 20); do
        systemctl is-active --quiet "$tls_unit"
        # Verify the certificate and hostname, and require an actual upgrade
        # through the TLS proxy with the configured browser Origin.
        code=$(curl --silent --noproxy '*' --globoff --output /dev/null --write-out '%{http_code}' \
            --connect-timeout 2 --max-time 3 --http1.1 \
            --resolve "$tls_host:$tls_port:127.0.0.1" \
            -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
            -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
            -H "Origin: $origin" "$tls_url" || true)
        if [ "$code" = 101 ]; then ready=1; break; fi
        sleep 2
    done
    test "$ready" = 1 || { echo 'WSS handshake failed: check DNS, firewall, certificate issuance and allowed Origin'; exit 1; }
    systemctl --no-pager --full status "$tls_unit"
    printf 'Verified secure gateway: %s\n' "$tls_url"
fi
printf 'Installed release %s; previous release retained: %s\n' "$release" "$old"
trap - EXIT HUP INT TERM
# Staging is a generated directory with a fixed /tmp/scenemax-deploy- prefix.
rm -rf "$stage"

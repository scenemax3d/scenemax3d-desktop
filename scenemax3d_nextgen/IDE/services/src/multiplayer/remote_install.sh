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
tls_provider=caddy
nginx_applied=0
cd "$stage"
sha256sum -c SHA256SUMS
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
if [ "$tls" = 1 ]; then
    command -v curl >/dev/null || { echo 'Install curl before WSS deployment'; exit 1; }
    listeners=$(ss -H -ltnp "sport = :$tls_port")
    if printf '%s\n' "$listeners" | grep -q '"nginx"'; then
        # A shared web server is never stopped or replaced. Serialize SceneMax's
        # config edits across projects; external edits are also checked by hash/content.
        printf '%s\n' "$listeners" | grep -v '"nginx"' | grep -q . && { echo 'TLS port has mixed owners; refusing deployment'; exit 1; }
        command -v python3 >/dev/null || { echo 'Install python3 to integrate with existing Nginx'; exit 1; }
        exec 7>/run/lock/scenemax-nginx-deploy.lock
        flock -w 120 7 || { echo 'Another Nginx deployment is running'; exit 1; }
        nginx -t
        python3 "$stage/nginx-route.py" prepare "$stage/nginx-plan.json" "$tls_url" '127.0.0.1:@TCP_PORT@' "$project"
        tls_provider=nginx
        echo 'Using existing Nginx for secure WebSocket connections'
    else
        if [ -n "$listeners" ]; then
            previous_tls_pid=$(systemctl show -p MainPID --value "$tls_unit" 2>/dev/null || true)
            test -n "$previous_tls_pid" && test "$previous_tls_pid" != 0 &&
                ! printf '%s\n' "$listeners" | grep -v "pid=$previous_tls_pid," | grep -q . ||
                { echo 'TLS port is occupied by an unrelated service; refusing deployment'; exit 1; }
        fi
        (
@CADDY_SETUP@
        )
        /usr/bin/caddy validate --config "$stage/caddy.json"
    fi
fi
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
    printf '%s\n' "$tls_provider" > "$root/releases/$release/tls-provider"
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
        if [ "$nginx_applied" = 1 ]; then
            if python3 "$stage/nginx-route.py" rollback "$stage/nginx-plan.json"; then
                nginx -t && nginx -s reload || echo 'Nginx rollback reload failed; inspect the retained staging logs'
            else
                echo 'Nginx changed concurrently; manual review needed. Existing sites were not overwritten.'
            fi
        fi
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
if [ "$tls" = 1 ] && [ "$tls_provider" = caddy ]; then
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
if [ "$tls" = 1 ]; then
    if [ "$tls_provider" = caddy ]; then
        systemctl enable "$tls_unit"; systemctl restart "$tls_unit"
    elif python3 "$stage/nginx-route.py" changed "$stage/nginx-plan.json"; then
        nginx_applied=1
        python3 "$stage/nginx-route.py" apply "$stage/nginx-plan.json"
        nginx -t
        nginx -s reload
    fi
fi
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
        if [ "$tls_provider" = caddy ]; then systemctl is-active --quiet "$tls_unit"; fi
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
    if [ "$tls_provider" = caddy ]; then systemctl --no-pager --full status "$tls_unit"; fi
    printf 'Verified secure gateway: %s\n' "$tls_url"
fi
printf 'Installed release %s; previous release retained: %s\n' "$release" "$old"
trap - EXIT HUP INT TERM
# Staging is a generated directory with a fixed /tmp/scenemax-deploy- prefix.
rm -rf "$stage"

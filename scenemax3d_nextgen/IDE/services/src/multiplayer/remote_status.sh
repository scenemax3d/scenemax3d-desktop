server=@SERVER@
gateway=@GATEWAY@
printf 'CURRENT SERVICE STATUS (checked %s)\n' "$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
printf 'These are current process states, not a game connection test.\n\n'
status_code=0
show_state() {
    label=$1
    unit=$2
    query_code=0
    properties=$(systemctl show --no-pager --property=LoadState --property=ActiveState --property=SubState "$unit" 2>&1) || query_code=$?
    load=$(printf '%s\n' "$properties" | sed -n 's/^LoadState=//p')
    active=$(printf '%s\n' "$properties" | sed -n 's/^ActiveState=//p')
    sub=$(printf '%s\n' "$properties" | sed -n 's/^SubState=//p')
    if [ "$load" = not-found ]; then
        state='NOT INSTALLED - use Deploy & start first'
    elif [ "$query_code" != 0 ] || [ -z "$active" ] || [ -z "$sub" ]; then
        state='UNKNOWN - could not read current service state'
        status_code=1
    elif [ "$active" = active ] && [ "$sub" = running ]; then
        state='RUNNING'
    elif [ "$active" = failed ]; then
        state='FAILED'
    elif [ "$active" = inactive ]; then
        state='STOPPED'
    else
        state='NOT RUNNING'
    fi
    printf '%s: %s\n  Service: %s\n' "$label" "$state" "$unit"
    if [ -n "$active" ]; then printf '  systemd: %s (%s)\n' "$active" "$sub"; fi
    if [ "$query_code" != 0 ] && [ "$load" != not-found ]; then printf '  Query details: %s\n' "$properties"; fi
    printf '\n'
}
show_state 'Zig UDP server' "$server"
show_state 'Browser WebSocket gateway' "$gateway"
printf '%s\n' '----- HISTORICAL LOGS (most recent 8 entries per service) -----'
printf 'Entries below may be old. Errors here do not change the current states above.\nTimestamps below are UTC.\n'
for unit in "$server" "$gateway"; do
    printf '\n--- History: %s ---\n' "$unit"
    journalctl --utc --no-pager -o short-iso -n 8 -u "$unit" 2>&1 || printf 'Historical logs unavailable; see current service state above.\n'
done
exit "$status_code"

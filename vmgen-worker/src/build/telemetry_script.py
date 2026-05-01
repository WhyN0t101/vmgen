"""
Bash collector script installed on the VM when telemetryEnabled=True.
Students run `collect-telemetry` after exploitation to generate a JSON
report they can upload via the VMGen portal.
"""

TELEMETRY_COLLECTOR_SCRIPT = r"""#!/bin/bash
# VMGen Telemetry Collector
# Run this inside the VM after exploitation to capture session data.
# Then upload /tmp/vmgen-telemetry.json via the VMGen portal.

OUTPUT="/tmp/vmgen-telemetry.json"
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

# Collect recent bash history (last 200 entries, sanitised)
HISTORY_FILE="$HOME/.bash_history"
HISTORY_JSON="["
history_count=0
if [ -f "$HISTORY_FILE" ]; then
    history_count=$(wc -l < "$HISTORY_FILE")
    first_cmd=1
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        # Skip lines that look like they contain credentials
        echo "$line" | grep -qiE '(password|passwd|--pass|-p\s|secret|token|apikey)' && continue
        # Escape for JSON
        cleaned=$(printf '%s' "$line" | head -c 300 | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g')
        [ $first_cmd -eq 0 ] && HISTORY_JSON+=","
        HISTORY_JSON+="{\"cmd\":\"$cleaned\"}"
        first_cmd=0
    done < <(tail -200 "$HISTORY_FILE")
fi
HISTORY_JSON+="]"

cat > "$OUTPUT" <<JSON
{
  "vmgen_version": "1.0",
  "collected_at": "$TIMESTAMP",
  "hostname": "$(hostname)",
  "history_entry_count": $history_count,
  "recent_commands": $HISTORY_JSON
}
JSON

echo ""
echo "Telemetry saved to: $OUTPUT"
echo "Upload this file via your build's status page on the VMGen portal."
"""

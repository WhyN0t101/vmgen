"""Validate vmgen-brain.json and embed it into vmgen-brain.html.

vmgen-brain.json is the source of truth. The HTML carries an inline copy so it
opens straight from disk (browsers block fetch() on file:// URLs).

Usage:  python sync_brain.py
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
JSON_PATH = HERE / "vmgen-brain.json"
HTML_PATH = HERE / "vmgen-brain.html"
BLOCK = re.compile(
    r'(<script id="brain-data" type="application/json">)(.*?)(</script>)',
    re.DOTALL,
)


def validate(data: dict) -> list[str]:
    errors = []
    ids = [n["id"] for n in data["nodes"]]
    seen = set()
    for i in ids:
        if i in seen:
            errors.append(f"duplicate node id: {i}")
        seen.add(i)
    layer_ids = {l["id"] for l in data["layers"]}
    col_count = len(data["columns"])
    for n in data["nodes"]:
        if n["layer"] not in layer_ids:
            errors.append(f"{n['id']}: unknown layer {n['layer']}")
        if not 0 <= n["col"] < col_count:
            errors.append(f"{n['id']}: bad col {n['col']}")
        for link in n.get("links", []):
            if link["to"] not in seen:
                errors.append(f"{n['id']}: link to unknown node {link['to']}")
    for f in data["flows"]:
        for s in f["steps"]:
            if s["node"] not in seen:
                errors.append(f"{f['id']}: step references unknown node {s['node']}")
    for t in data["traps"]:
        for nid in t["nodes"]:
            if nid not in seen:
                errors.append(f"{t['id']}: references unknown node {nid}")
    return errors


def main() -> int:
    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    errors = validate(data)
    if errors:
        print("Validation failed:", *errors, sep="\n  ")
        return 1

    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    payload = payload.replace("</", "<\\/")  # never close the <script> tag early

    html = HTML_PATH.read_text(encoding="utf-8")
    if not BLOCK.search(html):
        print("brain-data <script> block not found in HTML")
        return 1
    html = BLOCK.sub(lambda m: m.group(1) + payload + m.group(3), html, count=1)
    HTML_PATH.write_text(html, encoding="utf-8")

    edges = sum(len(n.get("links", [])) for n in data["nodes"])
    print(
        f"OK: {len(data['nodes'])} nodes, {edges} links, "
        f"{len(data['flows'])} flows, {len(data['traps'])} traps -> {HTML_PATH.name}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

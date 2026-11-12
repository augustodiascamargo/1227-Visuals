#!/usr/bin/env python3
"""Adds/updates a release in api/data/manifest.json.

usage: manifest_entry.py <jar> <version> <download-url> [notes...]
Computes SHA-256 and size from the jar itself, so the manifest always matches the file.
"""
import hashlib, json, os, re, sys, datetime

jar, version, url = sys.argv[1], sys.argv[2], sys.argv[3]
notes = sys.argv[4:]
if not re.fullmatch(r"\d+(\.\d+)*", version):
    sys.exit("bad version")
if not url.startswith("https://github.com/"):
    sys.exit("download URL must be a GitHub release asset")
data = open(jar, "rb").read()
entry = {
    "version": version,
    "minecraft": "1.8.9",
    "forge": "11.15.1.2318",
    "date": datetime.date.today().isoformat(),
    "notes": notes,
    "file": {"name": f"1227-{version}.jar", "url": url, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)},
}
path = os.path.join(os.path.dirname(__file__), "..", "api", "data", "manifest.json")
m = json.load(open(path))
m.pop("_comment", None)
m["schema"] = 1
m["versions"] = [v for v in m.get("versions", []) if v["version"] != version]
m["versions"].insert(0, entry)
m["latest"] = max((v["version"] for v in m["versions"]), key=lambda s: [int(x) for x in s.split(".")])
json.dump(m, open(path, "w"), indent=2, ensure_ascii=False)
print(json.dumps(entry, indent=2))

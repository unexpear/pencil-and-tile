"""Checks listing.md (or the listing file named) against Google Play's length limits (title 30, short description 80, full 4000)."""
import os, re, sys
name = sys.argv[1] if len(sys.argv) > 1 else "listing.md"
text = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), name), encoding="utf-8").read()
ok = True
for block in re.split(r"\n## ", text)[1:]:
    lang = block.split("\n", 1)[0].strip()
    title = re.search(r"Title: (.*)", block).group(1).strip()
    short = re.search(r"Short description: (.*)", block).group(1).strip()
    full = block.split("Full description:\n", 1)[1].strip()
    for name, value, limit in (("title", title, 30), ("short", short, 80), ("full", full, 4000)):
        flag = "OK" if len(value) <= limit else "TOO LONG"
        ok &= len(value) <= limit
        print(f"{lang:6} {name:5} {len(value):5}/{limit} {flag}")
sys.exit(0 if ok else 1)

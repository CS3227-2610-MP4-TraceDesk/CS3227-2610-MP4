"""Rebuild present.html from deck.json and slides/*.html (run from this folder)."""
import html, json, re, pathlib

here = pathlib.Path(__file__).parent
deck = json.loads((here / "deck.json").read_text(encoding="utf-8"))
template = (here / "_template.txt").read_text(encoding="utf-8")
parts = []
for sid in deck["order"]:
    s = (here / "slides" / f"{sid}.html").read_text(encoding="utf-8")
    m = re.search(r"<aside>(.*?)</aside>", s, re.S)
    note = m.group(1).strip() if m else ""
    s = re.sub(r"<aside>.*?</aside>", "", s, flags=re.S)
    parts.append(f'<div class="slide" data-note="{html.escape(note, quote=True)}">{s}</div>')
(here / "present.html").write_text(template.replace("@@SLIDES@@", "\n".join(parts)), encoding="utf-8")
print(f"built present.html with {len(parts)} slides")

# Deck: "Less context, same code?"

Copy of the slide deck at https://claude.ai/artifact/GHMTv6S2Bds6PGDhhKSFGC
(13 slides, about 12 minutes). The speaker notes on each slide are the
[script](../script.md).

| File | Use |
| --- | --- |
| `present.html` | Offline presenter. Open in a browser. Keys: →/Space next, ← back, `N` speaker notes, `F` full screen, Home/End. Click right or left half to move. Fonts load from Google Fonts; without internet it falls back to Arial/Courier. |
| `less-context-same-code.pdf` | Backup, one 16:9 page per slide, no notes. |
| `deck.json`, `slides/*.html` | Source in the Claude Slides format (order, sections, fonts; one file per slide with `<aside>` notes). Edit these, then rebuild the two files above. |

Rebuilding: run `python build.py`. It fills `_template.txt` with `slides/*.html` in
`deck.json` order and moves each `<aside>` into the notes panel. The PDF was printed from it with headless
Edge (`--print-to-pdf`) while serving this folder with `python -m http.server`.
Scripts do not run when the file is opened through some preview panes; a normal
browser is fine.

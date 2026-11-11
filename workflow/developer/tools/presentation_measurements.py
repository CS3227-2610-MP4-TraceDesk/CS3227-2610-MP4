"""Offline, reproducible measurements for the Developer presentation.

Reads only tracked repository files and git metadata at HEAD. It never reads
.env files, session logs, prompts or user data, and makes no network calls.

Usage (from repository root, tiktoken installed in any venv):
    python workflow/developer/tools/presentation_measurements.py
"""

from __future__ import annotations

import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path, PurePosixPath

import tiktoken

ROOT = Path(__file__).resolve().parents[3]
ENC = tiktoken.get_encoding("o200k_base")  # tokenizer family used by Codex models; Claude differs
LINK = re.compile(r"\]\(([^)#\s]+)(?:#[^)]*)?\)")


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True,
                          text=True, encoding="utf-8", check=True).stdout


def tracked() -> list[str]:
    return [p for p in git("ls-files").splitlines()
            if not PurePosixPath(p).name.startswith(".env")]


def tokens(paths) -> int:
    total = 0
    for p in paths:
        try:
            total += len(ENC.encode((ROOT / p).read_text(encoding="utf-8"),
                                    disallowed_special=()))
        except (UnicodeDecodeError, FileNotFoundError):
            pass
    return total


def link_closure(start: str, files: set[str]) -> set[str]:
    """Markdown files reachable by following relative links from start."""
    seen, stack = set(), [start]
    while stack:
        cur = stack.pop()
        if cur in seen or cur not in files:
            continue
        seen.add(cur)
        text = (ROOT / cur).read_text(encoding="utf-8")
        for target in LINK.findall(text):
            if "://" in target or not target.endswith(".md"):
                continue
            resolved = (PurePosixPath(cur).parent / target).as_posix()
            parts: list[str] = []
            for seg in resolved.split("/"):
                if seg == "..":
                    parts.pop() if parts else None
                elif seg not in ("", "."):
                    parts.append(seg)
            stack.append("/".join(parts))
    return seen


CODE_EXT = {".java", ".ts", ".tsx", ".sql", ".css", ".kts", ".gradle", ".js", ".mjs"}

FOCUSED_DEV_AI = [
    "AGENTS.md",
    ".agents/skills/tracedesk-spec-development/SKILL.md",
    ".agents/skills/tracedesk-ai-integration/SKILL.md",
    "workflow/specs/developer-ai.md",
    "workflow/specs/secured-ai-gateway.md",
    "workflow/decisions/002-shared-role-contracts.md",
    "docs/ai-policy.md",
]


def context_experiment(files: list[str]) -> dict:
    md = [p for p in files if p.endswith(".md")]
    text = [p for p in files if p.endswith(".md") or Path(p).suffix in CODE_EXT]
    closure = sorted(link_closure("AGENTS.md", set(md)))
    sets = {
        "A. Always loaded (AGENTS.md)": ["AGENTS.md"],
        "B. Focused set for DEV-AI #31": FOCUSED_DEV_AI,
        "C. Every doc linked from AGENTS.md, followed transitively": closure,
        "D. All tracked Markdown": md,
        "E. All tracked Markdown + source code": text,
    }
    missing = [p for p in FOCUSED_DEV_AI if p not in files]
    return {"missing_focused_files": missing,
            "rows": [{"set": k, "files": len(v), "tokens": tokens(v)}
                     for k, v in sets.items()]}


def growth_experiment() -> list[dict]:
    """Bytes of Markdown vs source code at the last commit of each day (first-parent)."""
    by_day: dict[str, str] = {}
    for line in git("log", "--first-parent", "--format=%H %ad", "--date=short",
                    "--reverse").splitlines():
        sha, day = line.split()
        by_day[day] = sha
    rows = []
    for day, sha in by_day.items():
        sizes = defaultdict(int)
        counts = defaultdict(int)
        for entry in git("ls-tree", "-r", "-l", sha).splitlines():
            meta, path = entry.split("\t", 1)
            size = meta.split()[3]
            if size == "-":
                continue
            kind = "docs" if path.endswith(".md") else (
                "code" if Path(path).suffix in CODE_EXT else None)
            if kind:
                sizes[kind] += int(size)
                counts[kind] += 1
        rows.append({"day": day, "commit": sha[:7],
                     "md_files": counts["docs"], "md_kb": round(sizes["docs"] / 1024),
                     "code_files": counts["code"], "code_kb": round(sizes["code"] / 1024)})
    return rows


def main() -> None:
    files = tracked()
    out = {
        "head": git("rev-parse", "--short", "HEAD").strip(),
        "dirty": bool(git("status", "--porcelain").strip()),
        "tokenizer": "tiktoken o200k_base (approximation; Claude tokenizer differs)",
        "context": context_experiment(files),
        "growth": growth_experiment(),
    }
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    main()

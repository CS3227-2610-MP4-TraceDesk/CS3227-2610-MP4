#!/usr/bin/env python3
"""Check portable resource links, skill metadata and the extraction boundary."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def check():
    errors = []
    documents = [ROOT / "README.md", ROOT / "AGENTS.md"]
    for folder in ("docs", "workflow", "harness", ".agents/skills"):
        documents.extend(sorted((ROOT / folder).rglob("*.md")))

    for path in documents:
        content = path.read_text()
        label = path.relative_to(ROOT)
        if "/Users/" in content or "C:\\Users\\" in content:
            errors.append(f"{label}: personal absolute path")
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
            if "://" in target or target.startswith("#"):
                continue
            target_path = (path.parent / target.split("#", 1)[0]).resolve()
            if not target_path.is_relative_to(ROOT) or not target_path.exists():
                errors.append(f"{label}: broken or non-portable link: {target}")

    skills = sorted((ROOT / ".agents/skills").glob("*/SKILL.md"))
    if not skills:
        errors.append("No project skills found")
    for path in skills:
        content = path.read_text()
        header = re.match(r"---\nname: ([a-z0-9-]+)\ndescription: ([^\n]+)\n---\n", content)
        if not header or header[1] != path.parent.name or len(header[1]) > 64:
            errors.append(f"{path.relative_to(ROOT)}: invalid skill name/description metadata")

    provenance_count = 0
    for manifest_name, hash_key in (("reuse-manifest.json", "source_sha256"),
                                    ("engineering-reuse.json", "sha256")):
        manifest = json.loads((ROOT / "docs" / manifest_name).read_text())
        for entry in manifest["entries"]:
            destination = (ROOT / entry["destination"]).resolve()
            if not destination.is_relative_to(ROOT) or not destination.is_file():
                errors.append(f"Missing or unsafe manifest destination: {entry['destination']}")
            if not re.fullmatch(r"[a-f0-9]{64}", entry[hash_key]):
                errors.append(f"Invalid source hash: {entry['source_path']}")
            provenance_count += 1

    java_sources = sorted((ROOT / "backend/ai-transport/src").rglob("*.java"))
    if not java_sources:
        errors.append("No transport sources found")
    for path in java_sources:
        text = path.read_text()
        for forbidden in ("com.company.incidentdesk", "org.springframework", "java.sql", "javax.sql",
                          "AiGatewayControl.Ticket"):
            if forbidden in text:
                errors.append(f"{path.relative_to(ROOT)}: old-project/framework coupling: {forbidden}")

    frontend_sources = sorted((ROOT / "frontend/src").rglob("*.ts"))
    frontend_sources += sorted((ROOT / "frontend/src").rglob("*.tsx"))
    for path in frontend_sources:
        if "incident-desk" in path.read_text() or "incidentdesk" in path.read_text():
            errors.append(f"{path.relative_to(ROOT)}: old product coupling")

    if errors:
        print("Foundation checks failed:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print(f"Foundation checks passed: {len(documents)} documents, {len(skills)} skills, "
          f"{provenance_count} provenance entries, {len(java_sources)} transport Java sources.")
    return 0


if __name__ == "__main__":
    sys.exit(check())

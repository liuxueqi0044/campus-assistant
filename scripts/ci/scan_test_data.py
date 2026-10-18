#!/usr/bin/env python3
"""Fail when fixtures or generated evidence contain credential-shaped or personal test data."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PATHS = (ROOT / "tests" / "fixtures", ROOT / "contracts" / "stubs", ROOT / "artifacts")
TEXT_SUFFIXES = {".csv", ".html", ".json", ".log", ".md", ".txt", ".xml", ".yaml", ".yml"}
SAFE_MARKERS = ("CHANGE_ME", "NOT_A_CREDENTIAL", "TEST_", "replace-with-")
RULES = {
    "private-key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "cloud-access-key": re.compile(r"(?:AKIA[0-9A-Z]{16}|LTAI[A-Za-z0-9_-]{12,}|AKID[A-Za-z0-9_-]{12,}|AKLT[A-Za-z0-9_-]{12,})"),
    "china-id-card": re.compile(r"(?<!\d)[1-9]\d{5}(?:18|19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)"),
    "china-mobile": re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)"),
    "credential-value": re.compile(
        r"(?i)[\"']?(?:password|secret|access[_-]?secret|api[_-]?secret|private[_-]?key|access[_-]?token)[\"']?\s*[:=]\s*[\"']([^\"'\s${}]{12,})"
    ),
    "merchant-number": re.compile(r"(?i)[\"']?(?:mchId|merchantId|merchantRef)[\"']?\s*[:=]\s*[\"']?(\d{8,})"),
}


def files_under(paths: Sequence[Path]):
    for path in paths:
        if path.is_file():
            yield path
        elif path.is_dir():
            yield from (candidate for candidate in path.rglob("*") if candidate.is_file())


def safe_match(match: re.Match[str]) -> bool:
    value = match.group(1) if match.lastindex else match.group(0)
    return any(marker.lower() in value.lower() for marker in SAFE_MARKERS)


def scan(paths: Sequence[Path]) -> tuple[int, list[dict[str, object]]]:
    findings: list[dict[str, object]] = []
    scanned = 0
    for path in sorted(set(files_under(paths))):
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        scanned += 1
        for number, line in enumerate(text.splitlines(), start=1):
            for rule, pattern in RULES.items():
                for match in pattern.finditer(line):
                    if safe_match(match):
                        continue
                    findings.append({
                        "rule": rule,
                        "path": str(path.relative_to(ROOT)).replace("\\", "/")
                        if path.is_relative_to(ROOT) else str(path),
                        "line": number,
                    })
    return scanned, findings


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    paths = [path if path.is_absolute() else ROOT / path for path in args.paths] or list(DEFAULT_PATHS)
    scanned, findings = scan(paths)
    payload = {"schemaVersion": 1, "status": "FAIL" if findings else "PASS",
               "scannedFiles": scanned, "findings": findings}
    if args.report:
        report = args.report if args.report.is_absolute() else ROOT / args.report
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{payload['status']}: scanned {scanned} text file(s), found {len(findings)} issue(s)")
    for finding in findings:
        print(f"- {finding['rule']} {finding['path']}:{finding['line']}")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())

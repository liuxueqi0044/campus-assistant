#!/usr/bin/env python3
"""Fail closed unless every requested module has complete requested-level evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Mapping, Sequence


def validate_report(module: str, payload: Mapping[str, object], expected_level: str = "A") -> list[str]:
    errors: list[str] = []
    if payload.get("module") != module:
        errors.append(f"module_mismatch:{payload.get('module')}")
    if payload.get("level") != expected_level:
        errors.append(f"level_mismatch:{payload.get('level')}!=expected:{expected_level}")
    if payload.get("status") != "PASS":
        errors.append(f"status_not_pass:{payload.get('status')}")
    if payload.get("mysqlConfigured") is not True:
        errors.append("mysql_not_configured")
    if expected_level == "B" and payload.get("valkeyConfigured") is not True:
        errors.append("valkey_not_configured")
    if payload.get("sourceSha") in (None, "", "UNKNOWN", "UNAVAILABLE"):
        errors.append("source_sha_missing")

    required = payload.get("requiredTests")
    suites = payload.get("suites")
    if not isinstance(required, list) or not required:
        errors.append("required_tests_missing")
        required = []
    if not isinstance(suites, dict):
        errors.append("suite_evidence_missing")
        suites = {}
    for name in required:
        suite = suites.get(name)
        if not isinstance(suite, dict):
            errors.append(f"required_suite_missing:{name}")
            continue
        if not isinstance(suite.get("tests"), int) or suite["tests"] <= 0:
            errors.append(f"required_suite_zero:{name}")
        for key in ("failures", "errors", "skipped"):
            if suite.get(key) != 0:
                errors.append(f"required_suite_{key}:{name}")

    if module == "M00.04":
        mutation = payload.get("mutationGuard")
        if not isinstance(mutation, dict) or mutation.get("status") != "PASS":
            errors.append("mutation_guard_not_pass")
    return errors


def load_and_validate(specification: str) -> list[str]:
    try:
        module_and_level, raw_path = specification.split("=", 1)
    except ValueError:
        return [f"invalid_specification:{specification}"]
    if ":" in module_and_level:
        module, expected_level = module_and_level.rsplit(":", 1)
    else:
        module, expected_level = module_and_level, "A"
    if not module or expected_level not in {"A", "B", "C"}:
        return [f"invalid_specification:{specification}"]
    path = Path(raw_path)
    if not path.is_file():
        return [f"{module}:report_missing:{path}"]
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"{module}:report_unreadable:{type(exc).__name__}"]
    if not isinstance(payload, dict):
        return [f"{module}:report_not_object"]
    return [f"{module}:{expected_level}:{error}"
            for error in validate_report(module, payload, expected_level)]


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--report",
        action="append",
        required=True,
        metavar="MODULE[:LEVEL]=PATH",
        help="expected MODULE[:LEVEL]=PATH report; level defaults to A; repeat for every gate",
    )
    args = parser.parse_args(argv)
    errors = [error for specification in args.report for error in load_and_validate(specification)]
    if errors:
        print("Acceptance evidence gate failed:")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"Acceptance evidence gate passed for {len(args.report)} module(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())

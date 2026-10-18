#!/usr/bin/env python3
"""Fail-closed independent M01.03 B gate for the three real HTTP integration selectors."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Mapping

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
SUITE_PREFIX = "cn.iocoder.yudao.server.integration."
SELECTORS = (
    "M0101MemberOAuth2IntegrationIT#disabledMemberCannotLoginAgainOrCreateAnotherToken",
    "M0203TenantBoundaryIntegrationIT#memberVerificationUsesRealMediaBindingsAndSetOrderIdempotency",
    "M0203TenantBoundaryIntegrationIT#mediaBindingsRollBackWhenVerificationInsertFails",
)
PREPARATION = "M0101BDatabasePreparationIT"
REQUIRED_ENV = (
    "CAMPUS_M0103_B_MYSQL_URL",
    "CAMPUS_M0103_B_MYSQL_USERNAME",
    "CAMPUS_M0103_B_MYSQL_PASSWORD",
    "CAMPUS_M0103_B_VALKEY_URL",
    "CAMPUS_M0103_B_SCHEMA_GUARD",
    "CAMPUS_M0103_B_PREPARE",
)
EXPECTED_SCHEMA_GUARD = "campus_m0103_b_acceptance"
EXPECTED_PREPARE = "IMPORT_FIXED_BASELINE"


def report_path(selector: str) -> Path:
    class_name = selector.split("#", 1)[0]
    return BACKEND / "yudao-server" / "target" / "surefire-reports" / f"TEST-{SUITE_PREFIX}{class_name}.xml"


def parse_fresh_suite(selector: str, started_ns: int) -> tuple[dict[str, int] | None, str | None]:
    path = report_path(selector)
    if not path.is_file() or path.stat().st_mtime_ns < started_ns:
        return None, "suite_missing_or_stale"
    try:
        root = ET.parse(path).getroot()
        if root.attrib.get("name") != SUITE_PREFIX + selector.split("#", 1)[0]:
            return None, "suite_name_mismatch"
        evidence = {name: int(root.attrib.get(name, "0")) for name in ("tests", "failures", "errors", "skipped")}
    except (OSError, ET.ParseError, ValueError):
        return None, "suite_xml_invalid"
    if evidence["tests"] == 0:
        return evidence, "suite_zero_tests"
    if evidence["failures"] or evidence["errors"]:
        return evidence, "suite_failed"
    if evidence["skipped"]:
        return evidence, "suite_skipped"
    return evidence, None


def sanitize(text: str) -> str:
    for name, value in os.environ.items():
        if value and any(marker in name.upper() for marker in ("PASSWORD", "TOKEN", "SECRET", "KEY", "CREDENTIAL", "AUTH")):
            text = text.replace(value, "<redacted>")
    return re.sub(r"(?i)(password|passwd|secret|token|authorization|api[_-]?key)\s*([:=])\s*[^\s,;]+",
                  r"\1\2<redacted>", text)


def maven_command() -> str | None:
    found = shutil.which("mvn")
    if found:
        return found
    bundled = ROOT.parent / ".tools" / "apache-maven-3.9.16" / "bin" / "mvn.cmd"
    return str(bundled) if bundled.is_file() else None


def source_sha() -> str:
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else "UNKNOWN"


def run_selector(maven: str, selector: str) -> tuple[int, dict[str, int] | None, str | None, str]:
    started_ns = time.time_ns()
    command = [maven, "-B", "-pl", "yudao-server", "-am", f"-Dtest={selector}",
               "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false", "test"]
    test_environment = os.environ.copy()
    # The Java fixtures intentionally keep M01.01_B variable names; map them only inside this
    # dedicated gate so M01.03 never silently falls back to a shared schema.
    for suffix in ("MYSQL_URL", "MYSQL_USERNAME", "MYSQL_PASSWORD", "VALKEY_URL", "SCHEMA_GUARD", "PREPARE"):
        test_environment[f"CAMPUS_M0101_B_{suffix}"] = test_environment[f"CAMPUS_M0103_B_{suffix}"]
    try:
        result = subprocess.run(command, cwd=BACKEND, env=test_environment, text=True,
                                capture_output=True, check=False)
        process_reason = None if result.returncode == 0 else f"maven_exit:{result.returncode}"
        output = sanitize((result.stdout + "\n" + result.stderr).strip())[-3000:]
    except OSError as exc:
        return 127, None, f"maven_exception:{type(exc).__name__}", ""
    evidence, suite_reason = parse_fresh_suite(selector, started_ns)
    reason = ";".join(part for part in (process_reason, suite_reason) if part) or None
    return result.returncode, evidence, reason, output if reason else ""


def environment_gate(environment: Mapping[str, str] | None = None) -> list[str]:
    values = os.environ if environment is None else environment
    missing = [name for name in REQUIRED_ENV if not values.get(name, "").strip()]
    if missing:
        return [f"missing:{name}" for name in missing]
    reasons = []
    if values["CAMPUS_M0103_B_SCHEMA_GUARD"] != EXPECTED_SCHEMA_GUARD:
        reasons.append("schema_guard_mismatch")
    if values["CAMPUS_M0103_B_PREPARE"] != EXPECTED_PREPARE:
        reasons.append("prepare_guard_mismatch")
    return reasons


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "artifacts/acceptance/m01.03-b.json")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        assert environment_gate({})  # missing dedicated environment is never PASS
        valid = {name: "value" for name in REQUIRED_ENV}
        valid["CAMPUS_M0103_B_SCHEMA_GUARD"] = "wrong"
        valid["CAMPUS_M0103_B_PREPARE"] = EXPECTED_PREPARE
        assert "schema_guard_mismatch" in environment_gate(valid)
        assert PREPARATION == "M0101BDatabasePreparationIT"
        assert len(SELECTORS) == 3 and all("#" in selector for selector in SELECTORS)
        print("self-test passed: M01.03 B selectors and fail-closed environment gate")
        return 0

    reasons = environment_gate()
    mysql_configured = all(os.environ.get(name, "").strip()
                           for name in REQUIRED_ENV[:3]) and not any(
                               reason in ("schema_guard_mismatch", "prepare_guard_mismatch") for reason in reasons)
    valkey_configured = bool(os.environ.get(REQUIRED_ENV[3], "").strip()) and not any(
        reason in ("schema_guard_mismatch", "prepare_guard_mismatch") for reason in reasons)
    suites: dict[str, dict[str, object]] = {}
    preparation_evidence: dict[str, int] | None = None
    preparation_reason: str | None = None
    diagnostics: list[str] = []
    status = "EXTERNAL_NOT_READY" if reasons else "FAIL"
    if not reasons:
        maven = maven_command()
        if not maven:
            reasons = ["maven_not_found"]
            status = "EXTERNAL_NOT_READY"
        else:
            prep_exit, preparation_evidence, preparation_reason, prep_diagnostic = run_selector(maven, PREPARATION)
            suites[PREPARATION] = {
                **(preparation_evidence or {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}),
                "exitCode": prep_exit, "reason": preparation_reason,
            }
            if preparation_reason:
                reasons.append(f"preparation:{preparation_reason}")
                if prep_diagnostic:
                    diagnostics.append(f"{PREPARATION}: {prep_diagnostic}")
            else:
                for selector in SELECTORS:
                    exit_code, evidence, reason, diagnostic = run_selector(maven, selector)
                    suites[selector] = {**(evidence or {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}),
                                        "exitCode": exit_code, "reason": reason}
                    if reason:
                        reasons.append(f"{selector}:{reason}")
                        if diagnostic:
                            diagnostics.append(f"{selector}: {diagnostic}")
            status = "PASS" if not reasons else "FAIL"

    report = args.report if args.report.is_absolute() else ROOT / args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({"module": "M01.03", "level": "B", "status": status,
                                  "generatedAt": datetime.now(timezone.utc).isoformat(),
                                  "sourceSha": source_sha(), "requiredTests": list(SELECTORS),
                                  "preparationEvidence": preparation_evidence,
                                  "preparationReason": preparation_reason,
                                  "mysqlConfigured": mysql_configured, "valkeyConfigured": valkey_configured,
                                  "environmentMapping": "CAMPUS_M0103_B_* -> CAMPUS_M0101_B_* for Java fixtures",
                                  "suites": suites, "reasons": reasons}, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")
    print(status)
    for reason in reasons:
        print(f"- {reason}")
    for diagnostic in diagnostics:
        print(f"--- sanitized diagnostic ---\n{diagnostic}")
    return 0 if status == "PASS" else 2


if __name__ == "__main__":
    sys.exit(main())

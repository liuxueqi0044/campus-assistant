#!/usr/bin/env python3
"""Fail-closed independent M02.02 B gate for real media binding evidence.

M02.02 A already covers the module unit tests and the opt-in MySQL class, and the M01.03 B
gate exercised two cross-module transaction paths. This gate gives M02.02 its own isolated
schema, its own ordered selectors and its own report so another module's run can never stand
in for M02.02 B.

The dedicated ``CAMPUS_M0202_B_*`` environment is mapped inside the child process only. The
Java fixtures still read ``CAMPUS_M0101_B_*`` (server integration tests) and
``CAMPUS_M0202_MYSQL_*`` (media module test); pre-existing values for those names are always
overwritten so a shared M01.01/M02.02 schema can never leak in as a fallback.
"""

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
from typing import Mapping, NamedTuple, Sequence

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"

REQUIRED_ENV = (
    "CAMPUS_M0202_B_MYSQL_URL",
    "CAMPUS_M0202_B_MYSQL_USERNAME",
    "CAMPUS_M0202_B_MYSQL_PASSWORD",
    "CAMPUS_M0202_B_VALKEY_URL",
    "CAMPUS_M0202_B_SCHEMA_GUARD",
    "CAMPUS_M0202_B_PREPARE",
)
EXPECTED_SCHEMA_GUARD = "campus_m0202_b_acceptance"
EXPECTED_PREPARE = "IMPORT_FIXED_BASELINE"

# Java fixtures keep their historical variable names; the dedicated gate maps onto them.
SERVER_FIXTURE_PREFIX = "CAMPUS_M0101_B_"
MEDIA_FIXTURE_PREFIX = "CAMPUS_M0202_MYSQL_"
DEDICATED_PREFIX = "CAMPUS_M0202_B_"
MAPPINGS = (
    (f"{DEDICATED_PREFIX}MYSQL_URL", f"{SERVER_FIXTURE_PREFIX}MYSQL_URL"),
    (f"{DEDICATED_PREFIX}MYSQL_USERNAME", f"{SERVER_FIXTURE_PREFIX}MYSQL_USERNAME"),
    (f"{DEDICATED_PREFIX}MYSQL_PASSWORD", f"{SERVER_FIXTURE_PREFIX}MYSQL_PASSWORD"),
    (f"{DEDICATED_PREFIX}VALKEY_URL", f"{SERVER_FIXTURE_PREFIX}VALKEY_URL"),
    (f"{DEDICATED_PREFIX}SCHEMA_GUARD", f"{SERVER_FIXTURE_PREFIX}SCHEMA_GUARD"),
    (f"{DEDICATED_PREFIX}PREPARE", f"{SERVER_FIXTURE_PREFIX}PREPARE"),
    (f"{DEDICATED_PREFIX}MYSQL_URL", f"{MEDIA_FIXTURE_PREFIX}URL"),
    (f"{DEDICATED_PREFIX}MYSQL_USERNAME", f"{MEDIA_FIXTURE_PREFIX}USERNAME"),
    (f"{DEDICATED_PREFIX}MYSQL_PASSWORD", f"{MEDIA_FIXTURE_PREFIX}PASSWORD"),
)

# Configuration problems are reported as EXTERNAL_NOT_READY, never as a test failure.
CONFIG_REASONS = frozenset({
    "schema_guard_mismatch",
    "prepare_guard_mismatch",
    "mysql_url_not_disposable",
    "mysql_url_schema_mismatch",
})


class Selector(NamedTuple):
    """One ordered, independently evidenced B selector."""

    module: str
    suite: str
    test: str
    min_tests: int


PREPARATION = Selector(
    module="yudao-server",
    suite="cn.iocoder.yudao.server.integration.M0101BDatabasePreparationIT",
    test="M0101BDatabasePreparationIT",
    min_tests=1,
)
SELECTORS = (
    Selector(
        module="yudao-server",
        suite="cn.iocoder.yudao.server.integration.M0203TenantBoundaryIntegrationIT",
        test="M0203TenantBoundaryIntegrationIT#memberVerificationUsesRealMediaBindingsAndSetOrderIdempotency",
        min_tests=1,
    ),
    Selector(
        module="yudao-server",
        suite="cn.iocoder.yudao.server.integration.M0203TenantBoundaryIntegrationIT",
        test="M0203TenantBoundaryIntegrationIT#mediaBindingsRollBackWhenVerificationInsertFails",
        min_tests=1,
    ),
    Selector(
        module="campus-module-media",
        suite="cn.iocoder.yudao.module.campus.media.infrastructure.M0202MySqlIntegrationTest",
        test="M0202MySqlIntegrationTest",
        min_tests=4,
    ),
)


def report_path(selector: Selector) -> Path:
    return BACKEND / selector.module / "target" / "surefire-reports" / f"TEST-{selector.suite}.xml"


def jdbc_schema(url: str) -> str | None:
    """Return the schema of a plain disposable MySQL JDBC URL, or None if it is not one.

    ``M0202MySqlIntegrationTest`` calls ``Flyway.clean()``, so the gate refuses anything that is
    not an unquoted single-schema JDBC URL without option injection.
    """
    value = url.strip()
    if not value.startswith("jdbc:mysql://") or value.count("?") > 1:
        return None
    if any(character in value for character in (";", "#", "\\", '"', "'")):
        return None
    if any(character.isspace() for character in value):
        return None
    match = re.fullmatch(r"jdbc:mysql://[^/?#;@]+/([A-Za-z0-9_]+)(?:\?[^#;]*)?", value)
    return match.group(1) if match else None


def mapped_environment(environment: Mapping[str, str]) -> dict[str, str]:
    """Map the dedicated gate variables onto the names the Java fixtures read.

    Assignments are unconditional so any ambient ``CAMPUS_M0101_B_*`` or
    ``CAMPUS_M0202_MYSQL_*`` value is replaced instead of acting as a fallback.
    """
    result = dict(environment)
    for source, target in MAPPINGS:
        result[target] = result.get(source, "")
    return result


def parse_fresh_suite(selector: Selector, started_ns: int) -> tuple[dict[str, int] | None, str | None]:
    path = report_path(selector)
    if not path.is_file() or path.stat().st_mtime_ns < started_ns:
        return None, "suite_missing_or_stale"
    try:
        root = ET.parse(path).getroot()
        if root.attrib.get("name") != selector.suite:
            return None, "suite_name_mismatch"
        evidence = {name: int(root.attrib.get(name, "0") or 0)
                    for name in ("tests", "failures", "errors", "skipped")}
    except (OSError, ET.ParseError, ValueError):
        return None, "suite_xml_invalid"
    if evidence["tests"] == 0:
        return evidence, "suite_zero_tests"
    if evidence["tests"] < selector.min_tests:
        return evidence, f"suite_incomplete:{evidence['tests']}<{selector.min_tests}"
    if evidence["failures"]:
        return evidence, "suite_failed"
    if evidence["errors"]:
        return evidence, "suite_errored"
    if evidence["skipped"]:
        return evidence, "suite_skipped"
    return evidence, None


def sanitize(text: str) -> str:
    for name, value in os.environ.items():
        if value and len(value) >= 4 and any(
                marker in name.upper() for marker in ("PASSWORD", "TOKEN", "SECRET", "KEY", "CREDENTIAL", "AUTH")):
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


def run_selector(maven: str, selector: Selector,
                 environment: Mapping[str, str]) -> tuple[int, dict[str, int] | None, str | None, str]:
    started_ns = time.time_ns()
    command = [maven, "-B", "-pl", selector.module, "-am", f"-Dtest={selector.test}",
               "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false", "test"]
    try:
        result = subprocess.run(command, cwd=BACKEND, env=mapped_environment(environment), text=True,
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
    missing = [name for name in REQUIRED_ENV if not str(values.get(name, "")).strip()]
    if missing:
        return [f"missing:{name}" for name in missing]
    reasons = []
    if values["CAMPUS_M0202_B_SCHEMA_GUARD"] != EXPECTED_SCHEMA_GUARD:
        reasons.append("schema_guard_mismatch")
    if values["CAMPUS_M0202_B_PREPARE"] != EXPECTED_PREPARE:
        reasons.append("prepare_guard_mismatch")
    schema = jdbc_schema(str(values["CAMPUS_M0202_B_MYSQL_URL"]))
    if schema is None:
        reasons.append("mysql_url_not_disposable")
    elif schema != EXPECTED_SCHEMA_GUARD:
        reasons.append("mysql_url_schema_mismatch")
    return reasons


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "artifacts/acceptance/m02.02-b.json")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        assert environment_gate({})  # a missing dedicated environment is never PASS
        valid = {name: "value" for name in REQUIRED_ENV}
        valid["CAMPUS_M0202_B_SCHEMA_GUARD"] = EXPECTED_SCHEMA_GUARD
        valid["CAMPUS_M0202_B_PREPARE"] = EXPECTED_PREPARE
        valid["CAMPUS_M0202_B_MYSQL_URL"] = (
            "jdbc:mysql://127.0.0.1:3306/campus_m0202_b_acceptance?useSSL=false")
        assert environment_gate(valid) == []
        mismatched = dict(valid, CAMPUS_M0202_B_SCHEMA_GUARD="wrong")
        assert "schema_guard_mismatch" in environment_gate(mismatched)
        wrong_prepare = dict(valid, CAMPUS_M0202_B_PREPARE="SKIP_BASELINE")
        assert "prepare_guard_mismatch" in environment_gate(wrong_prepare)
        shared_schema = dict(valid, CAMPUS_M0202_B_MYSQL_URL="jdbc:mysql://127.0.0.1:3306/campus_m0202_acceptance")
        assert "mysql_url_schema_mismatch" in environment_gate(shared_schema)
        assert PREPARATION.test == "M0101BDatabasePreparationIT"
        assert [selector.module for selector in SELECTORS] == ["yudao-server", "yudao-server", "campus-module-media"]
        assert all("#" in selector.test or selector.module == "campus-module-media" for selector in SELECTORS)
        assert report_path(SELECTORS[2]).name == (
            "TEST-cn.iocoder.yudao.module.campus.media.infrastructure.M0202MySqlIntegrationTest.xml")
        polluted = dict(valid)
        polluted["CAMPUS_M0101_B_MYSQL_URL"] = "jdbc:mysql://127.0.0.1:3306/campus_acceptance"
        polluted["CAMPUS_M0202_MYSQL_URL"] = "jdbc:mysql://127.0.0.1:3306/campus_m0202_acceptance"
        mapped = mapped_environment(polluted)
        assert mapped["CAMPUS_M0101_B_MYSQL_URL"] == valid["CAMPUS_M0202_B_MYSQL_URL"]
        assert mapped["CAMPUS_M0202_MYSQL_URL"] == valid["CAMPUS_M0202_B_MYSQL_URL"]
        assert mapped["CAMPUS_M0101_B_SCHEMA_GUARD"] == EXPECTED_SCHEMA_GUARD
        assert mapped["CAMPUS_M0101_B_PREPARE"] == EXPECTED_PREPARE
        assert mapped["CAMPUS_M0202_MYSQL_PASSWORD"] == valid["CAMPUS_M0202_B_MYSQL_PASSWORD"]
        print("self-test passed: M02.02 B selectors, fixture mapping and fail-closed environment gate")
        return 0

    reasons = environment_gate()
    config_problems = set(reasons) & CONFIG_REASONS
    mysql_configured = all(os.environ.get(name, "").strip() for name in REQUIRED_ENV[:3]) and not config_problems
    valkey_configured = bool(os.environ.get(REQUIRED_ENV[3], "").strip()) and not config_problems
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
            prep_exit, preparation_evidence, preparation_reason, prep_diagnostic = run_selector(
                maven, PREPARATION, os.environ)
            suites[PREPARATION.test] = {
                **(preparation_evidence or {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}),
                "module": PREPARATION.module, "suite": PREPARATION.suite,
                "minTests": PREPARATION.min_tests, "exitCode": prep_exit, "reason": preparation_reason,
            }
            if preparation_reason:
                reasons.append(f"preparation:{preparation_reason}")
                if prep_diagnostic:
                    diagnostics.append(f"{PREPARATION.test}: {prep_diagnostic}")
            else:
                for selector in SELECTORS:
                    exit_code, evidence, reason, diagnostic = run_selector(maven, selector, os.environ)
                    suites[selector.test] = {
                        **(evidence or {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}),
                        "module": selector.module, "suite": selector.suite,
                        "minTests": selector.min_tests, "exitCode": exit_code, "reason": reason,
                    }
                    if reason:
                        reasons.append(f"{selector.test}:{reason}")
                        if diagnostic:
                            diagnostics.append(f"{selector.test}: {diagnostic}")
            status = "PASS" if not reasons else "FAIL"

    report = args.report if args.report.is_absolute() else ROOT / args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({
        "module": "M02.02", "level": "B", "status": status,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceSha": source_sha(),
        "requiredTests": [selector.test for selector in SELECTORS],
        "preparationEvidence": preparation_evidence,
        "preparationReason": preparation_reason,
        "mysqlConfigured": mysql_configured, "valkeyConfigured": valkey_configured,
        "environmentMapping": (
            "CAMPUS_M0202_B_{MYSQL_URL,MYSQL_USERNAME,MYSQL_PASSWORD,VALKEY_URL,SCHEMA_GUARD,PREPARE} "
            "-> CAMPUS_M0101_B_* for the server integration fixtures and "
            "CAMPUS_M0202_B_MYSQL_{URL,USERNAME,PASSWORD} -> CAMPUS_M0202_MYSQL_* for the media module fixture"
        ),
        "suites": suites, "reasons": reasons,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(status)
    for reason in reasons:
        print(f"- {reason}")
    for diagnostic in diagnostics:
        print(f"--- sanitized diagnostic ---\n{diagnostic}")
    return 0 if status == "PASS" else 2


if __name__ == "__main__":
    sys.exit(main())

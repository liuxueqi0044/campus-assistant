#!/usr/bin/env python3
"""Explicit M02.03 A-level gate for private proof authorization, proxying and audit."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import asdict
from pathlib import Path
from typing import Mapping, Sequence

import m02_acceptance as common


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "database" / "migrations" / "V0011__M02_03_media_access_log.sql"
REQUIRED_TESTS = (
    "MembershipVerificationProofPolicyProviderTest",
    "DownloadAuthorizationServiceTest",
    "MediaDownloadControllerBoundaryTest",
    "MediaDownloadResponseWriterTest",
    "MediaDownloadLocalStorageAcceptanceTest",
    "MediaDownloadGrantResponseTest",
    "M0203MySqlIntegrationTest",
)
REPORT_MODULES = (common.MEDIA, ROOT / "backend" / "campus-module-identity")


def configured_mysql(environment: Mapping[str, str]) -> tuple[bool, dict[str, str]]:
    selected = {
        "url": environment.get("CAMPUS_M0203_MYSQL_URL", "").strip(),
        "username": environment.get("CAMPUS_M0203_MYSQL_USERNAME", "").strip(),
        "password": environment.get("CAMPUS_M0203_MYSQL_PASSWORD", "").strip(),
    }
    return all(selected.values()), selected


def is_disposable_mysql_url(url: str) -> bool:
    value = url.strip()
    if not value.startswith("jdbc:mysql://") or value.count("?") > 1:
        return False
    if any(character in value for character in (";", "#", "\\", '"', "'")):
        return False
    if any(character.isspace() for character in value):
        return False
    match = re.fullmatch(r"jdbc:mysql://([^/?#;@]+)(?:/([^/?#;]+))?(?:\?([^#;]*))?", value)
    if match is None:
        return False
    schema = match.group(2) or ""
    return re.fullmatch(r"(?:campus_m0203_test|campus_m0203_acceptance)[A-Za-z0-9_]*", schema) is not None


def runner_environment(environment: Mapping[str, str], selected: Mapping[str, str]) -> dict[str, str]:
    result = common.runner_environment(environment, {
        "url": "", "username": "", "password": "",
    })
    result.update({
        "CAMPUS_M0203_MYSQL_URL": selected["url"],
        "CAMPUS_M0203_MYSQL_USERNAME": selected["username"],
        "CAMPUS_M0203_MYSQL_PASSWORD": selected["password"],
    })
    for name in ("CAMPUS_M0202_MYSQL_URL", "CAMPUS_M0202_MYSQL_USERNAME", "CAMPUS_M0202_MYSQL_PASSWORD"):
        result.pop(name, None)
    return result


def acceptance(environment: Mapping[str, str], report_path: Path) -> int:
    mysql_ready, selected = configured_mysql(environment)
    disposable = is_disposable_mysql_url(selected["url"]) if mysql_ready else False
    command = common.CommandResult(0, "", "")
    suites: dict[str, common.SuiteEvidence] = {}
    failures: list[dict[str, str]] = []
    actual_environment = runner_environment(environment, selected)
    if mysql_ready and disposable:
        started_ns = time.time_ns()
        command = common.run([
            common.maven_command(actual_environment), "-B", "-pl", "campus-module-media", "-am",
            "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false",
            "-Dtest=" + ",".join(REQUIRED_TESTS), "test",
        ], actual_environment)
        suites = common.parse_surefire(started_ns, REPORT_MODULES)
        failures = common.parse_surefire_failures(started_ns, actual_environment, REPORT_MODULES)
        status, reasons = common.evaluate(command, suites, True, REQUIRED_TESTS)
    elif mysql_ready:
        status, reasons = "FAIL", ["mysql_url_not_disposable"]
    else:
        status, reasons = "EXTERNAL_NOT_READY", ["mysql_external_not_ready"]

    payload = {
        "schemaVersion": 1,
        "generatedAt": common.now_utc(),
        "module": "M02.03",
        "level": "A",
        "status": status,
        "sourceSha": common.git_sha(),
        "migrationHash": common.sha256_file(MIGRATION) if MIGRATION.is_file() else "MISSING",
        "migrations": common.migration_summary(),
        "requiredTests": list(REQUIRED_TESTS),
        "suites": {name: asdict(suite) for name, suite in suites.items()},
        "suiteFailures": failures,
        "mavenExitCode": command.returncode,
        "launcherException": bool(command.exception),
        "mavenDiagnostic": common.diagnostics(command, actual_environment),
        "mysqlConfigured": mysql_ready,
        "mysqlUrlDisposable": disposable,
        "unverified": reasons,
        "cleanup": "M0203MySqlIntegrationTest cleans only the caller-provided disposable schema before and after each case.",
    }
    common.write_report(report_path, payload)
    print(status)
    for reason in reasons:
        print(f"- {reason}")
    if status == "FAIL":
        print(common.diagnostics(command, actual_environment))
        for failure in failures:
            print(f"{failure['suite']}.{failure['test']} [{failure['outcome']}]: {failure['detail']}")
    return 0 if status == "PASS" else 2


def self_test() -> int:
    good = common.CommandResult(0, "", "")
    suites = {name: common.SuiteEvidence(name, 1, 0, 0, 0, "fixture.xml") for name in REQUIRED_TESTS}
    assert common.evaluate(good, suites, True, REQUIRED_TESTS)[0] == "PASS"
    assert common.evaluate(good, {}, True, REQUIRED_TESTS)[0] == "FAIL"
    assert is_disposable_mysql_url("jdbc:mysql://127.0.0.1:3306/campus_m0203_acceptance?useSSL=false")
    assert not is_disposable_mysql_url("jdbc:mysql://127.0.0.1:3306/campus_acceptance")
    print("self-test passed: M02.03 suite and disposable-schema gates are fail-closed")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    report = args.report or ROOT / "artifacts" / "acceptance" / "m02.03-a.json"
    report = report if report.is_absolute() else ROOT / report
    return acceptance(os.environ, report)


if __name__ == "__main__":
    sys.exit(main())

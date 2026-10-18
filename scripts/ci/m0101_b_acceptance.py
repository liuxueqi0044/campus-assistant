#!/usr/bin/env python3
"""Strictly run M01.01 B preparation, then the full-server integration selector."""
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

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
SUITE_PREFIX = "cn.iocoder.yudao.server.integration."
PREPARATION = "M0101BDatabasePreparationIT"
FULL_SERVER = "M0101MemberOAuth2IntegrationIT"
REQUIRED_ENV = ("CAMPUS_M0101_B_MYSQL_URL", "CAMPUS_M0101_B_MYSQL_USERNAME", "CAMPUS_M0101_B_MYSQL_PASSWORD",
                "CAMPUS_M0101_B_VALKEY_URL", "CAMPUS_M0101_B_SCHEMA_GUARD", "CAMPUS_M0101_B_PREPARE")


def parse_fresh_suite(selector: str, started_ns: int) -> tuple[dict[str, int] | None, str | None]:
    path = BACKEND / "yudao-server" / "target" / "surefire-reports" / f"TEST-{SUITE_PREFIX}{selector}.xml"
    if not path.is_file() or path.stat().st_mtime_ns < started_ns:
        return None, "suite_missing_or_stale"
    try:
        root = ET.parse(path).getroot()
        if root.attrib.get("name") != SUITE_PREFIX + selector:
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


def sanitize_diagnostic_text(text: str) -> str:
    for name, value in os.environ.items():
        if value and any(marker in name.upper() for marker in ("PASSWORD", "TOKEN", "SECRET", "KEY", "CREDENTIAL", "AUTH")):
            text = text.replace(value, "<redacted>")
    # Maven and framework diagnostics can render credentials as key/value pairs even when the
    # exact value was not copied verbatim from an environment variable. Keep the failure useful
    # in the Actions log without turning the acceptance artifact into a credential sink.
    text = re.sub(
        r"(?i)(password|passwd|secret|token|authorization|api[_-]?key)\s*([:=])\s*[^\s,;]+",
        r"\1\2<redacted>",
        text,
    )
    return text


def suite_failure_diagnostic(selector: str) -> str:
    """Extract useful nested causes from the exact Surefire XML without publishing the whole report."""
    path = BACKEND / "yudao-server" / "target" / "surefire-reports" / f"TEST-{SUITE_PREFIX}{selector}.xml"
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return ""

    details: list[str] = []
    exception_pattern = re.compile(
        r"(?i)(caused by:|suppressed:|application failed|beancreationexception|"
        r"unsatisfieddependencyexception|nosuchbeandefinitionexception|could not resolve placeholder|"
        r"failed to configure a datasource|connection refused|table .* doesn't exist|"
        r"(?:[a-z_$][\w$]*\.)+[a-z_$][\w$]*(?:exception|error)(?::|$))"
    )
    for testcase in root.iter("testcase"):
        case_name = testcase.attrib.get("name", "unknown")
        for element in testcase:
            kind = element.tag.rsplit("}", 1)[-1]
            if kind not in ("error", "failure"):
                continue
            error_type = element.attrib.get("type", kind)
            message = " ".join(element.attrib.get("message", "").split())
            if message:
                message = message[:500]
                details.append(f"{case_name}: {error_type}: {message}")
            else:
                details.append(f"{case_name}: {error_type}")

            stack_text = element.text or ""
            matched = []
            for raw_line in stack_text.splitlines():
                line = " ".join(raw_line.split())
                if line and exception_pattern.search(line):
                    matched.append(line[:500])
            details.extend(matched[-30:])

    if not details:
        return ""
    # Preserve ordering while avoiding repeated wrapper exceptions from Spring's context cache.
    unique_details = list(dict.fromkeys(details))
    extracted = sanitize_diagnostic_text("\n".join(unique_details))[-5000:]
    return f"Surefire failure summary ({path.name}):\n{extracted}"


def diagnostics(completed: subprocess.CompletedProcess[str]) -> str:
    text = sanitize_diagnostic_text((completed.stdout + "\n" + completed.stderr).strip())
    # Spring's condition report can be thousands of lines long and otherwise hides the exception
    # chain that appeared earlier. Preserve a compact cause summary as well as the Maven tail.
    cause_pattern = re.compile(
        r"(?i)(caused by:|application failed|beancreationexception|unsatisfieddependencyexception|"
        r"nosuchbeandefinitionexception|illegalstateexception:|sqlexception|could not resolve placeholder|"
        r"failed to configure a datasource|connection refused|table .* doesn't exist)"
    )
    causes = [line.strip() for line in text.splitlines() if cause_pattern.search(line)]
    cause_summary = "\n".join(causes[-30:])[-1800:]
    tail = text[-2100:]
    if cause_summary:
        return f"Exception cause summary:\n{cause_summary}\n--- Maven tail ---\n{tail}"
    return tail


def runner_environment() -> dict[str, str]:
    environment = os.environ.copy()
    if not environment.get("JAVA_HOME"):
        tools = ROOT.parent / ".tools"
        jdk = next(iter(sorted(tools.glob("temurin-*/jdk-*"))), None)
        if jdk:
            environment["JAVA_HOME"] = str(jdk)
            environment["PATH"] = str(jdk / "bin") + os.pathsep + environment.get("PATH", "")
    return environment


def execute(maven: str, selector: str) -> tuple[int, dict[str, int] | None, str | None, str]:
    started_ns = time.time_ns()
    completed = subprocess.run([maven, "-B", "-pl", "yudao-server", "-am", f"-Dtest={selector}",
                                "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false", "test"],
                               cwd=BACKEND, env=runner_environment(), text=True, capture_output=True, check=False)
    evidence, reason = parse_fresh_suite(selector, started_ns)
    if completed.returncode != 0:
        reason = f"maven_exit:{completed.returncode}" if reason is None else f"maven_exit:{completed.returncode};{reason}"
    diagnostic = ""
    if reason:
        parts = [part for part in (suite_failure_diagnostic(selector), diagnostics(completed)) if part]
        diagnostic = "\n--- Process diagnostic ---\n".join(parts)
    return completed.returncode, evidence, reason, diagnostic


def maven_command() -> str | None:
    on_path = shutil.which("mvn")
    if on_path:
        return on_path
    bundled = ROOT.parent / ".tools" / "apache-maven-3.9.16" / "bin" / "mvn.cmd"
    return str(bundled) if bundled.is_file() else None


def source_sha() -> str:
    completed = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=False)
    return completed.stdout.strip() if completed.returncode == 0 else "UNKNOWN"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "artifacts/acceptance/m01.01-b.json")
    args = parser.parse_args()
    missing = [name for name in REQUIRED_ENV if not os.environ.get(name, "").strip()]
    mysql_ready = not any(name in missing for name in REQUIRED_ENV[:3])
    valkey_ready = REQUIRED_ENV[3] not in missing
    invalid_guard = os.environ.get("CAMPUS_M0101_B_PREPARE", "") not in ("", "IMPORT_FIXED_BASELINE")
    suites: dict[str, object] = {}
    if missing or invalid_guard:
        reasons = [f"missing:{name}" for name in missing] + (["prepare_guard_must_equal:IMPORT_FIXED_BASELINE"] if invalid_guard else [])
        status = "EXTERNAL_NOT_READY"
    else:
        maven = maven_command()
        if not maven:
            status, reasons = "EXTERNAL_NOT_READY", ["maven_not_found"]
        else:
            prep_exit, prep_evidence, prep_reason, prep_diagnostic = execute(maven, PREPARATION)
            suites[PREPARATION] = {**(prep_evidence or {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}),
                                   "exitCode": prep_exit, "reason": prep_reason, "diagnostic": prep_diagnostic}
            if prep_reason:
                status, reasons = "FAIL", [f"preparation:{prep_reason}"]
            else:
                full_exit, full_evidence, full_reason, full_diagnostic = execute(maven, FULL_SERVER)
                suites[FULL_SERVER] = {**(full_evidence or {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}),
                                        "exitCode": full_exit, "reason": full_reason, "diagnostic": full_diagnostic}
                status, reasons = ("PASS", []) if full_reason is None else ("FAIL", [f"full_server:{full_reason}"])
    report = args.report if args.report.is_absolute() else ROOT / args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({"module": "M01.01", "level": "B", "status": status, "generatedAt": datetime.now(timezone.utc).isoformat(),
                                  "sourceSha": source_sha(), "mysqlConfigured": mysql_ready,
                                  "valkeyConfigured": valkey_ready,
                                  "requiredTests": [PREPARATION, FULL_SERVER], "requiredEnvironmentCount": len(REQUIRED_ENV),
                                  "suites": suites, "reasons": reasons}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(status)
    for reason in reasons:
        print(f"- {reason}")
    if status == "FAIL":
        for name, suite in suites.items():
            diagnostic = suite.get("diagnostic") if isinstance(suite, dict) else None
            if diagnostic:
                print(f"--- {name} diagnostic (sanitized/truncated) ---")
                print(diagnostic)
    return 0 if status == "PASS" else 2


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Explicit M02.02 A-level acceptance gate for verified media binding.

The gate runs only the focused media unit tests, the real evidence adapter test, and the
opt-in MySQL integration class. A missing caller-provided disposable schema is reported as
EXTERNAL_NOT_READY; no in-memory substitute or empty Surefire result can become PASS.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Mapping, Sequence


ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
MEDIA = BACKEND / "campus-module-media"
MIGRATION = ROOT / "database" / "migrations" / "V0010__M02_02_media_binding.sql"
REQUIRED_TESTS = (
    "FileValidationPipelineTest",
    "MediaCompletionServiceTest",
    "MediaBindingServiceTest",
    "MembershipVerificationEvidenceAdapterTest",
    "M0202MySqlIntegrationTest",
)
MYSQL_ENV_NAMES = frozenset({
    "CAMPUS_M0202_MYSQL_URL",
    "CAMPUS_M0202_MYSQL_USERNAME",
    "CAMPUS_M0202_MYSQL_PASSWORD",
})
SENSITIVE_ENV_NAME = re.compile(r"(?i)(password|secret|token|key|auth|credential)")
DIAGNOSTIC_LIMIT = 6000


@dataclass(frozen=True)
class SuiteEvidence:
    name: str
    tests: int
    failures: int
    errors: int
    skipped: int
    file: str


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str
    exception: str | None = None


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git_sha() -> str:
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True,
                            capture_output=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else "UNKNOWN"


def migration_summary() -> list[dict[str, str]]:
    return [{"name": path.name, "sha256": sha256_file(path)}
            for path in sorted((ROOT / "database" / "migrations").glob("V*__*.sql"))]


def dependency_summary() -> dict[str, dict[str, object]]:
    paths = (BACKEND / "pom.xml", MEDIA / "pom.xml", MIGRATION)
    return {
        str(path.relative_to(ROOT)).replace("\\", "/"): {
            "sha256": sha256_file(path), "bytes": path.stat().st_size
        }
        for path in paths if path.is_file()
    }


def configured_mysql(environment: Mapping[str, str]) -> tuple[bool, dict[str, str]]:
    selected = {
        "url": environment.get("CAMPUS_M0202_MYSQL_URL", "").strip(),
        "username": environment.get("CAMPUS_M0202_MYSQL_USERNAME", "").strip(),
        "password": environment.get("CAMPUS_M0202_MYSQL_PASSWORD", "").strip(),
    }
    return all(selected.values()), selected


def is_disposable_mysql_url(url: str) -> bool:
    """Allow only a single, explicitly named M02.02 disposable schema.

    The integration test calls Flyway.clean(), so accepting a generic JDBC URL here could
    destroy an unrelated database. The schema is intentionally limited to a safe unquoted
    MySQL identifier with an optional underscore suffix.
    """
    value = url.strip()
    if not value.startswith("jdbc:mysql://") or value.count("?") > 1:
        return False
    if any(character in value for character in (";", "#", "\\", "\"", "'")):
        return False
    if any(character.isspace() for character in value):
        return False
    match = re.fullmatch(r"jdbc:mysql://([^/?#;@]+)(?:/([^/?#;]+))?(?:\\?([^#;]*))?", value)
    if match is None:
        return False
    schema = match.group(2) or ""
    return re.fullmatch(r"(?:campus_m0202_test|campus_m0202_acceptance)[A-Za-z0-9_]*", schema) is not None


def runner_environment(environment: Mapping[str, str], selected: Mapping[str, str]) -> dict[str, str]:
    result = dict(environment)
    result.update({
        "CAMPUS_M0202_MYSQL_URL": selected["url"],
        "CAMPUS_M0202_MYSQL_USERNAME": selected["username"],
        "CAMPUS_M0202_MYSQL_PASSWORD": selected["password"],
    })
    tools = ROOT.parent / ".tools"
    if not result.get("JAVA_HOME"):
        jdk = next(iter(sorted(tools.glob("temurin-*/jdk-*"))), None)
        if jdk:
            result["JAVA_HOME"] = str(jdk)
    if result.get("JAVA_HOME"):
        result["PATH"] = str(Path(result["JAVA_HOME"]) / "bin") + os.pathsep + result.get("PATH", "")
    return result


def maven_command(environment: Mapping[str, str]) -> str:
    configured = environment.get("CAMPUS_MAVEN_COMMAND", "").strip()
    if configured:
        return configured
    on_path = shutil.which("mvn")
    if on_path:
        return on_path
    bundled = ROOT.parent / ".tools" / "apache-maven-3.9.16" / "bin" / "mvn.cmd"
    return str(bundled) if bundled.is_file() else "mvn"


def parse_surefire(start_ns: int, modules: Sequence[Path] = (MEDIA,)) -> dict[str, SuiteEvidence]:
    reports: dict[str, SuiteEvidence] = {}
    for module in modules:
        directory = module / "target" / "surefire-reports"
        if not directory.is_dir():
            continue
        for path in directory.glob("TEST-*.xml"):
            if path.stat().st_mtime_ns < start_ns:
                continue
            try:
                root = ET.parse(path).getroot()
            except (OSError, ET.ParseError):
                continue
            cases = list(root.findall("testcase"))
            full_name = root.attrib.get("name", path.stem.removeprefix("TEST-"))
            name = full_name.rsplit(".", 1)[-1]
            reports[name] = SuiteEvidence(
                name=name,
                tests=int(root.attrib.get("tests", len(cases)) or 0),
                failures=int(root.attrib.get("failures", 0) or 0),
                errors=int(root.attrib.get("errors", 0) or 0),
                skipped=int(root.attrib.get("skipped", 0) or 0),
                file=str(path.relative_to(ROOT)).replace("\\", "/"),
            )
    return reports


def parse_surefire_failures(start_ns: int, environment: Mapping[str, str],
                            modules: Sequence[Path] = (MEDIA,)) -> list[dict[str, str]]:
    failures: list[dict[str, str]] = []
    for module in modules:
        directory = module / "target" / "surefire-reports"
        if not directory.is_dir():
            continue
        for path in directory.glob("TEST-*.xml"):
            if path.stat().st_mtime_ns < start_ns:
                continue
            try:
                root = ET.parse(path).getroot()
            except (OSError, ET.ParseError):
                continue
            suite = root.attrib.get("name", path.stem.removeprefix("TEST-")).rsplit(".", 1)[-1]
            for case in root.findall("testcase"):
                outcome = case.find("failure")
                if outcome is None:
                    outcome = case.find("error")
                if outcome is None:
                    continue
                detail = "\n".join(part for part in (
                    outcome.attrib.get("message", ""), outcome.text or "",
                ) if part).strip()
                failures.append({
                    "suite": suite,
                    "test": case.attrib.get("name", "unknown"),
                    "outcome": outcome.tag,
                    "type": outcome.attrib.get("type", ""),
                    "detail": sanitize(detail, environment)[-2000:],
                })
    return failures


def evaluate(command: CommandResult, suites: Mapping[str, SuiteEvidence], mysql_ready: bool,
             required_tests: Sequence[str] = REQUIRED_TESTS) -> tuple[str, list[str]]:
    reasons: list[str] = []
    if command.exception:
        reasons.append("runner_exception")
    if command.returncode != 0:
        reasons.append(f"maven_exit_{command.returncode}")
    for name in required_tests:
        suite = suites.get(name)
        if suite is None:
            reasons.append(f"required_test_missing:{name}")
        elif suite.tests == 0:
            reasons.append(f"required_test_zero:{name}")
        elif suite.skipped:
            reasons.append(f"required_test_skipped:{name}")
        elif suite.failures or suite.errors:
            reasons.append(f"required_test_failed:{name}")
    if not mysql_ready:
        reasons.append("mysql_external_not_ready")
    return ("PASS" if not reasons else
            ("EXTERNAL_NOT_READY" if reasons == ["mysql_external_not_ready"] else "FAIL"), reasons)


def run(command: Sequence[str], environment: Mapping[str, str]) -> CommandResult:
    try:
        completed = subprocess.run(list(command), cwd=BACKEND, env=dict(environment), text=True,
                                   capture_output=True, check=False)
        return CommandResult(completed.returncode, completed.stdout, completed.stderr)
    except Exception as exc:  # The report must expose launcher failures, never mask them.
        return CommandResult(255, "", "", type(exc).__name__)


def sanitize(text: str, environment: Mapping[str, str]) -> str:
    for name, value in environment.items():
        if value and len(value) >= 4 and (name in MYSQL_ENV_NAMES or SENSITIVE_ENV_NAME.search(name)):
            text = text.replace(value, "<redacted>")
    return re.sub(r"(?i)(password|passwd|secret|token|authorization)\s*([:=])\s*[^\s,;]+",
                  r"\1\2<redacted>", text)


def diagnostics(command: CommandResult, environment: Mapping[str, str]) -> str:
    text = sanitize("\n".join(part for part in (command.stdout, command.stderr) if part).strip(), environment)
    if command.exception:
        text = f"launcher exception: {command.exception}\n{text}".strip()
    lines = text.splitlines()
    if len(lines) > 40:
        lines = ["... <diagnostic truncated> ..."] + lines[-39:]
    text = "\n".join(lines)
    return text[-DIAGNOSTIC_LIMIT:] or "(Maven produced no diagnostic output)"


def write_report(path: Path, payload: Mapping[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def acceptance(environment: Mapping[str, str], report_path: Path) -> int:
    mysql_ready, selected = configured_mysql(environment)
    mysql_url_disposable = is_disposable_mysql_url(selected["url"]) if mysql_ready else False
    command = CommandResult(0, "", "")
    suites: dict[str, SuiteEvidence] = {}
    suite_failures: list[dict[str, str]] = []
    actual_environment = runner_environment(environment, selected)
    if mysql_ready and mysql_url_disposable:
        started_ns = time.time_ns()
        command = run([
            maven_command(actual_environment), "-B", "-pl", "campus-module-media", "-am",
            "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false",
            "-Dtest=" + ",".join(REQUIRED_TESTS), "test",
        ], actual_environment)
        suites = parse_surefire(started_ns)
        suite_failures = parse_surefire_failures(started_ns, actual_environment)
    if mysql_ready and mysql_url_disposable:
        status, reasons = evaluate(command, suites, mysql_ready)
    elif mysql_ready:
        status, reasons = "FAIL", ["mysql_url_not_disposable"]
    else:
        # Do not manufacture missing Surefire failures when the external database prerequisite
        # is absent; this is an explicit readiness result, never a pass or an empty test run.
        status, reasons = "EXTERNAL_NOT_READY", ["mysql_external_not_ready"]
    payload = {
        "schemaVersion": 1,
        "generatedAt": now_utc(),
        "module": "M02.02",
        "level": "A",
        "status": status,
        "sourceSha": git_sha(),
        "migrationHash": sha256_file(MIGRATION) if MIGRATION.is_file() else "MISSING",
        "migrations": migration_summary(),
        "dependencySummary": dependency_summary(),
        "requiredTests": list(REQUIRED_TESTS),
        "suites": {name: asdict(suite) for name, suite in suites.items()},
        "suiteFailures": suite_failures,
        "mavenExitCode": command.returncode,
        "launcherException": bool(command.exception),
        "mavenDiagnostic": diagnostics(command, actual_environment),
        "mysqlConfigured": mysql_ready,
        "mysqlUrlDisposable": mysql_url_disposable,
        "unverified": reasons,
        "cleanup": "M0202MySqlIntegrationTest cleans only the caller-provided disposable schema before and after each case.",
    }
    write_report(report_path, payload)
    print(status)
    for reason in reasons:
        print(f"- {reason}")
    if status == "FAIL":
        print(diagnostics(command, actual_environment))
        for failure in suite_failures:
            print(f"{failure['suite']}.{failure['test']} [{failure['outcome']}]: {failure['detail']}")
    return 0 if status == "PASS" else 2


def self_test() -> int:
    good = CommandResult(0, "", "")
    all_suites = {name: SuiteEvidence(name, 1, 0, 0, 0, "fixture.xml") for name in REQUIRED_TESTS}
    assert evaluate(good, all_suites, True)[0] == "PASS"
    assert evaluate(good, all_suites, False)[0] == "EXTERNAL_NOT_READY"
    assert evaluate(good, {**all_suites, REQUIRED_TESTS[-1]: SuiteEvidence(REQUIRED_TESTS[-1], 0, 0, 0, 0, "fixture.xml")}, True)[0] == "FAIL"
    assert evaluate(good, {**all_suites, REQUIRED_TESTS[-1]: SuiteEvidence(REQUIRED_TESTS[-1], 1, 0, 0, 1, "fixture.xml")}, True)[0] == "FAIL"
    assert evaluate(CommandResult(1, "", ""), all_suites, True)[0] == "FAIL"
    secret = "jdbc:mysql://ci-user:ci-password@localhost/campus_m0202"
    diagnostic = sanitize("url=" + secret + " password=ci-password ordinary", {
        "CAMPUS_M0202_MYSQL_URL": secret,
        "CAMPUS_M0202_MYSQL_PASSWORD": "ci-password",
        "ORDINARY": "ordinary",
    })
    assert secret not in diagnostic and "ci-password" not in diagnostic and "ordinary" in diagnostic
    print("self-test passed: MySQL readiness, Surefire evidence, and redaction gates are fail-closed")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    report = args.report or ROOT / "artifacts" / "acceptance" / "m02.02-a.json"
    report = report if report.is_absolute() else ROOT / report
    return acceptance(os.environ, report)


if __name__ == "__main__":
    sys.exit(main())

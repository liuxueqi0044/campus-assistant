#!/usr/bin/env python3
"""Explicit M01 A-level acceptance gates for identity and membership boundaries.

It deliberately proves only local MySQL storage and deterministic module behavior. Live WeChat,
Valkey-backed server startup, and token delivery are reported as separate B/C evidence rather than
being silently replaced by mocks or treated as a reason to pass an unavailable external check.
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
IDENTITY = BACKEND / "campus-module-identity"
M0101_REQUIRED_TESTS = (
    "WechatIdentityBindingServiceTest",
    "LoginApplicationServiceTest",
    "WechatLoginControllerTest",
    "CampusLogoutControllerTest",
    "PlatformTenantExecutorTest",
    "M0101MySqlIntegrationTest",
)
M0103_REQUIRED_TESTS = (
    "MembershipServiceTest",
    "MembershipVerificationConfigurationTest",
    "MembershipVerificationExceptionTest",
    "VerificationApplicationServiceTest",
    "MembershipTest",
    "VerificationAttemptTest",
    "MembershipVerificationControllerTest",
    "VerificationReviewControllerTest",
    "M0103MySqlIntegrationTest",
)
M0101_MYSQL_ENV_ALIASES = {
    "url": ("CAMPUS_M0101_MYSQL_URL", "CAMPUS_MYSQL_IT_URL"),
    "username": ("CAMPUS_M0101_MYSQL_USERNAME", "CAMPUS_MYSQL_IT_USERNAME"),
    "password": ("CAMPUS_M0101_MYSQL_PASSWORD", "CAMPUS_MYSQL_IT_PASSWORD"),
}
M0103_MYSQL_ENV_ALIASES = {
    "url": ("CAMPUS_M0103_MYSQL_URL",),
    "username": ("CAMPUS_M0103_MYSQL_USERNAME",),
    "password": ("CAMPUS_M0103_MYSQL_PASSWORD",),
}
MODULE_SPECS = {
    "M01.01": {
        "tests": M0101_REQUIRED_TESTS,
        "mysql_aliases": M0101_MYSQL_ENV_ALIASES,
        "env_prefix": "CAMPUS_M0101_MYSQL",
        "external_unverified": [
            "B: real member/OAuth2 persistence and token issuance integration",
            "C: real WeChat code2session, timeout behavior, and mobile client login/logout",
        ],
        "cleanup": "M0101MySqlIntegrationTest cleans only the caller-provided disposable schema before and after each case.",
    },
    "M01.03": {
        "tests": M0103_REQUIRED_TESTS,
        "mysql_aliases": M0103_MYSQL_ENV_ALIASES,
        "env_prefix": "CAMPUS_M0103_MYSQL",
        "external_unverified": [
            "B: real evidence/media ownership adapter supplied by M02",
            "B: real school-scoped reviewer authorization supplied by M01.04",
        ],
        "cleanup": "M0103MySqlIntegrationTest cleans only the dedicated disposable M01.03 schema before and after each case.",
    },
}
# Compatibility aliases retained for existing M01.01 callers and self-tests.
REQUIRED_TESTS = M0101_REQUIRED_TESTS
MYSQL_ENV_ALIASES = M0101_MYSQL_ENV_ALIASES
MYSQL_ENV_NAMES = frozenset(
    name for spec in MODULE_SPECS.values() for aliases in spec["mysql_aliases"].values() for name in aliases
)
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
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else "UNKNOWN"


def migration_summary() -> list[dict[str, str]]:
    return [{"name": path.name, "sha256": sha256_file(path)}
            for path in sorted((ROOT / "database" / "migrations").glob("V*__*.sql"))]


def dependency_summary() -> dict[str, dict[str, object]]:
    paths = (BACKEND / "pom.xml", BACKEND / "campus-common" / "pom.xml", IDENTITY / "pom.xml")
    return {str(path.relative_to(ROOT)).replace("\\", "/"): {"sha256": sha256_file(path), "bytes": path.stat().st_size}
            for path in paths if path.is_file()}


def configured_mysql(environment: Mapping[str, str], module: str = "M01.01") -> tuple[bool, dict[str, str]]:
    selected: dict[str, str] = {}
    for field, aliases in MODULE_SPECS[module]["mysql_aliases"].items():
        selected[field] = next((environment[name].strip() for name in aliases if environment.get(name, "").strip()), "")
    return all(selected.values()), selected


def runner_environment(environment: Mapping[str, str], selected: Mapping[str, str],
                       module: str = "M01.01") -> dict[str, str]:
    result = dict(environment)
    prefix = MODULE_SPECS[module]["env_prefix"]
    result.update({
        f"{prefix}_URL": selected["url"],
        f"{prefix}_USERNAME": selected["username"],
        f"{prefix}_PASSWORD": selected["password"],
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


def parse_surefire(start_ns: int) -> dict[str, SuiteEvidence]:
    reports: dict[str, SuiteEvidence] = {}
    directory = IDENTITY / "target" / "surefire-reports"
    if not directory.is_dir():
        return reports
    for path in directory.glob("TEST-*.xml"):
        if path.stat().st_mtime_ns < start_ns:
            continue
        try:
            root = ET.parse(path).getroot()
        except (OSError, ET.ParseError):
            continue
        cases = list(root.findall("testcase"))
        name = root.attrib.get("name", path.stem.removeprefix("TEST-")).rsplit(".", 1)[-1]
        reports[name] = SuiteEvidence(
            name=name,
            tests=int(root.attrib.get("tests", len(cases)) or 0),
            failures=int(root.attrib.get("failures", 0) or 0),
            errors=int(root.attrib.get("errors", 0) or 0),
            skipped=int(root.attrib.get("skipped", 0) or 0),
            file=str(path.relative_to(ROOT)).replace("\\", "/"),
        )
    return reports


def parse_surefire_failures(start_ns: int, environment: Mapping[str, str]) -> list[dict[str, str]]:
    failures: list[dict[str, str]] = []
    directory = IDENTITY / "target" / "surefire-reports"
    if not directory.is_dir():
        return failures
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
    return ("PASS" if not reasons else ("EXTERNAL_NOT_READY" if reasons == ["mysql_external_not_ready"] else "FAIL"), reasons)


def run(command: Sequence[str], environment: Mapping[str, str]) -> CommandResult:
    try:
        complete = subprocess.run(list(command), cwd=BACKEND, env=dict(environment), text=True, capture_output=True, check=False)
        return CommandResult(complete.returncode, complete.stdout, complete.stderr)
    except Exception as exc:  # The gate must report launcher failures, never mask them.
        return CommandResult(255, "", "", type(exc).__name__)


def sanitize(text: str, environment: Mapping[str, str]) -> str:
    for name, value in environment.items():
        if value and len(value) >= 4 and (name in MYSQL_ENV_NAMES or SENSITIVE_ENV_NAME.search(name)):
            text = text.replace(value, "<redacted>")
    return re.sub(r"(?i)(password|passwd|secret|token|authorization)\s*([:=])\s*[^\s,;]+",
                  r"\1\2<redacted>", text)


def diagnostics(command: CommandResult, environment: Mapping[str, str]) -> str:
    text = sanitize("\n".join(part for part in (command.stdout, command.stderr) if part).strip(), environment)
    lines = text.splitlines()
    if len(lines) > 40:
        lines = ["... <diagnostic truncated> ..."] + lines[-39:]
    text = "\n".join(lines)
    return text[-DIAGNOSTIC_LIMIT:] or "(Maven produced no diagnostic output)"


def write_report(path: Path, payload: Mapping[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def acceptance(environment: Mapping[str, str], report_path: Path, module: str = "M01.01") -> int:
    spec = MODULE_SPECS[module]
    required_tests: Sequence[str] = spec["tests"]
    mysql_ready, selected = configured_mysql(environment, module)
    command = CommandResult(0, "", "")
    suites: dict[str, SuiteEvidence] = {}
    suite_failures: list[dict[str, str]] = []
    actual_environment = runner_environment(environment, selected, module)
    if mysql_ready:
        started_ns = time.time_ns()
        command = run([
            maven_command(actual_environment), "-B", "-pl", "campus-module-identity", "-am",
            "-Dsurefire.failIfNoSpecifiedTests=false", "-DfailIfNoTests=false",
            "-Dtest=" + ",".join(required_tests), "test",
        ], actual_environment)
        suites = parse_surefire(started_ns)
        suite_failures = parse_surefire_failures(started_ns, actual_environment)
        status, reasons = evaluate(command, suites, mysql_ready, required_tests)
    else:
        # Do not pretend empty Surefire output is test evidence. The unavailable database is an
        # explicit external prerequisite, so this is distinguishable from a configured run that
        # somehow produced no selected test reports (which evaluate() marks as FAIL).
        status, reasons = "EXTERNAL_NOT_READY", ["mysql_external_not_ready"]
    payload = {
        "schemaVersion": 1, "generatedAt": now_utc(), "module": module, "level": "A",
        "status": status, "sourceSha": git_sha(), "migrations": migration_summary(),
        "dependencySummary": dependency_summary(), "requiredTests": list(required_tests),
        "suites": {name: asdict(suite) for name, suite in suites.items()},
        "suiteFailures": suite_failures,
        "mavenExitCode": command.returncode, "launcherException": bool(command.exception),
        "mavenDiagnostic": diagnostics(command, actual_environment), "mysqlConfigured": mysql_ready,
        "valkeyRequired": False, "unverified": reasons,
        "externalUnverified": spec["external_unverified"],
        "cleanup": spec["cleanup"],
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
    m0103_suites = {name: SuiteEvidence(name, 1, 0, 0, 0, "fixture.xml") for name in M0103_REQUIRED_TESTS}
    assert evaluate(good, m0103_suites, True, M0103_REQUIRED_TESTS)[0] == "PASS"
    print("self-test passed: missing MySQL, zero/skipped tests, and Maven failures cannot become green")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--module", choices=tuple(MODULE_SPECS), default="M01.01")
    parser.add_argument("--level", choices=("A",), default="A")
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    report = args.report or ROOT / "artifacts" / "acceptance" / f"{args.module.lower().replace('.', '-')}-a.json"
    report = report if report.is_absolute() else ROOT / report
    return acceptance(os.environ, report, args.module)


if __name__ == "__main__":
    sys.exit(main())

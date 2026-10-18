#!/usr/bin/env python3
"""Small, explicit acceptance runner for the M00 foundation.

The runner deliberately does not rely on Maven's default test discovery.  It records the
selected test classes, their Surefire evidence, and the external readiness decision in a
machine-readable report.  A report is useful only when a green process also contained every
required test, so skipped and zero-test suites are failures at this boundary.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Mapping, Sequence


ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
COMMON = BACKEND / "campus-common"
M00_04_TESTS = (
    "RequestCanonicalizerTest",
    "IdempotentCommandExecutorTest",
    "M004MySqlIdempotencyIT",
)
M00_04_MYSQL_ENV_ALIASES = {
    "url": ("CAMPUS_MYSQL_IT_URL", "CAMPUS_M004_MYSQL_URL", "CAMPUS_M003_MYSQL_URL"),
    "username": (
        "CAMPUS_MYSQL_IT_USERNAME",
        "CAMPUS_M004_MYSQL_USERNAME",
        "CAMPUS_M003_MYSQL_USERNAME",
    ),
    "password": ("CAMPUS_MYSQL_IT_PASSWORD", "CAMPUS_M004_MYSQL_PASSWORD", "CAMPUS_M003_MYSQL_PASSWORD"),
}
M00_05_MYSQL_ENV_ALIASES = {
    "url": ("CAMPUS_MYSQL_IT_URL", "CAMPUS_M005_MYSQL_URL"),
    "username": ("CAMPUS_MYSQL_IT_USERNAME", "CAMPUS_M005_MYSQL_USERNAME"),
    "password": ("CAMPUS_MYSQL_IT_PASSWORD", "CAMPUS_M005_MYSQL_PASSWORD"),
}
MODULE_SPECS = {
    "M00.04": {"tests": M00_04_TESTS, "mysql_aliases": M00_04_MYSQL_ENV_ALIASES},
    "M00.05": {"tests": ("M005MySqlOutboxIT",), "mysql_aliases": M00_05_MYSQL_ENV_ALIASES},
}
# Kept as a compatibility alias for the M00.04-focused self-tests and callers.
REQUIRED_TESTS = M00_04_TESTS
MYSQL_ENV_NAMES = frozenset(
    name for spec in MODULE_SPECS.values() for aliases in spec["mysql_aliases"].values() for name in aliases
)
SENSITIVE_ENV_NAME = re.compile(r"(?i)(password|secret|token|key|auth|credential)")
MAVEN_DIAGNOSTIC_LIMIT = 6000


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


def git_sha() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, capture_output=True, check=False
    )
    return result.stdout.strip() if result.returncode == 0 else "UNKNOWN"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def migration_summary() -> list[dict[str, str]]:
    result = []
    for path in sorted((ROOT / "database" / "migrations").glob("V*__*.sql")):
        result.append({"name": path.name, "sha256": sha256_file(path)})
    return result


def dependency_summary() -> dict[str, object]:
    # Names and versions are safe evidence; never include lockfile URLs, tokens, or environment.
    summary: dict[str, object] = {}
    for path in (
        BACKEND / "pom.xml",
        COMMON / "pom.xml",
        BACKEND / "campus-test-support" / "pom.xml",
        BACKEND / "docs" / "dependency-baseline.txt",
        ROOT / "frontend" / "apps" / "student" / "package.json",
        ROOT / "frontend" / "apps" / "student" / "package-lock.json",
    ):
        if path.is_file():
            summary[str(path.relative_to(ROOT)).replace("\\", "/")] = {
                "sha256": sha256_file(path),
                "bytes": path.stat().st_size,
            }
    return summary


def env_presence(
    environment: Mapping[str, str], aliases: Mapping[str, Sequence[str]]
) -> dict[str, bool]:
    return {
        key: any(bool(environment.get(name, "").strip()) for name in names)
        for key, names in aliases.items()
    }


def parse_surefire(start_ns: int) -> dict[str, SuiteEvidence]:
    reports: dict[str, SuiteEvidence] = {}
    report_dir = COMMON / "target" / "surefire-reports"
    if not report_dir.is_dir():
        return reports
    for path in report_dir.glob("TEST-*.xml"):
        if path.stat().st_mtime_ns < start_ns:
            continue
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError):
            continue
        name = root.attrib.get("name", path.stem.removeprefix("TEST-"))
        # Surefire's attributes are authoritative, but count child nodes as a fallback for older
        # plugin output. This prevents a malformed/empty report from becoming a false green.
        cases = list(root.findall("testcase"))
        tests = int(root.attrib.get("tests", len(cases)) or 0)
        skipped = int(root.attrib.get("skipped", sum(1 for case in cases if case.find("skipped") is not None)) or 0)
        failures = int(root.attrib.get("failures", sum(1 for case in cases if case.find("failure") is not None)) or 0)
        errors = int(root.attrib.get("errors", sum(1 for case in cases if case.find("error") is not None)) or 0)
        reports[name.rsplit(".", 1)[-1]] = SuiteEvidence(
            name=name,
            tests=tests,
            failures=failures,
            errors=errors,
            skipped=skipped,
            file=str(path.relative_to(ROOT)).replace("\\", "/"),
        )
    return reports


def evaluate_run(
    command: CommandResult,
    suites: Mapping[str, SuiteEvidence],
    mysql_ready: bool,
    required_tests: Sequence[str] = REQUIRED_TESTS,
) -> tuple[str, list[str]]:
    """Return (status, reasons), keeping process and test evidence independent."""
    reasons: list[str] = []
    if command.exception:
        reasons.append("runner_exception")
    if command.returncode != 0:
        reasons.append(f"maven_exit_{command.returncode}")
    for test_name in required_tests:
        suite = suites.get(test_name)
        if suite is None:
            reasons.append(f"required_test_missing:{test_name}")
            continue
        if suite.tests == 0:
            reasons.append(f"required_test_zero:{test_name}")
        # The opt-in MySQL class is expected to be skipped when no external database is
        # configured. That condition is reported as EXTERNAL_NOT_READY below; a skip in a
        # configured run (or in either deterministic unit test) remains a hard failure.
        if suite.skipped and (mysql_ready or test_name != "M004MySqlIdempotencyIT"):
            reasons.append(f"required_test_skipped:{test_name}")
        if suite.failures or suite.errors:
            reasons.append(f"required_test_failed:{test_name}")
    if not mysql_ready:
        reasons.append("mysql_external_not_ready")
    return ("PASS" if not reasons else ("EXTERNAL_NOT_READY" if reasons == ["mysql_external_not_ready"] else "FAIL"), reasons)


def run_command(command: Sequence[str], cwd: Path, environment: Mapping[str, str]) -> CommandResult:
    try:
        completed = subprocess.run(
            list(command), cwd=cwd, env=dict(environment), text=True, capture_output=True, check=False
        )
        return CommandResult(completed.returncode, completed.stdout, completed.stderr)
    except Exception as exc:  # noqa: BLE001 - the report must surface launcher failures
        return CommandResult(255, "", "", type(exc).__name__)


def sanitize_diagnostics(command: CommandResult, environment: Mapping[str, str]) -> str:
    """Keep Maven's useful tail while preventing configured values from reaching logs/reports."""
    text = "\n".join(part for part in (command.stdout, command.stderr) if part).strip()
    if command.exception:
        text = f"launcher exception: {command.exception}\n{text}".strip()
    sensitive_values = (
        value
        for name, value in environment.items()
        if value and len(value) >= 4 and (name in MYSQL_ENV_NAMES or SENSITIVE_ENV_NAME.search(name))
    )
    for value in sorted(sensitive_values, key=len, reverse=True):
        text = text.replace(value, "<redacted>")
    # Cover common Maven/plugin diagnostics even when a tool prints a secret that was not present
    # verbatim in the environment mapping (for example a generated connection string).
    text = re.sub(
        r"(?i)(password|passwd|secret|token|authorization)\s*([:=])\s*[^\s,;]+",
        r"\1\2<redacted>",
        text,
    )
    lines = text.splitlines()
    if len(lines) > 40:
        lines = ["... <diagnostic truncated> ..."] + lines[-39:]
    text = "\n".join(lines)
    if len(text) > MAVEN_DIAGNOSTIC_LIMIT:
        text = "... <diagnostic truncated> ...\n" + text[-(MAVEN_DIAGNOSTIC_LIMIT - 32):]
    return text or "(Maven produced no diagnostic output)"


def report_payload(
    *,
    module: str,
    required_tests: Sequence[str],
    status: str,
    reasons: Iterable[str],
    command: CommandResult,
    diagnostic: str,
    suites: Mapping[str, SuiteEvidence],
    mysql_ready: bool,
    mutation: dict[str, object],
) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "generatedAt": now_utc(),
        "module": module,
        "level": "A",
        "status": status,
        "sourceSha": git_sha(),
        "migrations": migration_summary(),
        "dependencySummary": dependency_summary(),
        "requiredTests": list(required_tests),
        "suites": {key: vars(value) for key, value in suites.items()},
        "mavenExitCode": command.returncode,
        "launcherException": bool(command.exception),
        "mavenDiagnostic": diagnostic,
        "mysqlConfigured": mysql_ready,
        "unverified": list(reasons),
        "mutationGuard": mutation,
        "cleanup": {"conclusion": "Maven generated reports are retained for artifact upload; no database cleanup is claimed by this runner."},
    }


def write_report(path: Path, payload: Mapping[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def maven_test_command(test_names: Sequence[str], *extra_properties: str) -> list[str]:
    """Build the clean-reactor-safe command used by every module selector."""
    return [
        "mvn",
        "-B",
        "-pl",
        "campus-common",
        "-am",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-DfailIfNoTests=false",
        *extra_properties,
        f"-Dtest={','.join(test_names)}",
        "test",
    ]


def acceptance(environment: Mapping[str, str], report_path: Path, module: str = "M00.04") -> int:
    spec = MODULE_SPECS[module]
    required_tests: Sequence[str] = spec["tests"]
    presence = env_presence(environment, spec["mysql_aliases"])
    mysql_ready = all(presence.values())
    # Do not manufacture a local/in-memory substitute when the external A-level database is
    # absent. The report is intentionally EXTERNAL_NOT_READY and the non-zero gate tells CI that
    # the evidence is incomplete; a configured run below always invokes the explicit selector.
    if not mysql_ready:
        command = CommandResult(0, "", "")
        suites: dict[str, SuiteEvidence] = {}
        status, reasons = "EXTERNAL_NOT_READY", ["mysql_external_not_ready"]
    else:
        started_ns = time.time_ns()
        command = run_command(
            maven_test_command(required_tests),
            BACKEND,
            environment,
        )
        suites = parse_surefire(started_ns)
        status, reasons = evaluate_run(command, suites, mysql_ready, required_tests)
    mutation: dict[str, object] = (
        {"status": "NOT_APPLICABLE", "reason": "M00.04-only mutation guard"}
        if module != "M00.04"
        else {"status": "NOT_RUN", "reason": "requires configured isolated MySQL"}
    )

    # The mutation is a guard, not a second acceptance result. A configured database must fail
    # the concurrent test after its identity unique key is removed; a green mutation is a gate bug.
    if module == "M00.04" and mysql_ready and status == "PASS":
        mutation_started = time.time_ns()
        mutation_command = run_command(
            maven_test_command(
                ("M004MySqlIdempotencyIT#fiftyConcurrentSameKeyCallsWriteOneBusinessRow",),
                "-Dcampus.m004.mutation.drop-unique=true",
            ),
            BACKEND,
            environment,
        )
        mutation_suites = parse_surefire(mutation_started)
        mutation_suite = mutation_suites.get("M004MySqlIdempotencyIT")
        mutation_failed_as_expected = (
            mutation_command.returncode != 0
            and mutation_suite is not None
            and mutation_suite.tests > 0
            and mutation_suite.skipped == 0
            and mutation_suite.failures + mutation_suite.errors > 0
        )
        mutation = {
            "status": "PASS" if mutation_failed_as_expected else "FAIL",
            "expected": "concurrent test must fail after dropping campus_idempotency identity unique key",
            "mavenExitCode": mutation_command.returncode,
            "suiteFound": mutation_suite is not None,
        }
        if not mutation_failed_as_expected:
            status = "FAIL"
            reasons.append("mutation_guard_did_not_fail")

    payload = report_payload(
        module=module,
        required_tests=required_tests,
        status=status,
        reasons=reasons,
        command=command,
        diagnostic=sanitize_diagnostics(command, environment),
        suites=suites,
        mysql_ready=mysql_ready,
        mutation=mutation,
    )
    write_report(report_path, payload)
    print(status)
    if command.returncode != 0 or command.exception:
        print("Maven diagnostic (sanitized/truncated):")
        print(sanitize_diagnostics(command, environment))
    if not mysql_ready:
        print(f"EXTERNAL_NOT_READY: MySQL URL, username, and password variables are required for {module} A.")
    for reason in reasons:
        print(f"- {reason}")
    return 0 if status == "PASS" else 2


def self_test() -> int:
    good = CommandResult(0, "", "")
    skipped = {name: SuiteEvidence(name, 1, 0, 0, 0, "fixture") for name in REQUIRED_TESTS}
    skipped["M004MySqlIdempotencyIT"] = SuiteEvidence("M004MySqlIdempotencyIT", 1, 0, 0, 1, "fixture")
    assert evaluate_run(good, skipped, True)[0] == "FAIL"
    incomplete = {name: SuiteEvidence(name, 1, 0, 0, 0, "fixture") for name in REQUIRED_TESTS[:-1]}
    assert evaluate_run(good, incomplete, True)[0] == "FAIL"
    assert evaluate_run(CommandResult(1, "", ""), skipped, True)[0] == "FAIL"
    assert evaluate_run(good, {}, False)[0] == "FAIL"
    assert evaluate_run(good, {}, True)[0] == "FAIL"
    outbox = ("M005MySqlOutboxIT",)
    outbox_suite = {"M005MySqlOutboxIT": SuiteEvidence("M005MySqlOutboxIT", 5, 0, 0, 0, "fixture")}
    assert evaluate_run(good, outbox_suite, True, outbox)[0] == "PASS"
    assert "-am" in maven_test_command(outbox)
    assert "-Dtest=M005MySqlOutboxIT" in maven_test_command(outbox)
    assert "-Dcampus.m004.mutation.drop-unique=true" not in maven_test_command(outbox)
    print("self-test passed: launcher errors, skipped/zero/missing tests, external readiness and module selectors are enforced")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--module", choices=tuple(MODULE_SPECS), default="M00.04")
    parser.add_argument("--level", choices=("A",), default="A")
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--self-test", action="store_true", help="run runner invariants without Maven or MySQL")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    report_path = args.report
    if report_path is None:
        report_path = ROOT / "artifacts" / "acceptance" / f"{args.module.lower().replace('.', '-')}-a.json"
    elif not report_path.is_absolute():
        report_path = ROOT / report_path
    return acceptance(os.environ, report_path, args.module)


if __name__ == "__main__":
    sys.exit(main())

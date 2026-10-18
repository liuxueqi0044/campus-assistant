"""Fail-closed self-tests for the fixed M01.01 B full-server gate."""

from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "ci" / "m0101_b_acceptance.py"
SPEC = importlib.util.spec_from_file_location("m0101_b_acceptance", SCRIPT)
assert SPEC and SPEC.loader
GATE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = GATE
SPEC.loader.exec_module(GATE)


class M0101BAcceptanceGateTest(unittest.TestCase):

    def test_missing_external_environment_is_not_a_pass(self) -> None:
        environment = os.environ.copy()
        for name in GATE.REQUIRED_ENV:
            environment.pop(name, None)
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "m01.01-b.json"
            completed = subprocess.run(
                [sys.executable, str(SCRIPT), "--report", str(report)],
                cwd=ROOT,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertNotEqual(completed.returncode, 0)
            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(payload["status"], "EXTERNAL_NOT_READY")
            self.assertIs(payload["mysqlConfigured"], False)
            self.assertIs(payload["valkeyConfigured"], False)
            self.assertEqual(payload["requiredTests"], [GATE.PREPARATION, GATE.FULL_SERVER])
            self.assertNotIn("requiredEnvironment", payload)

    def test_surefire_evidence_rejects_skips(self) -> None:
        with tempfile.TemporaryDirectory() as directory, patch.object(GATE, "BACKEND", Path(directory)):
            reports = Path(directory) / "yudao-server" / "target" / "surefire-reports"
            reports.mkdir(parents=True)
            path = reports / f"TEST-{GATE.SUITE_PREFIX}{GATE.PREPARATION}.xml"
            started_ns = time.time_ns()
            path.write_text(
                f'<testsuite name="{GATE.SUITE_PREFIX}{GATE.PREPARATION}" tests="1" '
                'failures="0" errors="0" skipped="1"/>',
                encoding="utf-8",
            )
            os.utime(path, ns=(started_ns + 1_000_000, started_ns + 1_000_000))

            evidence, reason = GATE.parse_fresh_suite(GATE.PREPARATION, started_ns)

            self.assertEqual(evidence, {"tests": 1, "failures": 0, "errors": 0, "skipped": 1})
            self.assertEqual(reason, "suite_skipped")

    def test_diagnostics_preserve_early_cause_before_large_condition_report(self) -> None:
        completed = subprocess.CompletedProcess(
            args=["mvn"],
            returncode=1,
            stdout="Caused by: example startup exception\n" + ("condition report noise\n" * 400),
            stderr="",
        )

        diagnostic = GATE.diagnostics(completed)

        self.assertIn("Exception cause summary:", diagnostic)
        self.assertIn("Caused by: example startup exception", diagnostic)
        self.assertIn("--- Maven tail ---", diagnostic)

    def test_suite_failure_diagnostic_extracts_nested_cause_from_surefire_xml(self) -> None:
        with tempfile.TemporaryDirectory() as directory, patch.object(GATE, "BACKEND", Path(directory)):
            reports = Path(directory) / "yudao-server" / "target" / "surefire-reports"
            reports.mkdir(parents=True)
            path = reports / f"TEST-{GATE.SUITE_PREFIX}{GATE.FULL_SERVER}.xml"
            giant_context = "Failed to load ApplicationContext for [" + ("customizer, " * 300) + "]"
            path.write_text(
                f'''<testsuite name="{GATE.SUITE_PREFIX}{GATE.FULL_SERVER}" tests="1" failures="0" errors="1" skipped="0">
  <testcase name="loginPersistsIdentityAndIssuesBearerToken">
    <error type="java.lang.IllegalStateException" message="{giant_context}"><![CDATA[
java.lang.IllegalStateException: {giant_context}
    at org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContext(DefaultCacheAwareContextLoaderDelegate.java:180)
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'memberAuthController'
Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 'example.required'
    at org.springframework.util.PropertyPlaceholderHelper.parseStringValue(PropertyPlaceholderHelper.java:180)
]]></error>
  </testcase>
</testsuite>''',
                encoding="utf-8",
            )

            diagnostic = GATE.suite_failure_diagnostic(GATE.FULL_SERVER)

            self.assertIn("Surefire failure summary", diagnostic)
            self.assertIn("UnsatisfiedDependencyException", diagnostic)
            self.assertIn("Could not resolve placeholder 'example.required'", diagnostic)
            self.assertLessEqual(len(diagnostic), 5500)


if __name__ == "__main__":
    unittest.main()

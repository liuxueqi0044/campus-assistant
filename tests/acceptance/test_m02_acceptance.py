"""Self-tests for the M02.02 MySQL acceptance gate."""

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "m02_acceptance.py"
SPEC = importlib.util.spec_from_file_location("m02_acceptance", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class M02AcceptanceGateTest(unittest.TestCase):
    def suites(self, *, tests=1, skipped=0):
        return {
            name: MODULE.SuiteEvidence(name, tests, 0, 0, skipped, "fixture.xml")
            for name in MODULE.REQUIRED_TESTS
        }

    def test_full_evidence_passes_when_isolated_mysql_is_configured(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), self.suites(), True)[0], "PASS")

    def test_missing_mysql_is_external_not_ready(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), self.suites(), False)[0], "EXTERNAL_NOT_READY")

    def test_missing_mysql_acceptance_writes_external_not_ready_without_suite_fabrication(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "m02.02-a.json"
            result = MODULE.acceptance({}, report)
            payload = json.loads(report.read_text(encoding="utf-8"))
        self.assertEqual(result, 2)
        self.assertEqual(payload["status"], "EXTERNAL_NOT_READY")
        self.assertEqual(payload["suites"], {})

    def test_missing_or_zero_or_skipped_or_failed_suite_cannot_pass(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), {}, True)[0], "FAIL")
        self.assertEqual(MODULE.evaluate(
            MODULE.CommandResult(0, "", ""), self.suites(tests=0), True
        )[0], "FAIL")
        self.assertEqual(MODULE.evaluate(
            MODULE.CommandResult(0, "", ""), self.suites(skipped=1), True
        )[0], "FAIL")
        failed = self.suites()
        failed["MediaBindingServiceTest"] = MODULE.SuiteEvidence(
            "MediaBindingServiceTest", 1, 1, 0, 0, "fixture.xml"
        )
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), failed, True)[0], "FAIL")

    def test_maven_launcher_failure_cannot_be_masked(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(1, "", ""), self.suites(), True)[0], "FAIL")

    def test_surefire_parser_collects_fresh_reports_from_multiple_modules(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(MODULE, "ROOT", Path(directory)):
            modules = (Path(directory) / "media", Path(directory) / "identity")
            for index, module in enumerate(modules):
                reports = module / "target" / "surefire-reports"
                reports.mkdir(parents=True)
                name = f"Fixture{index}Test"
                (reports / f"TEST-example.{name}.xml").write_text(
                    f'<testsuite name="example.{name}" tests="1" failures="0" errors="0" skipped="0">'
                    '<testcase name="passes"/></testsuite>', encoding="utf-8")

            evidence = MODULE.parse_surefire(0, modules)

        self.assertEqual(set(evidence), {"Fixture0Test", "Fixture1Test"})
        self.assertTrue(all(suite.tests == 1 for suite in evidence.values()))

    def test_mysql_environment_requires_all_three_dedicated_values(self):
        ready, selected = MODULE.configured_mysql({
            "CAMPUS_M0202_MYSQL_URL": "jdbc:mysql://localhost/campus_m0202",
            "CAMPUS_M0202_MYSQL_USERNAME": "fixture-user",
            "CAMPUS_M0202_MYSQL_PASSWORD": "fixture-password",
        })
        self.assertTrue(ready)
        self.assertEqual(selected["url"], "jdbc:mysql://localhost/campus_m0202")
        ready, _ = MODULE.configured_mysql({
            "CAMPUS_M0202_MYSQL_URL": "jdbc:mysql://localhost/campus_m0202",
            "CAMPUS_M0202_MYSQL_USERNAME": "fixture-user",
        })
        self.assertFalse(ready)

    def test_mysql_url_allowlist_requires_an_explicit_disposable_schema(self):
        self.assertTrue(MODULE.is_disposable_mysql_url(
            "jdbc:mysql://localhost:3306/campus_m0202_test_20260918?useSSL=false&serverTimezone=UTC"
        ))
        self.assertTrue(MODULE.is_disposable_mysql_url(
            "jdbc:mysql://127.0.0.1/campus_m0202_acceptance_unique?allowPublicKeyRetrieval=true"
        ))
        for url in (
            "jdbc:mysql://localhost/production",
            "jdbc:mysql://localhost/campus",
            "jdbc:mysql://localhost/",
            "jdbc:mysql://localhost",
            "jdbc:mysql://localhost/campus_m0202_test;DROP DATABASE campus_m0202_test",
            "jdbc:mysql://localhost/campus_m0202_test?x=1;DROP TABLE campus_media_asset",
        ):
            self.assertFalse(MODULE.is_disposable_mysql_url(url), url)

    def test_invalid_disposable_url_fails_without_starting_maven(self):
        environment = {
            "CAMPUS_M0202_MYSQL_URL": "jdbc:mysql://localhost/campus",
            "CAMPUS_M0202_MYSQL_USERNAME": "fixture-user",
            "CAMPUS_M0202_MYSQL_PASSWORD": "fixture-password",
        }
        with tempfile.TemporaryDirectory() as directory, patch.object(
            MODULE, "run", side_effect=AssertionError("Maven must not start")
        ) as runner:
            report = Path(directory) / "m02.02-a.json"
            result = MODULE.acceptance(environment, report)
            self.assertFalse(runner.called)
            payload = json.loads(report.read_text(encoding="utf-8"))
        self.assertEqual(result, 2)
        self.assertEqual(payload["status"], "FAIL")
        self.assertEqual(payload["unverified"], ["mysql_url_not_disposable"])

    def test_diagnostics_redact_database_values_and_bound_output(self):
        secret = "jdbc:mysql://fixture-user:fixture-password@localhost/campus_m0202"
        diagnostic = MODULE.sanitize(
            secret + "\npassword=fixture-password\n" + "ordinary-value " * 1000,
            {
                "CAMPUS_M0202_MYSQL_URL": secret,
                "CAMPUS_M0202_MYSQL_PASSWORD": "fixture-password",
                "ORDINARY": "ordinary-value",
            },
        )
        self.assertNotIn(secret, diagnostic)
        self.assertNotIn("fixture-password", diagnostic)
        self.assertIn("ordinary-value", diagnostic)

    def test_report_contains_source_and_v0010_hash(self):
        self.assertEqual(len(MODULE.sha256_file(MODULE.MIGRATION)), 64)
        self.assertIn("M0202MySqlIntegrationTest", MODULE.REQUIRED_TESTS)
        self.assertEqual(MODULE.REQUIRED_TESTS[-1], "M0202MySqlIntegrationTest")


if __name__ == "__main__":
    unittest.main()

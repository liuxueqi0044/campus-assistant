"""Self-tests for the M00.06 acceptance gate."""

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "m00_acceptance.py"
SPEC = importlib.util.spec_from_file_location("m00_acceptance", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class AcceptanceGateTest(unittest.TestCase):
    def suite(self, skipped=0, tests=1):
        return {
            name: MODULE.SuiteEvidence(name, tests, 0, 0, skipped, "fixture.xml")
            for name in MODULE.REQUIRED_TESTS
        }

    def test_nonzero_launcher_is_not_masked(self):
        result = MODULE.evaluate_run(MODULE.CommandResult(1, "", ""), self.suite(), True)
        self.assertEqual(result[0], "FAIL")

    def test_skipped_required_test_is_not_masked(self):
        result = MODULE.evaluate_run(MODULE.CommandResult(0, "", ""), self.suite(skipped=1), True)
        self.assertEqual(result[0], "FAIL")

    def test_zero_tests_are_not_masked(self):
        result = MODULE.evaluate_run(MODULE.CommandResult(0, "", ""), self.suite(tests=0), True)
        self.assertEqual(result[0], "FAIL")

    def test_missing_mysql_is_external_not_ready_when_evidence_is_complete(self):
        result = MODULE.evaluate_run(MODULE.CommandResult(0, "", ""), self.suite(), False)
        self.assertEqual(result[0], "EXTERNAL_NOT_READY")

    def test_maven_diagnostic_redacts_environment_and_is_bounded(self):
        secret = "jdbc:mysql://ci-user:ci-password@localhost/db"
        command = MODULE.CommandResult(1, secret + "\n" + "x" * 10000 + "\nCI=true ordinary-value", "password=ci-password")
        diagnostic = MODULE.sanitize_diagnostics(
            command,
            {
                "CAMPUS_MYSQL_IT_URL": secret,
                "CAMPUS_MYSQL_IT_PASSWORD": "ci-password",
                "CI": "true",
                "ORDINARY_ENV": "ordinary-value",
            },
        )
        self.assertLessEqual(len(diagnostic), MODULE.MAVEN_DIAGNOSTIC_LIMIT)
        self.assertNotIn(secret, diagnostic)
        self.assertNotIn("ci-password", diagnostic)
        self.assertIn("<redacted>", diagnostic)
        self.assertIn("ordinary-value", diagnostic)

    def test_m005_selects_only_outbox_it_and_keeps_reactor_flags(self):
        tests = MODULE.MODULE_SPECS["M00.05"]["tests"]
        command = MODULE.maven_test_command(tests)
        self.assertIn("-am", command)
        self.assertIn("-Dsurefire.failIfNoSpecifiedTests=false", command)
        self.assertIn("-DfailIfNoTests=false", command)
        self.assertIn("-Dtest=M005MySqlOutboxIT", command)
        self.assertNotIn("campus.m004.mutation.drop-unique=true", command)

    def test_m005_missing_or_skipped_outbox_evidence_is_not_green(self):
        tests = MODULE.MODULE_SPECS["M00.05"]["tests"]
        good = {
            "M005MySqlOutboxIT": MODULE.SuiteEvidence("M005MySqlOutboxIT", 5, 0, 0, 0, "fixture.xml")
        }
        skipped = {
            "M005MySqlOutboxIT": MODULE.SuiteEvidence("M005MySqlOutboxIT", 5, 0, 0, 1, "fixture.xml")
        }
        self.assertEqual(MODULE.evaluate_run(MODULE.CommandResult(0, "", ""), good, True, tests)[0], "PASS")
        self.assertEqual(MODULE.evaluate_run(MODULE.CommandResult(0, "", ""), skipped, True, tests)[0], "FAIL")
        self.assertEqual(MODULE.evaluate_run(MODULE.CommandResult(0, "", ""), {}, True, tests)[0], "FAIL")


if __name__ == "__main__":
    unittest.main()

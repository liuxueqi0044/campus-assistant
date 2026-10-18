"""Self-tests for the M01.01 MySQL acceptance gate."""

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "m01_acceptance.py"
SPEC = importlib.util.spec_from_file_location("m01_acceptance", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class M01AcceptanceGateTest(unittest.TestCase):
    def suites(self, *, tests=1, skipped=0):
        return {name: MODULE.SuiteEvidence(name, tests, 0, 0, skipped, "fixture.xml") for name in MODULE.REQUIRED_TESTS}

    def test_full_evidence_passes_when_mysql_is_configured(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), self.suites(), True)[0], "PASS")

    def test_missing_mysql_is_external_not_ready(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), self.suites(), False)[0], "EXTERNAL_NOT_READY")

    def test_zero_skipped_missing_or_failed_suites_do_not_pass(self):
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), self.suites(tests=0), True)[0], "FAIL")
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), self.suites(skipped=1), True)[0], "FAIL")
        partial = self.suites()
        partial.pop("M0101MySqlIntegrationTest")
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(0, "", ""), partial, True)[0], "FAIL")
        self.assertEqual(MODULE.evaluate(MODULE.CommandResult(1, "", ""), self.suites(), True)[0], "FAIL")

    def test_generic_mysql_variables_are_normalized_for_the_opt_in_test(self):
        ready, selected = MODULE.configured_mysql({
            "CAMPUS_MYSQL_IT_URL": "jdbc:mysql://localhost/campus_m0101_it",
            "CAMPUS_MYSQL_IT_USERNAME": "fixture-user",
            "CAMPUS_MYSQL_IT_PASSWORD": "fixture-password",
        })
        self.assertTrue(ready)
        self.assertEqual(selected["url"], "jdbc:mysql://localhost/campus_m0101_it")

    def test_m0103_uses_only_its_dedicated_schema_variables(self):
        environment = {
            "CAMPUS_MYSQL_IT_URL": "jdbc:mysql://localhost/shared_acceptance",
            "CAMPUS_MYSQL_IT_USERNAME": "shared-user",
            "CAMPUS_MYSQL_IT_PASSWORD": "shared-password",
        }
        ready, _ = MODULE.configured_mysql(environment, "M01.03")
        self.assertFalse(ready)
        environment.update({
            "CAMPUS_M0103_MYSQL_URL": "jdbc:mysql://localhost/campus_m0103_acceptance",
            "CAMPUS_M0103_MYSQL_USERNAME": "fixture-user",
            "CAMPUS_M0103_MYSQL_PASSWORD": "fixture-password",
        })
        ready, selected = MODULE.configured_mysql(environment, "M01.03")
        self.assertTrue(ready)
        actual = MODULE.runner_environment(environment, selected, "M01.03")
        self.assertEqual(actual["CAMPUS_M0103_MYSQL_URL"], environment["CAMPUS_M0103_MYSQL_URL"])

    def test_m0103_requires_every_selected_suite(self):
        suites = {
            name: MODULE.SuiteEvidence(name, 1, 0, 0, 0, "fixture.xml")
            for name in MODULE.M0103_REQUIRED_TESTS
        }
        self.assertEqual(MODULE.evaluate(
            MODULE.CommandResult(0, "", ""), suites, True, MODULE.M0103_REQUIRED_TESTS)[0], "PASS")
        suites.pop("M0103MySqlIntegrationTest")
        self.assertEqual(MODULE.evaluate(
            MODULE.CommandResult(0, "", ""), suites, True, MODULE.M0103_REQUIRED_TESTS)[0], "FAIL")

    def test_surefire_failure_details_are_redacted_and_bounded(self):
        original_identity = MODULE.IDENTITY
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "target" / "surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-fixture.xml").write_text(
                '<testsuite name="example.FixtureTest" tests="1" failures="1" errors="0" skipped="0">'
                '<testcase name="fails"><failure type="AssertionError" '
                'message="password=fixture-secret expected true">stack trace</failure></testcase>'
                '</testsuite>', encoding="utf-8")
            MODULE.IDENTITY = root
            try:
                failures = MODULE.parse_surefire_failures(0, {"TEST_PASSWORD": "fixture-secret"})
            finally:
                MODULE.IDENTITY = original_identity
        self.assertEqual(failures[0]["suite"], "FixtureTest")
        self.assertNotIn("fixture-secret", failures[0]["detail"])
        self.assertIn("<redacted>", failures[0]["detail"])


if __name__ == "__main__":
    unittest.main()

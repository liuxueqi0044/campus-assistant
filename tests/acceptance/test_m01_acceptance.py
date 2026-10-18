"""Self-tests for the M01.01 MySQL acceptance gate."""

import importlib.util
import sys
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


if __name__ == "__main__":
    unittest.main()

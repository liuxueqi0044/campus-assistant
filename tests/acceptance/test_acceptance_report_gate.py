"""Self-tests for the aggregate acceptance evidence gate."""

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "check_acceptance_reports.py"
SPEC = importlib.util.spec_from_file_location("check_acceptance_reports", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def passing_payload(module="M00.05"):
    return {
        "module": module,
        "level": "A",
        "status": "PASS",
        "mysqlConfigured": True,
        "sourceSha": "abc123",
        "requiredTests": ["RealMySqlIT"],
        "suites": {
            "RealMySqlIT": {
                "tests": 1,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
            }
        },
    }


class AcceptanceReportGateTest(unittest.TestCase):
    def test_complete_report_passes(self):
        self.assertEqual(MODULE.validate_report("M00.05", passing_payload()), [])

    def test_missing_skipped_or_failed_evidence_is_rejected(self):
        payload = passing_payload()
        payload["status"] = "EXTERNAL_NOT_READY"
        payload["suites"]["RealMySqlIT"]["skipped"] = 1
        errors = MODULE.validate_report("M00.05", payload)
        self.assertIn("status_not_pass:EXTERNAL_NOT_READY", errors)
        self.assertIn("required_suite_skipped:RealMySqlIT", errors)

    def test_m0004_requires_the_mutation_guard(self):
        payload = passing_payload("M00.04")
        self.assertIn("mutation_guard_not_pass", MODULE.validate_report("M00.04", payload))
        payload["mutationGuard"] = {"status": "PASS"}
        self.assertEqual(MODULE.validate_report("M00.04", payload), [])

    def test_b_report_requires_exact_level_and_valkey(self):
        payload = passing_payload("M01.01")
        payload["level"] = "B"
        errors = MODULE.validate_report("M01.01", payload, "B")
        self.assertIn("valkey_not_configured", errors)
        payload["valkeyConfigured"] = True
        self.assertEqual(MODULE.validate_report("M01.01", payload, "B"), [])
        self.assertIn("level_mismatch:B!=expected:A", MODULE.validate_report("M01.01", payload))


if __name__ == "__main__":
    unittest.main()

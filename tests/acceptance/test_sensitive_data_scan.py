"""Self-tests for the sensitive fixture/evidence scanner."""

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "scan_test_data.py"
SPEC = importlib.util.spec_from_file_location("scan_test_data", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class SensitiveDataScanTest(unittest.TestCase):

    def test_safe_synthetic_fixture_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "safe.json"
            fixture.write_text('{"user":"U1","secret":"TEST_SECRET_NOT_A_CREDENTIAL"}', encoding="utf-8")
            scanned, findings = MODULE.scan([fixture])
            self.assertEqual(scanned, 1)
            self.assertEqual(findings, [])

    def test_personal_and_secret_shapes_fail_without_copying_values(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "unsafe.json"
            fixture.write_text(
                '{"mobile":"13812345678","accessSecret":"very-real-looking-secret-value"}',
                encoding="utf-8",
            )
            scanned, findings = MODULE.scan([fixture])
            serialized = json.dumps(findings)
            self.assertEqual(scanned, 1)
            self.assertEqual({finding["rule"] for finding in findings}, {"china-mobile", "credential-value"})
            self.assertNotIn("13812345678", serialized)
            self.assertNotIn("very-real-looking-secret-value", serialized)


if __name__ == "__main__":
    unittest.main()

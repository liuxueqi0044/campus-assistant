"""Self-tests for the independent M02.02 B gate script."""

import importlib.util
import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch


REPO = Path(__file__).resolve().parents[2]


def load(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    assert specification and specification.loader
    module = importlib.util.module_from_spec(specification)
    sys.modules[specification.name] = module
    specification.loader.exec_module(module)
    return module


MODULE = load("m0202_b_acceptance", REPO / "scripts" / "ci" / "m0202_b_acceptance.py")
EVIDENCE_GATE = load("check_acceptance_reports", REPO / "scripts" / "ci" / "check_acceptance_reports.py")


def dedicated_environment() -> dict[str, str]:
    return {
        "CAMPUS_M0202_B_MYSQL_URL":
            "jdbc:mysql://127.0.0.1:3306/campus_m0202_b_acceptance?useSSL=false&serverTimezone=UTC",
        "CAMPUS_M0202_B_MYSQL_USERNAME": "campus_ci",
        "CAMPUS_M0202_B_MYSQL_PASSWORD": "TEST_CAMPUS_CI_NOT_A_CREDENTIAL",
        "CAMPUS_M0202_B_VALKEY_URL": "redis://127.0.0.1:6379/0",
        "CAMPUS_M0202_B_SCHEMA_GUARD": "campus_m0202_b_acceptance",
        "CAMPUS_M0202_B_PREPARE": "IMPORT_FIXED_BASELINE",
    }


def suite_xml(*, tests: int, failures: int = 0, errors: int = 0, skipped: int = 0) -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<testsuite name="{name}" tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">'
        "</testsuite>"
    ).format(name="{name}", tests=tests, failures=failures, errors=errors, skipped=skipped)


class M0202BGateTest(unittest.TestCase):
    def test_missing_dedicated_environment_is_never_ready(self):
        reasons = MODULE.environment_gate({})
        self.assertEqual(len(reasons), len(MODULE.REQUIRED_ENV))
        self.assertTrue(all(reason.startswith("missing:") for reason in reasons))

    def test_guard_and_prepare_tokens_must_match_the_frozen_contract(self):
        environment = dedicated_environment()
        self.assertEqual(MODULE.environment_gate(environment), [])
        self.assertIn("schema_guard_mismatch",
                      MODULE.environment_gate(dict(environment, CAMPUS_M0202_B_SCHEMA_GUARD="campus_acceptance")))
        self.assertIn("prepare_guard_mismatch",
                      MODULE.environment_gate(dict(environment, CAMPUS_M0202_B_PREPARE="SKIP")))

    def test_shared_schema_and_unsafe_jdbc_urls_are_refused_before_any_test(self):
        environment = dedicated_environment()
        shared = dict(environment, CAMPUS_M0202_B_MYSQL_URL="jdbc:mysql://127.0.0.1:3306/campus_m0202_acceptance")
        self.assertIn("mysql_url_schema_mismatch", MODULE.environment_gate(shared))
        for unsafe in (
            "jdbc:mysql://127.0.0.1:3306/campus_m0202_b_acceptance;DROP=1",
            "jdbc:mysql://127.0.0.1:3306/campus_m0202_b_acceptance?useSSL=false?x=1",
            "jdbc:mysql://127.0.0.1:3306/",
            "jdbc:mysql://127.0.0.1:3306/campus_m0202_b_acceptance/extra",
        ):
            self.assertIn("mysql_url_not_disposable",
                          MODULE.environment_gate(dict(environment, CAMPUS_M0202_B_MYSQL_URL=unsafe)), unsafe)

    def test_dedicated_environment_overwrites_shared_fixture_variables(self):
        environment = dedicated_environment()
        environment["CAMPUS_M0101_B_MYSQL_URL"] = "jdbc:mysql://127.0.0.1:3306/campus_acceptance"
        environment["CAMPUS_M0202_MYSQL_PASSWORD"] = "shared-secret"
        mapped = MODULE.mapped_environment(environment)
        self.assertEqual(mapped["CAMPUS_M0101_B_MYSQL_URL"], environment["CAMPUS_M0202_B_MYSQL_URL"])
        self.assertEqual(mapped["CAMPUS_M0101_B_SCHEMA_GUARD"], "campus_m0202_b_acceptance")
        self.assertEqual(mapped["CAMPUS_M0101_B_PREPARE"], "IMPORT_FIXED_BASELINE")
        self.assertEqual(mapped["CAMPUS_M0202_MYSQL_URL"], environment["CAMPUS_M0202_B_MYSQL_URL"])
        self.assertEqual(mapped["CAMPUS_M0202_MYSQL_PASSWORD"], environment["CAMPUS_M0202_B_MYSQL_PASSWORD"])

    def test_selector_layout_covers_both_modules_in_the_frozen_order(self):
        self.assertEqual(MODULE.PREPARATION.test, "M0101BDatabasePreparationIT")
        self.assertEqual([selector.module for selector in MODULE.SELECTORS],
                         ["yudao-server", "yudao-server", "campus-module-media"])
        self.assertEqual(MODULE.SELECTORS[0].test.split("#")[1],
                         "memberVerificationUsesRealMediaBindingsAndSetOrderIdempotency")
        self.assertEqual(MODULE.SELECTORS[1].test.split("#")[1],
                         "mediaBindingsRollBackWhenVerificationInsertFails")
        self.assertEqual(MODULE.SELECTORS[2].suite,
                         "cn.iocoder.yudao.module.campus.media.infrastructure.M0202MySqlIntegrationTest")

    def test_only_fresh_complete_passing_evidence_can_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(MODULE, "BACKEND", Path(directory)):
                selector = MODULE.SELECTORS[2]
                path = MODULE.report_path(selector)
                path.parent.mkdir(parents=True)
                stale_start = time.time_ns() + 1_000_000_000
                path.write_text(suite_xml(tests=4).format(name=selector.suite), encoding="utf-8")
                self.assertEqual(MODULE.parse_fresh_suite(selector, stale_start)[1], "suite_missing_or_stale")
                fresh_start = time.time_ns()
                path.write_text(suite_xml(tests=4).format(name=selector.suite), encoding="utf-8")
                evidence, reason = MODULE.parse_fresh_suite(selector, fresh_start)
                self.assertIsNone(reason)
                self.assertEqual(evidence, {"tests": 4, "failures": 0, "errors": 0, "skipped": 0})
                for xml, expected in (
                    (suite_xml(tests=0).format(name=selector.suite), "suite_zero_tests"),
                    (suite_xml(tests=2).format(name=selector.suite), "suite_incomplete:2<4"),
                    (suite_xml(tests=4, failures=1).format(name=selector.suite), "suite_failed"),
                    (suite_xml(tests=4, errors=1).format(name=selector.suite), "suite_errored"),
                    (suite_xml(tests=4, skipped=1).format(name=selector.suite), "suite_skipped"),
                    (suite_xml(tests=4).format(name="other.Suite"), "suite_name_mismatch"),
                    ("not xml", "suite_xml_invalid"),
                ):
                    start = time.time_ns()
                    path.write_text(xml, encoding="utf-8")
                    self.assertEqual(MODULE.parse_fresh_suite(selector, start)[1], expected)

    def test_selector_runs_the_owning_module_with_the_mapped_environment(self):
        environment = dedicated_environment()
        for selector in (MODULE.PREPARATION,) + MODULE.SELECTORS:
            with self.subTest(selector=selector.test):
                captured = {}

                def fake_run(command, **kwargs):
                    captured["command"] = list(command)
                    captured["environment"] = dict(kwargs["env"])
                    captured["cwd"] = kwargs["cwd"]
                    return subprocess.CompletedProcess(command, 0, "ok", "")

                with patch.object(MODULE.subprocess, "run", fake_run), \
                        patch.object(MODULE, "parse_fresh_suite", return_value=(None, "suite_missing_or_stale")):
                    exit_code, _, reason, _ = MODULE.run_selector("mvn-fake", selector, environment)
                self.assertEqual(exit_code, 0)
                self.assertEqual(reason, "suite_missing_or_stale")
                self.assertIn(selector.module, captured["command"])
                self.assertIn(f"-Dtest={selector.test}", captured["command"])
                self.assertIn("-Dsurefire.failIfNoSpecifiedTests=false", captured["command"])
                self.assertEqual(captured["environment"]["CAMPUS_M0101_B_MYSQL_URL"],
                                 environment["CAMPUS_M0202_B_MYSQL_URL"])
                self.assertEqual(captured["environment"]["CAMPUS_M0202_MYSQL_URL"],
                                 environment["CAMPUS_M0202_B_MYSQL_URL"])

    def test_maven_launcher_failure_cannot_be_masked(self):
        selector = MODULE.SELECTORS[0]

        def raising_run(command, **kwargs):
            raise OSError("maven missing")

        with patch.object(MODULE.subprocess, "run", raising_run):
            exit_code, evidence, reason, _ = MODULE.run_selector("mvn-fake", selector, dedicated_environment())
        self.assertEqual((exit_code, evidence, reason), (127, None, "maven_exception:OSError"))

    def _acceptance(self, directory: str, *, tests_for, environment=None, maven="mvn-fake"):
        """:returns: (exit code, report payload, list of (test, module) run in order)."""
        report = Path(directory) / "m02.02-b.json"
        order: list[tuple[str, str]] = []
        by_test = {selector.test: selector for selector in (MODULE.PREPARATION,) + MODULE.SELECTORS}

        def fake_run(command, **kwargs):
            value = next(part.split("=", 1)[1] for part in command if part.startswith("-Dtest="))
            selector = by_test[value]
            order.append((value, selector.module))
            path = MODULE.report_path(selector)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(suite_xml(tests=tests_for(selector)).format(name=selector.suite), encoding="utf-8")
            return subprocess.CompletedProcess(command, 0, f"ran {value}", "")

        patches = [
            patch.object(MODULE, "BACKEND", Path(directory)),
            patch.object(MODULE, "maven_command", return_value=maven),
            patch.object(MODULE, "source_sha", return_value="0" * 40),
            patch.object(MODULE.subprocess, "run", fake_run),
        ]
        with patch.dict(os.environ, environment if environment is not None else dedicated_environment(), clear=True):
            for active in patches:
                active.start()
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    code = MODULE.main(["--report", str(report)])
            finally:
                for active in reversed(patches):
                    active.stop()
        return code, json.loads(report.read_text(encoding="utf-8")), order

    def test_full_pass_report_satisfies_the_shared_evidence_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            code, payload, order = self._acceptance(
                directory, tests_for=lambda selector: max(selector.min_tests, 1))
        self.assertEqual(code, 0)
        self.assertEqual(payload["status"], "PASS")
        self.assertEqual(payload["module"], "M02.02")
        self.assertEqual(payload["level"], "B")
        self.assertTrue(payload["mysqlConfigured"] and payload["valkeyConfigured"])
        self.assertEqual(EVIDENCE_GATE.validate_report("M02.02", payload, "B"), [])
        self.assertEqual(order, [(selector.test, selector.module)
                                 for selector in (MODULE.PREPARATION,) + MODULE.SELECTORS])

    def test_zero_or_incomplete_selector_evidence_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            code, payload, _ = self._acceptance(
                directory,
                tests_for=lambda selector: 0 if selector is MODULE.SELECTORS[1] else max(selector.min_tests, 1))
        self.assertEqual(code, 2)
        self.assertEqual(payload["status"], "FAIL")
        self.assertIn("M0203TenantBoundaryIntegrationIT#mediaBindingsRollBackWhenVerificationInsertFails:"
                      "suite_zero_tests", payload["reasons"])
        self.assertEqual(payload["suites"]["M0202MySqlIntegrationTest"]["tests"], 4)
        self.assertIsNone(payload["suites"]["M0202MySqlIntegrationTest"]["reason"])

    def test_missing_dedicated_environment_reports_external_not_ready_without_suites(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "m02.02-b.json"
            with patch.dict(os.environ, {}, clear=True), \
                    patch.object(MODULE, "source_sha", return_value="0" * 40), \
                    patch.object(MODULE.subprocess, "run", side_effect=AssertionError("must not run maven")):
                with contextlib.redirect_stdout(io.StringIO()):
                    code = MODULE.main(["--report", str(report)])
            payload = json.loads(report.read_text(encoding="utf-8"))
        self.assertEqual(code, 2)
        self.assertEqual(payload["status"], "EXTERNAL_NOT_READY")
        self.assertEqual(payload["suites"], {})
        self.assertFalse(payload["mysqlConfigured"])
        self.assertFalse(payload["valkeyConfigured"])

    def test_missing_maven_launcher_is_external_not_ready(self):
        with tempfile.TemporaryDirectory() as directory:
            code, payload, order = self._acceptance(directory, maven=None,
                                                    tests_for=lambda selector: max(selector.min_tests, 1))
        self.assertEqual(code, 2)
        self.assertEqual(payload["status"], "EXTERNAL_NOT_READY")
        self.assertEqual(payload["reasons"], ["maven_not_found"])
        self.assertEqual(order, [])

    def test_preparation_failure_stops_before_business_selectors(self):
        with tempfile.TemporaryDirectory() as directory:
            code, payload, order = self._acceptance(
                directory,
                tests_for=lambda selector: 0 if selector is MODULE.PREPARATION else max(selector.min_tests, 1))
        self.assertEqual(code, 2)
        self.assertEqual(payload["status"], "FAIL")
        self.assertEqual([test for test, _ in order], [MODULE.PREPARATION.test])
        self.assertEqual(payload["preparationReason"], "suite_zero_tests")


if __name__ == "__main__":
    unittest.main()

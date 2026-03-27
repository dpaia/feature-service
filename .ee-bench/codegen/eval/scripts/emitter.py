#!/usr/bin/env python3
"""Emit EE-bench JSON v2.0 evaluation output (6 criteria)."""
import json
import os
import re
import sys

MAX_OUTPUT = 8192


def _read(path: str, limit: int = MAX_OUTPUT) -> str:
    try:
        with open(path) as f:
            text = f.read(limit)
        return text
    except FileNotFoundError:
        return ""


def _load_json(path: str) -> dict:
    try:
        with open(path) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def _prefix(name: str) -> str:
    """Strip parameterized suffixes: Foo.Bar(x: 1) -> Foo.Bar"""
    return re.sub(r"\(.*\)$", "", name)


def _test_in(name: str, name_set: set) -> bool:
    """Match by exact name first, then by prefix."""
    if name in name_set:
        return True
    return _prefix(name) in name_set


def _evaluate_criterion(
    expected_names: list[str],
    eval_passed: set[str],
    eval_failed: set[str],
    baseline_passed: set[str],
    baseline_failed: set[str],
    should_fail_baseline: bool,
    has_test_patch: bool,
    empty_means: str,
) -> dict:
    """Shared evaluator for fail_to_pass and pass_to_pass criteria.

    Args:
        expected_names: list of expected test names
        eval_passed: set of tests that passed in eval run
        eval_failed: set of tests that failed in eval run
        baseline_passed: set of tests that passed in baseline run
        baseline_failed: set of tests that failed in baseline run
        should_fail_baseline: if True, tests must fail in baseline (fail_to_pass)
        has_test_patch: whether a test patch was applied
        empty_means: "fail" or "skipped" — what to return if expected list is empty
    """
    if not expected_names:
        return {
            "status": empty_means,
            "detail": {"expected": [], "actual_pass": [], "actual_fail": []},
        }

    expected_set = set(expected_names)
    actual_pass = [n for n in expected_names if _test_in(n, eval_passed)]
    actual_fail = [n for n in expected_names if _test_in(n, eval_failed)]

    # Check baseline consistency
    baseline_ok = True
    if has_test_patch:
        for n in expected_names:
            if should_fail_baseline:
                if not _test_in(n, baseline_failed):
                    baseline_ok = False
                    break
            else:
                if not _test_in(n, baseline_passed):
                    baseline_ok = False
                    break

    if should_fail_baseline:
        all_now_pass = all(_test_in(n, eval_passed) for n in expected_names)
        status = "pass" if (all_now_pass and baseline_ok) else "fail"
    else:
        all_still_pass = all(_test_in(n, eval_passed) for n in expected_names)
        status = "pass" if (all_still_pass and baseline_ok) else "fail"

    return {
        "status": status,
        "detail": {
            "expected": expected_names,
            "actual_pass": actual_pass,
            "actual_fail": actual_fail,
        },
    }


def main():
    compile_status = os.environ.get("COMPILE_STATUS", "fail")
    compile_duration = int(os.environ.get("COMPILE_DURATION", "0"))
    patch_status = os.environ.get("PATCH_STATUS", "skipped")
    patch_duration = int(os.environ.get("PATCH_DURATION", "0"))
    test_duration = int(os.environ.get("TEST_DURATION", "0"))
    baseline_duration = int(os.environ.get("BASELINE_DURATION", "0"))
    overall_duration = int(os.environ.get("OVERALL_DURATION", "0"))
    timestamp = os.environ.get("TIMESTAMP", "")
    has_test_patch = os.environ.get("HAS_TEST_PATCH", "false") == "true"

    compile_output = _read("/tmp/_compile_output.txt")
    patch_output = _read("/tmp/_patch_output.txt")

    expected = _load_json("/tmp/_expected.json")
    fail_to_pass_names = expected.get("fail_to_pass", [])
    pass_to_pass_names = expected.get("pass_to_pass", [])

    eval_data = _load_json("/tmp/eval_parser.json")
    baseline_data = _load_json("/tmp/baseline_parser.json")

    eval_passed = {m["name"] for m in eval_data.get("methods", []) if m.get("status") == "passed"}
    eval_failed = {m["name"] for m in eval_data.get("methods", []) if m.get("status") == "failed"}
    baseline_passed = {m["name"] for m in baseline_data.get("methods", []) if m.get("status") == "passed"}
    baseline_failed = {m["name"] for m in baseline_data.get("methods", []) if m.get("status") == "failed"}

    # Expand wildcards: ["*"] means "all discovered tests"
    all_eval_tests = sorted(eval_passed | eval_failed)
    if fail_to_pass_names == ["*"]:
        fail_to_pass_names = all_eval_tests
    if pass_to_pass_names == ["*"]:
        pass_to_pass_names = all_eval_tests

    # --- Compilation criterion ---
    compilation = {
        "status": compile_status,
        "duration_seconds": compile_duration,
        "output": compile_output[:MAX_OUTPUT] if compile_status == "fail" else "",
    }

    # --- Baseline tests criterion ---
    if compile_status != "pass" or not has_test_patch:
        baseline_tests = {"status": "skipped", "duration_seconds": 0}
    else:
        baseline_tests = {
            "status": "pass" if baseline_data.get("summary") else "fail",
            "duration_seconds": baseline_duration,
            "summary": baseline_data.get("summary", {}),
        }

    # --- Patch applied criterion ---
    patch_applied = {
        "status": patch_status,
        "duration_seconds": patch_duration,
        "output": patch_output[:MAX_OUTPUT] if patch_status == "fail" else "",
    }

    # --- Tests criterion ---
    if compile_status != "pass" or patch_status not in ("pass", "skipped"):
        tests = {"status": "skipped", "duration_seconds": 0}
    else:
        tests = {
            "status": "pass" if eval_data.get("summary") else "fail",
            "duration_seconds": test_duration,
            "summary": eval_data.get("summary", {}),
        }

    # --- fail_to_pass criterion ---
    if compile_status != "pass" or patch_status not in ("pass",):
        ftp = {"status": "skipped", "detail": {"expected": fail_to_pass_names, "actual_pass": [], "actual_fail": []}}
    else:
        ftp = _evaluate_criterion(
            fail_to_pass_names, eval_passed, eval_failed,
            baseline_passed, baseline_failed,
            should_fail_baseline=True, has_test_patch=has_test_patch,
            empty_means="fail",
        )

    # --- pass_to_pass criterion ---
    if compile_status != "pass" or patch_status not in ("pass", "skipped"):
        ptp = {"status": "skipped", "detail": {"expected": pass_to_pass_names, "actual_pass": [], "actual_fail": []}}
    else:
        ptp = _evaluate_criterion(
            pass_to_pass_names, eval_passed, eval_failed,
            baseline_passed, baseline_failed,
            should_fail_baseline=False, has_test_patch=has_test_patch,
            empty_means="skipped",
        )

    result = {
        "version": "2.0",
        "timestamp": timestamp,
        "duration_seconds": overall_duration,
        "criteria": {
            "compilation": compilation,
            "baseline_tests": baseline_tests,
            "patch_applied": patch_applied,
            "tests": tests,
            "fail_to_pass": ftp,
            "pass_to_pass": ptp,
        },
        "test_results": {
            "baseline": baseline_data if has_test_patch else {},
            "eval": eval_data,
        },
    }

    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()

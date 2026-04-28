#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    script_path = Path(__file__).resolve()
    repo_root = script_path.parents[2]
    default_cases = repo_root / "seqra-ir-api-py" / "testdata" / "roundtrip"

    parser = argparse.ArgumentParser(
        description="Run the bundled round-trip corpus through the differential harness."
    )
    parser.add_argument("--repo-root", default=str(repo_root))
    parser.add_argument("--cases-dir", default=str(default_cases))
    parser.add_argument("--server-python", default=sys.executable)
    parser.add_argument("--runtime-python", default=sys.executable)
    parser.add_argument("--emit-mode", default="fuzz", choices=["fuzz", "debug-ir"])
    parser.add_argument("--keep-out", action="store_true")
    parser.add_argument("--case", action="append", dest="case_names", help="Run only the named case directory")
    return parser.parse_args()


def load_case(case_dir: Path) -> dict:
    case_json = case_dir / "case.json"
    data = json.loads(case_json.read_text())
    data["name"] = case_dir.name
    data["dir"] = case_dir
    return data


def discover_cases(cases_dir: Path, only_names: set[str] | None) -> list[dict]:
    cases = []
    for case_dir in sorted(path for path in cases_dir.iterdir() if path.is_dir()):
        if only_names is not None and case_dir.name not in only_names:
            continue
        if not (case_dir / "case.json").exists():
            continue
        cases.append(load_case(case_dir))
    return cases


def run_case(repo_root: Path, harness_path: Path, case: dict, args: argparse.Namespace) -> subprocess.CompletedProcess[str]:
    case_dir: Path = case["dir"]
    files = sorted(str(path) for path in case_dir.glob("*.py"))
    entry_file = case_dir / case["entry_file"]
    cmd = [
        args.server_python,
        str(harness_path),
        "--repo-root",
        str(repo_root),
        "--server-python",
        args.server_python,
        "--runtime-python",
        args.runtime_python,
        "--emit-mode",
        args.emit_mode,
        "--entry-file",
        str(entry_file),
        "--function",
        case["function"],
        "--args-json",
        json.dumps(case.get("args_json", [])),
        "--source-root",
        str(case_dir),
    ]
    if args.keep_out:
        cmd.append("--keep-out")
    cmd.extend(files)

    return subprocess.run(cmd, cwd=str(repo_root), text=True, capture_output=True)


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    cases_dir = Path(args.cases_dir).resolve()
    harness_path = repo_root / "seqra-ir-api-py" / "scripts" / "py_diff_harness.py"
    only_names = set(args.case_names) if args.case_names else None

    cases = discover_cases(cases_dir, only_names)
    if not cases:
        print("No cases found.")
        return 1

    failed = []
    for case in cases:
        print(f"=== {case['name']} ===")
        print(case.get("description", ""))
        completed = run_case(repo_root, harness_path, case, args)
        sys.stdout.write(completed.stdout)
        if completed.stderr:
            sys.stderr.write(completed.stderr)
        if completed.returncode != 0:
            failed.append(case["name"])
        print()

    if failed:
        print(f"Summary: {len(cases) - len(failed)}/{len(cases)} cases passed")
        print("Failed cases: " + ", ".join(failed))
        return 1

    print(f"Summary: all {len(cases)} cases passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

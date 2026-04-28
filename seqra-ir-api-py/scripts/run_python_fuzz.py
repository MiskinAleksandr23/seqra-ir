#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import random
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass
class CaseSpec:
    name: str
    description: str
    function: str
    args: list[int]
    files: dict[str, str]


def parse_args() -> argparse.Namespace:
    script_path = Path(__file__).resolve()
    repo_root = script_path.parents[2]

    parser = argparse.ArgumentParser(
        description="Generate small random Python programs in the supported subset and compare original vs generated."
    )
    parser.add_argument("--repo-root", default=str(repo_root))
    parser.add_argument("--server-python", default=sys.executable)
    parser.add_argument("--runtime-python", default=sys.executable)
    parser.add_argument("--emit-mode", default="fuzz", choices=["fuzz", "debug-ir"])
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--keep-all", action="store_true", help="Keep all generated cases on disk")
    parser.add_argument("--stop-on-failure", action="store_true")
    parser.add_argument(
        "--save-failures-dir",
        help="Directory where failing generated cases should be copied"
    )
    return parser.parse_args()


def rand_const(rng: random.Random, low: int = 0, high: int = 9) -> int:
    return rng.randint(low, high)


def rand_positive(rng: random.Random, low: int = 1, high: int = 8) -> int:
    return rng.randint(low, high)


def gen_branching_math(rng: random.Random, case_id: int) -> CaseSpec:
    threshold = rand_positive(rng, 2, 10)
    add_k = rand_positive(rng, 1, 5)
    mul_k = rand_positive(rng, 2, 4)
    args = [rand_const(rng, 0, 15)]
    return CaseSpec(
        name=f"branching_math_{case_id:03d}",
        description="Random integer branch with arithmetic on both sides",
        function="main",
        args=args,
        files={
            "main.py": (
                "def main(x: int) -> int:\n"
                f"    if x < {threshold}:\n"
                f"        return x + {add_k}\n"
                f"    return x * {mul_k} - {add_k}\n"
            )
        },
    )


def gen_while_mod_accumulator(rng: random.Random, case_id: int) -> CaseSpec:
    mod_k = rand_positive(rng, 2, 4)
    add_k = rand_positive(rng, 1, 3)
    limit = rand_positive(rng, 4, 10)
    args = [limit]
    return CaseSpec(
        name=f"while_mod_acc_{case_id:03d}",
        description="While loop with modulo guard and accumulator",
        function="main",
        args=args,
        files={
            "main.py": (
                "def main(n: int) -> int:\n"
                "    i = 0\n"
                "    acc = 0\n"
                "    while i < n:\n"
                f"        if i % {mod_k} == 0:\n"
                f"            acc = acc + i + {add_k}\n"
                "        else:\n"
                "            acc = acc - 1\n"
                "        i = i + 1\n"
                "    return acc\n"
            )
        },
    )


def gen_for_range_step(rng: random.Random, case_id: int) -> CaseSpec:
    step = rng.choice([2, 3])
    upper = rand_positive(rng, 6, 15)
    mul_k = rand_positive(rng, 1, 3)
    args = [upper]
    return CaseSpec(
        name=f"for_step_{case_id:03d}",
        description="For-range with non-unit step and multiply/add body",
        function="main",
        args=args,
        files={
            "main.py": (
                "def main(n: int) -> int:\n"
                "    acc = 0\n"
                f"    for i in range(0, n, {step}):\n"
                f"        acc = acc + i * {mul_k}\n"
                "    return acc\n"
            )
        },
    )


def gen_reverse_break(rng: random.Random, case_id: int) -> CaseSpec:
    break_limit = rand_positive(rng, 6, 20)
    skip_value = rand_positive(rng, 1, 5)
    start = rand_positive(rng, 6, 10)
    args = [start]
    return CaseSpec(
        name=f"reverse_break_{case_id:03d}",
        description="Reverse range with continue and break",
        function="main",
        args=args,
        files={
            "main.py": (
                "def main(n: int) -> int:\n"
                "    acc = 0\n"
                "    for i in range(n, -1, -1):\n"
                f"        if i == {skip_value}:\n"
                "            continue\n"
                "        acc = acc + i\n"
                f"        if acc > {break_limit}:\n"
                "            break\n"
                "    return acc\n"
            )
        },
    )


def gen_recursive_sum(rng: random.Random, case_id: int) -> CaseSpec:
    base = rand_const(rng, 0, 2)
    args = [rand_positive(rng, 3, 7)]
    return CaseSpec(
        name=f"recursive_sum_{case_id:03d}",
        description="Linear recursion with arithmetic on unwind",
        function="main",
        args=args,
        files={
            "main.py": (
                "def helper(n: int) -> int:\n"
                f"    if n <= {base}:\n"
                "        return n\n"
                "    return n + helper(n - 1)\n\n"
                "def main(n: int) -> int:\n"
                "    return helper(n)\n"
            )
        },
    )


def gen_recursive_fib(rng: random.Random, case_id: int) -> CaseSpec:
    args = [rand_positive(rng, 4, 7)]
    return CaseSpec(
        name=f"recursive_fib_{case_id:03d}",
        description="Two-way recursion in fibonacci style",
        function="main",
        args=args,
        files={
            "main.py": (
                "def fib(n: int) -> int:\n"
                "    if n <= 1:\n"
                "        return n\n"
                "    return fib(n - 1) + fib(n - 2)\n\n"
                "def main(n: int) -> int:\n"
                "    return fib(n)\n"
            )
        },
    )


def gen_mutual_recursion(rng: random.Random, case_id: int) -> CaseSpec:
    args = [rand_positive(rng, 3, 9)]
    return CaseSpec(
        name=f"mutual_recursion_{case_id:03d}",
        description="Mutual recursion between two helpers",
        function="main",
        args=args,
        files={
            "main.py": (
                "def is_even(n: int) -> int:\n"
                "    if n == 0:\n"
                "        return 1\n"
                "    return is_odd(n - 1)\n\n"
                "def is_odd(n: int) -> int:\n"
                "    if n == 0:\n"
                "        return 0\n"
                "    return is_even(n - 1)\n\n"
                "def main(n: int) -> int:\n"
                "    return is_odd(n)\n"
            )
        },
    )


def gen_loop_recursive_helper(rng: random.Random, case_id: int) -> CaseSpec:
    args = [rand_positive(rng, 3, 6)]
    add_k = rand_positive(rng, 1, 2)
    return CaseSpec(
        name=f"loop_recursive_helper_{case_id:03d}",
        description="While loop calling a recursive helper",
        function="main",
        args=args,
        files={
            "main.py": (
                "def weight(n: int) -> int:\n"
                "    if n <= 1:\n"
                "        return 1\n"
                f"    return weight(n - 1) + {add_k}\n\n"
                "def main(limit: int) -> int:\n"
                "    i = 0\n"
                "    acc = 0\n"
                "    while i < limit:\n"
                "        acc = acc + weight(i)\n"
                "        i = i + 1\n"
                "    return acc\n"
            )
        },
    )


def gen_dfs_implicit_tree(rng: random.Random, case_id: int) -> CaseSpec:
    limit = rand_positive(rng, 12, 30)
    target = rand_positive(rng, 2, min(limit, 15))
    return CaseSpec(
        name=f"dfs_tree_{case_id:03d}",
        description="Recursive DFS over an implicit binary tree",
        function="main",
        args=[target],
        files={
            "main.py": (
                "def dfs(node: int, target: int, limit: int) -> int:\n"
                "    if node > limit:\n"
                "        return -1\n"
                "    if node == target:\n"
                "        return node\n"
                "    left = dfs(node * 2, target, limit)\n"
                "    if left != -1:\n"
                "        return left\n"
                "    return dfs(node * 2 + 1, target, limit)\n\n"
                "def main(target: int) -> int:\n"
                f"    return dfs(1, target, {limit})\n"
            )
        },
    )


def gen_multi_module_call(rng: random.Random, case_id: int) -> CaseSpec:
    step_k = rand_positive(rng, 1, 4)
    mul_k = rand_positive(rng, 2, 4)
    args = [rand_positive(rng, 3, 7)]
    return CaseSpec(
        name=f"multi_module_{case_id:03d}",
        description="Cross-module helper chain in a loop",
        function="main",
        args=args,
        files={
            "main.py": (
                "from helper import step\n\n"
                "def main(n: int) -> int:\n"
                "    i = 0\n"
                "    acc = 0\n"
                "    while i < n:\n"
                "        acc = step(acc, i)\n"
                "        i = i + 1\n"
                "    return acc\n"
            ),
            "helper.py": (
                "from math_ops import scale\n\n"
                "def step(acc: int, i: int) -> int:\n"
                f"    return acc + scale(i) + {step_k}\n"
            ),
            "math_ops.py": (
                "def scale(x: int) -> int:\n"
                f"    return x * {mul_k}\n"
            ),
        },
    )


GENERATORS = [
    gen_branching_math,
    gen_while_mod_accumulator,
    gen_for_range_step,
    gen_reverse_break,
    gen_recursive_sum,
    gen_recursive_fib,
    gen_mutual_recursion,
    gen_loop_recursive_helper,
    gen_dfs_implicit_tree,
    gen_multi_module_call,
]


def build_case(rng: random.Random, case_id: int) -> CaseSpec:
    generator = rng.choice(GENERATORS)
    return generator(rng, case_id)


def write_case(case_dir: Path, case: CaseSpec, seed: int) -> None:
    case_dir.mkdir(parents=True, exist_ok=True)
    metadata = {
        "description": case.description,
        "entry_file": "main.py",
        "function": case.function,
        "args_json": case.args,
        "seed": seed,
        "name": case.name,
    }
    (case_dir / "case.json").write_text(json.dumps(metadata, indent=2) + "\n")
    for filename, content in case.files.items():
        (case_dir / filename).write_text(content)


def run_case(repo_root: Path, harness_path: Path, case_dir: Path, case: CaseSpec, args: argparse.Namespace) -> subprocess.CompletedProcess[str]:
    files = sorted(str(path) for path in case_dir.glob("*.py"))
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
        str(case_dir / "main.py"),
        "--function",
        case.function,
        "--args-json",
        json.dumps(case.args),
        "--source-root",
        str(case_dir),
    ]
    cmd.extend(files)
    return subprocess.run(cmd, cwd=str(repo_root), text=True, capture_output=True)


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    harness_path = repo_root / "seqra-ir-api-py" / "scripts" / "py_diff_harness.py"
    rng = random.Random(args.seed)

    base_dir_obj: Path | None = None
    temp_dir: tempfile.TemporaryDirectory[str] | None = None
    if args.keep_all:
        base_dir_obj = repo_root / "seqra-ir-api-py" / "testdata" / "generated-fuzz"
        base_dir_obj.mkdir(parents=True, exist_ok=True)
    else:
        temp_dir = tempfile.TemporaryDirectory(prefix="seqra-generated-fuzz-")
        base_dir_obj = Path(temp_dir.name)

    failure_dir = Path(args.save_failures_dir).resolve() if args.save_failures_dir else None
    if failure_dir is not None:
        failure_dir.mkdir(parents=True, exist_ok=True)

    failed: list[str] = []
    unsupported: list[str] = []

    try:
        for case_id in range(args.count):
            case = build_case(rng, case_id)
            case_dir = base_dir_obj / case.name
            write_case(case_dir, case, args.seed)

            print(f"=== {case.name} ===")
            print(case.description)
            completed = run_case(repo_root, harness_path, case_dir, case, args)
            sys.stdout.write(completed.stdout)
            if completed.stderr:
                sys.stderr.write(completed.stderr)
            print()

            if completed.returncode == 0:
                continue

            output = (completed.stdout + "\n" + completed.stderr)
            if "Pipeline result: unsupported" in output:
                unsupported.append(case.name)
            else:
                failed.append(case.name)

            if failure_dir is not None:
                target = failure_dir / case.name
                if target.exists():
                    shutil.rmtree(target)
                shutil.copytree(case_dir, target)

            if args.stop_on_failure:
                break

        passed = args.count - len(failed) - len(unsupported)
        print(f"Summary: {passed}/{args.count} fuzz cases matched")
        if unsupported:
            print("Unsupported cases: " + ", ".join(unsupported))
        if failed:
            print("Mismatched cases: " + ", ".join(failed))
        return 0 if not failed and not unsupported else 1
    finally:
        if temp_dir is not None:
            temp_dir.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())

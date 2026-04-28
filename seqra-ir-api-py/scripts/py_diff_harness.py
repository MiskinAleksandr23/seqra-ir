#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import tempfile
import textwrap
import time
from pathlib import Path


def parse_args() -> argparse.Namespace:
    script_path = Path(__file__).resolve()
    repo_root = script_path.parents[2]

    parser = argparse.ArgumentParser(
        description="Run original Python and generated Python and compare observable behavior."
    )
    parser.add_argument("files", nargs="+", help="Python source files to feed into the pipeline")
    parser.add_argument("--repo-root", default=str(repo_root))
    parser.add_argument("--server-python", default=sys.executable)
    parser.add_argument("--runtime-python", default=sys.executable)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=50061)
    parser.add_argument("--emit-mode", default="fuzz", choices=["fuzz", "debug-ir"])
    parser.add_argument("--entry-file", help="Entry Python file. Defaults to the first positional file.")
    parser.add_argument("--module-name", help="Original module import name. Auto-derived when omitted.")
    parser.add_argument("--function", required=True, help="Function to invoke for comparison")
    parser.add_argument("--args-json", default="[]", help="JSON array of positional arguments")
    parser.add_argument("--source-root", help="Root added to PYTHONPATH for original modules")
    parser.add_argument("--out-dir", help="Directory for generated files")
    parser.add_argument("--json-out", help="Path for saved protobuf response JSON")
    parser.add_argument("--keep-out", action="store_true", help="Keep temporary output directory")
    parser.add_argument(
        "--allow-unsupported",
        action="store_true",
        help="Let the client emit unsupported fuzz cases instead of failing early"
    )
    return parser.parse_args()


def common_source_root(files: list[Path]) -> Path:
    parents = [str(path.parent.resolve()) for path in files]
    return Path(os.path.commonpath(parents))


def module_name_from_file(entry_file: Path, source_root: Path) -> str:
    relative = entry_file.resolve().relative_to(source_root.resolve())
    return ".".join(relative.with_suffix("").parts)


def generated_module_name(module_name: str) -> str:
    parts = module_name.split(".")
    parts[-1] = parts[-1] + "_generated"
    return ".".join(parts)


def wait_for_port(host: str, port: int, timeout_sec: float = 15.0) -> None:
    deadline = time.time() + timeout_sec
    last_error: Exception | None = None
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=0.5):
                return
        except OSError as exc:
            last_error = exc
            time.sleep(0.2)
    raise RuntimeError(f"Timed out waiting for {host}:{port}: {last_error}")


def run_subprocess(cmd: list[str], cwd: Path, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        cwd=str(cwd),
        env=env,
        text=True,
        capture_output=True,
    )


def run_callable(python_bin: str, pythonpath: Path, module_name: str, function_name: str, args_json: str) -> dict:
    runner = textwrap.dedent(
        """
        import importlib
        import json
        import sys

        def normalize(value):
            if value is None or isinstance(value, (bool, int, float, str)):
                return value
            if isinstance(value, (list, tuple)):
                return [normalize(item) for item in value]
            if isinstance(value, dict):
                return {str(key): normalize(val) for key, val in value.items()}
            return {"__repr__": repr(value), "__type__": type(value).__name__}

        payload = json.loads(sys.argv[1])
        module_name = sys.argv[2]
        function_name = sys.argv[3]

        try:
            module = importlib.import_module(module_name)
            func = getattr(module, function_name)
            result = func(*payload)
            print(json.dumps({
                "status": "ok",
                "result": normalize(result),
                "result_type": type(result).__name__,
            }, sort_keys=True))
        except Exception as exc:
            print(json.dumps({
                "status": "exception",
                "type": type(exc).__name__,
                "message": str(exc),
            }, sort_keys=True))
        """
    ).strip()

    env = os.environ.copy()
    existing = env.get("PYTHONPATH")
    env["PYTHONPATH"] = str(pythonpath) if not existing else f"{pythonpath}{os.pathsep}{existing}"
    completed = run_subprocess(
        [python_bin, "-c", runner, args_json, module_name, function_name],
        cwd=pythonpath,
        env=env,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"Python runner failed for {module_name}.{function_name}:\nSTDOUT:\n{completed.stdout}\nSTDERR:\n{completed.stderr}"
        )
    return json.loads(completed.stdout.strip())


def main() -> int:
    args = parse_args()

    repo_root = Path(args.repo_root).resolve()
    files = [Path(file).resolve() for file in args.files]
    entry_file = Path(args.entry_file).resolve() if args.entry_file else files[0]
    source_root = Path(args.source_root).resolve() if args.source_root else common_source_root(files)
    module_name = args.module_name or module_name_from_file(entry_file, source_root)
    generated_name = generated_module_name(module_name)

    out_dir_obj = Path(args.out_dir).resolve() if args.out_dir else None
    temp_dir: tempfile.TemporaryDirectory[str] | None = None
    if out_dir_obj is None:
        temp_dir = tempfile.TemporaryDirectory(prefix="seqra-py-diff-")
        out_dir = Path(temp_dir.name)
    else:
        out_dir = out_dir_obj
        out_dir.mkdir(parents=True, exist_ok=True)

    json_out = Path(args.json_out).resolve() if args.json_out else out_dir / "output.json"

    server_cwd = repo_root / "seqra-ir-api-py" / "src" / "main" / "kotlin" / "org" / "seqra" / "ir" / "api" / "py" / "grpc"
    server_cmd = [
        args.server_python,
        "python_server.py",
        "--host",
        args.host,
        "--port",
        str(args.port),
    ]

    server = subprocess.Popen(
        server_cmd,
        cwd=str(server_cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )

    try:
        wait_for_port(args.host, args.port)

        gradle_args = [
            "./gradlew",
            ":seqra-ir-api-py:runPyGrpcClient",
            "--args=" + " ".join([
                "--host", args.host,
                "--port", str(args.port),
                "--emit-mode", args.emit_mode,
                *(["--allow-unsupported"] if args.allow_unsupported else []),
                "--out-dir", str(out_dir),
                "--json-out", str(json_out),
                *[str(path) for path in files],
            ]),
        ]
        client = run_subprocess(gradle_args, repo_root)
        if client.returncode != 0:
            combined = (client.stdout + "\n" + client.stderr).strip()
            if "Unsupported in fuzz mode" in combined:
                print("Pipeline result: unsupported")
                print(combined)
                return 2
            raise RuntimeError(f"Kotlin client failed:\nSTDOUT:\n{client.stdout}\nSTDERR:\n{client.stderr}")

        original_result = run_callable(
            python_bin=args.runtime_python,
            pythonpath=source_root,
            module_name=module_name,
            function_name=args.function,
            args_json=args.args_json,
        )
        generated_result = run_callable(
            python_bin=args.runtime_python,
            pythonpath=out_dir,
            module_name=generated_name,
            function_name=args.function,
            args_json=args.args_json,
        )

        print(f"Original module:  {module_name}")
        print(f"Generated module: {generated_name}")
        print(f"Output dir:       {out_dir}")
        print()
        print("Original result:")
        print(json.dumps(original_result, indent=2, sort_keys=True))
        print()
        print("Generated result:")
        print(json.dumps(generated_result, indent=2, sort_keys=True))
        print()

        if original_result == generated_result:
            print("Comparison: MATCH")
            return 0

        print("Comparison: MISMATCH")
        return 1

    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=5)

        if temp_dir is not None and not args.keep_out:
            temp_dir.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())

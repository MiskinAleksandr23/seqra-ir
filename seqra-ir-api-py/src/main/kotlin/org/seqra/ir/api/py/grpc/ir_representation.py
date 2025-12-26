import json
from pathlib import Path
from typing import List

from google.protobuf.json_format import MessageToDict
from mypyc.analysis import dataflow
from mypyc.ir.class_ir import ClassIR

from mypy import build
from mypy.errors import CompileError
from mypy.nodes import Expression, MypyFile
from mypy.options import Options
from mypy.test.config import test_temp_dir
from mypy.types import Type
from mypyc.analysis.ircheck import assert_func_ir_valid
from mypyc.errors import Errors
from mypyc.ir.module_ir import ModuleIR
from mypyc.irbuild.main import build_ir
from mypyc.irbuild.mapper import Mapper
from mypyc.options import CompilerOptions
from mypyc.common import TOP_LEVEL_NAME
from mypyc.transform import exceptions
from mypyc.ir.pprint import format_func, generate_names_for_ir
import io
from contextlib import redirect_stdout


def print_ir_operations(module: ModuleIR):
    for func in module.functions:
        if func.decl.name.startswith('__'):
            continue

        print(f"\nФункция: {func.decl.name}")
        print("-" * 50)

        formatted_lines = format_func(func)
        for line in formatted_lines:
            print(line)


def print_ir_operations_to_string(module) -> str:
    output = io.StringIO()
    with redirect_stdout(output):
        print_ir_operations(module)

    return output.getvalue()


def print_ir_func(modules: List[ModuleIR]):
    for module in modules:
        output_str = print_ir_operations_to_string(module)
        with open('ir_operations_output.txt', 'a', encoding='utf-8') as f:
            f.write(output_str)


def get_modules(files: List[str] = None, dir: str = None) -> List[ModuleIR]:
    source_files = []
    if files:
        source_files = files
    elif dir:
        directory = Path(dir).resolve()

        python_files = list(directory.rglob("*.py"))

        exclude_patterns = ['__pycache__', '.git', 'venv', 'env', '.idea', '.vscode']
        filtered_files = []

        for file in python_files:
            if not any(pattern in str(file) for pattern in exclude_patterns):
                filtered_files.append(str(file.resolve()))

        source_files = filtered_files

    sources = []
    for i, source_file in enumerate(source_files):
        with open(source_file, 'r') as f:
            program_text = f.read()
        module_name = "__main__" if i == 0 else f"__main__{i}"
        source = build.BuildSource(module_name, module_name, program_text)
        sources.append(source)

    compiler_options = CompilerOptions(capi_version=(3, 9))

    options = Options()
    options.show_traceback = True
    options.hide_error_codes = True
    options.use_builtins_fixtures = True
    options.strict_optional = True
    options.python_version = compiler_options.python_version or (3, 9)
    options.export_types = True
    options.preserve_asts = True

    per_module_options = {}
    for i in range(len(source_files)):
        module_name = "__main__" if i == 0 else f"__main__{i}"
        per_module_options[module_name] = {"mypyc": True}
    options.per_module_options = per_module_options

    result = build.build(sources=sources, options=options, alt_lib_path=test_temp_dir)
    if result.errors:
        raise CompileError(result.errors)

    errors = Errors(options)
    mapper_dict = {name: None for name in per_module_options}
    mapper = Mapper(mapper_dict)

    build_files = [result.files[name] for name in per_module_options]
    modules = build_ir(build_files, result.graph, result.types, mapper, compiler_options, errors)
    modules_list = list(modules.values())

    return modules_list


def get_classes(files: List[str] = None, dir: str = None) -> List[ClassIR]:
    modules = get_modules(files, dir)
    classes = []
    for module in modules:
        classes.append(module.classes)

    return classes


def get_cfg(files: List[str] = None, dir: str = None):
    modules = get_modules(files, dir)

    funcs_cfg = []
    for module in modules:
        for fn in module.functions:
            if fn.name == TOP_LEVEL_NAME:
                continue

            exceptions.insert_exception_handling(fn)

            if fn.decl.name.startswith('__'):
                continue

            output = io.StringIO()
            with redirect_stdout(output):
                print(f"\nФункция: {fn.decl.name}")
                print("-" * 50)

                formatted_lines = format_func(fn)
                for line in formatted_lines:
                    print(line)

            output_str = output.getvalue()

            cfg = dataflow.get_cfg(fn.blocks)

            actual = []
            edges = []
            for block in fn.blocks:
                if block in cfg.succ:
                    for succ in cfg.succ[block]:
                        edges.append((block.label, succ.label))

            edges_by_source = {}
            for src, dst in edges:
                if src not in edges_by_source:
                    edges_by_source[src] = []
                edges_by_source[src].append(dst)

            actual.append("\n")
            actual.append("------------------------CFG-----------------------")
            for src in sorted(edges_by_source.keys()):
                dsts = sorted(edges_by_source[src])
                dsts_str = ', '.join(f'L{dst}' for dst in dsts)
                actual.append(f"L{src} -> [{dsts_str}]")
            actual.append("\n")

            with open('ir_operations_outputt.txt', 'a', encoding='utf-8') as f:
                f.write(output_str)
                f.writelines("\n".join(actual))

            funcs_cfg.append((fn, cfg))

    with open('cfgs_debug.json', 'w', encoding='utf-8') as f:
        json.dump(funcs_cfg, f, indent=2, ensure_ascii=False, default=str)

    print(len(funcs_cfg))
    return funcs_cfg


if __name__ == "__main__":
    # get_modules(["C:/MKN/project2/solution.py", "C:/MKN/project2/anysystem.py"])
    # get_classes(["C:/MKN/project2/solution.py", "C:/MKN/project2/anysystem.py"])
    get_cfg(["C:/MKN/project2/test.py"])

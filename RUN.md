# Run Guide

Инструкция для запуска Python gRPC pipeline на другом компьютере.

## Что нужно

- `git`
- `Python 3.12` или близкий
- `JDK 22`
- интернет для `pip` и Gradle

## 1. Клонировать репозиторий

```bash
git clone git@github.com:MiskinAleksandr23/seqra-ir.git
cd seqra-ir
```

## 2. Поднять Python-окружение

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install mypy grpcio protobuf
```

## 3. Проверить Kotlin/Gradle часть

```bash
./gradlew :seqra-ir-api-py:test
```

## 4. Запустить Python gRPC server

Во втором терминале:

```bash
cd seqra-ir-api-py/src/main/kotlin/org/seqra/ir/api/py/grpc
/absolute/path/to/seqra-ir/.venv/bin/python python_server.py --host 127.0.0.1 --port 50051
```

Сервер должен написать:

```text
Starting gRPC server on 127.0.0.1:50051...
IR gRPC server started on 127.0.0.1:50051
```

## 5. Подать Python-файлы в pipeline

Из корня репозитория:

```bash
./gradlew :seqra-ir-api-py:runPyGrpcClient --args="--host 127.0.0.1 --port 50051 --out-dir /tmp/seqra_generated --json-out /tmp/seqra_generated/output.json /abs/path/to/file1.py /abs/path/to/file2.py"
```

Если файл один:

```bash
./gradlew :seqra-ir-api-py:runPyGrpcClient --args="--host 127.0.0.1 --port 50051 --out-dir /tmp/seqra_generated --json-out /tmp/seqra_generated/output.json /abs/path/to/test.py"
```

## 6. Что будет на выходе

В директории `--out-dir` появятся:

- `output.json`
- `*_generated.py`

Пример:

```bash
ls /tmp/seqra_generated
```

## Быстрый smoke test

Создать пример:

```bash
mkdir -p /tmp/seqra_pkg
cat > /tmp/seqra_pkg/helper.py <<'EOF'
def inc(x: int) -> int:
    return x + 1
EOF

cat > /tmp/seqra_pkg/main.py <<'EOF'
from helper import inc

def main(y: int) -> int:
    return inc(y)
EOF
```

Потом запустить:

```bash
./gradlew :seqra-ir-api-py:runPyGrpcClient --args="--host 127.0.0.1 --port 50051 --out-dir /tmp/seqra_generated --json-out /tmp/seqra_generated/output.json /tmp/seqra_pkg/main.py /tmp/seqra_pkg/helper.py"
```

Ожидаемый результат:

```text
Success: true
Module count: 2
Class count: 0
CFG count: 2
Generated Python file: /tmp/seqra_generated/main_generated.py
Generated Python file: /tmp/seqra_generated/helper_generated.py
```

## Важные замечания

- Сервер лучше запускать именно из директории:

```text
seqra-ir-api-py/src/main/kotlin/org/seqra/ir/api/py/grpc
```

Потому что рядом лежат `ir_pb2.py` и `ir_pb2_grpc.py`.

- Пути `/mnt/c/...` больше не нужны, клиент принимает обычные аргументы.

- Generated Python сейчас IR-подобный, а не восстановленный исходник один в один.

## Полезные файлы

- `py-pipeline-notes.md` — подробные заметки по pipeline
- `seqra-ir-api-py/src/main/kotlin/org/seqra/ir/api/py/grpc/python_server.py` — Python gRPC server
- `seqra-ir-api-py/src/main/kotlin/org/seqra/ir/api/py/grpc/Main.kt` — Kotlin client

# Python Pipeline Notes

## Что было сделано

Пайплайн в `seqra-ir-api-py` доведен до рабочего состояния для сценария:

`mypy IR -> gRPC/proto -> Kotlin PIR -> generated Python`

Исправления:

- В `ir_representation.py`:
  - убрана зависимость от `mypy` test fixtures;
  - починен многомодульный разбор;
  - добавлен отдельный temp cache;
  - отключен `sqlite_cache`;
  - добавлено нормальное определение имен модулей.

- В `python_server.py`:
  - убран жесткий bind на `[::]:50051`;
  - добавлены `--host/--port`;
  - исправлена сериализация CFG-блоков и переходов;
  - блоки больше не склеиваются из-за одинаковых `label = -1`;
  - исправлена сериализация `Return.value`, если возвращается результат `Op`.

- В `Mapper.kt`:
  - выровнено именование synthetic results для `ValueCase.OP`;
  - `return` теперь ссылается на тот же временный регистр, что и `assign`.

- В `Main.kt`:
  - убран хардкод `/mnt/c/...`;
  - добавлены CLI-аргументы;
  - добавлен вывод generated `.py` и JSON-ответа в указанные пути.

- В `PIRMethod.kt`, `PIRValue.kt`, `PIRInstVisitor.kt`:
  - закрыты явные `TODO`;
  - добавлена минимальная реализация `flowGraph()`;
  - убраны структурные дыры в PIR API.

- В `seqra-ir-api-py/build.gradle.kts`:
  - добавлены тестовые зависимости;
  - добавлен task `runPyGrpcClient`.

- Добавлен smoke-test:
  - `seqra-ir-api-py/src/test/kotlin/org/seqra/ir/api/py/grpc/MapperEmitterTest.kt`

## Что проверено

- Проходит:

```bash
./gradlew :seqra-ir-api-py:test
```

- Многомодульный пример `main.py + helper.py` теперь строится как:

```text
['main', 'helper']
```

- Реальный end-to-end через живой `python_server.py` и Kotlin-клиент прошел:

```bash
./gradlew :seqra-ir-api-py:runPyGrpcClient --args="--host 127.0.0.1 --port 50051 --out-dir /tmp/seqra_generated --json-out /tmp/seqra_generated/output.json /tmp/seqra_pkg/main.py /tmp/seqra_pkg/helper.py"
```

- На выходе были получены:
  - `/tmp/seqra_generated/main_generated.py`
  - `/tmp/seqra_generated/helper_generated.py`
  - `/tmp/seqra_generated/output.json`

## Остаточный риск

Сейчас генератор обратно в Python восстанавливает не исходный пользовательский код, а IR-подобный Python:

- с `__pc`;
- с временными регистрами;
- с runtime stubs вроде `__pir_call_c`.

Для этапа `proto -> PIR -> generated py` это уже рабочий round-trip по модели и CFG. Но перед фаззингом еще нужно улучшать эмиттер и покрытие op-видов.

---

## Пример пайплайна

Ниже живой пример, который был прогнан локально.

### Вход

Файл `/tmp/seqra_pkg/helper.py`:

```python
def inc(x: int) -> int:
    return x + 1
```

Файл `/tmp/seqra_pkg/main.py`:

```python
from helper import inc

def main(y: int) -> int:
    return inc(y)
```

### Как запускалось

Сервер:

```bash
/tmp/seqra-ir-pyenv/bin/python \
  /Users/near/seqra-ir/seqra-ir-api-py/src/main/kotlin/org/seqra/ir/api/py/grpc/python_server.py \
  --host 127.0.0.1 --port 50051
```

Клиент:

```bash
./gradlew :seqra-ir-api-py:runPyGrpcClient --args="--host 127.0.0.1 --port 50051 --out-dir /tmp/seqra_generated --json-out /tmp/seqra_generated/output.json /tmp/seqra_pkg/main.py /tmp/seqra_pkg/helper.py"
```

### Что вернул Python gRPC server

Клиент напечатал:

```text
Success: true
Module count: 2
Class count: 0
CFG count: 2
Generated Python file: /tmp/seqra_generated/main_generated.py
Generated Python file: /tmp/seqra_generated/helper_generated.py
```

### Что лежит в protobuf/json

Сохранилось в `/tmp/seqra_generated/output.json`.

Для `main` структура выглядит по сути так:

```json
{
  "fullname": "main",
  "functions": [
    {
      "decl": { "name": "main", "module_name": "main" },
      "blocks": [
        {
          "label": 0,
          "ops": [
            { "name": "Call", "register_op": { "call": { "fn": { "name": "inc", "module_name": "helper" }}}},
            { "name": "Return", "control_op": { "return": { "value": { "op": { "name": "Call" }}}}}
          ]
        }
      ]
    }
  ]
}
```

Для CFG после фикса labels уже нормальные:

```text
0
1
2
0
```

### Как это выглядит на Kotlin PIR-стороне

Примерно так:

```kotlin
PIRFunc(
  decl = PIRFuncDecl(name = "main", moduleName = "main", ...),
  instructions = listOf(
    PIRAssignInst(
      lhv = PIRRegister("__Call_call", ...),
      rhv = PIRDirectCallExpr(funcDecl = inc, args = [y], ...)
    ),
    PIRReturnInst(
      returnValue = PIRRegister("__Call_call", ...)
    )
  )
)
```

То есть схема сейчас такая:

1. Python исходник
2. `mypyc` IR
3. protobuf `CompleteResponse`
4. Kotlin `PIRFunc/PIRAssignInst/PIRReturnInst/...`
5. обратная генерация `.py`

### Что генерируется обратно в Python

Для `main`:

```python
def main(y):
    __pc = 0
    while True:
        if __pc == 0:
            __Call_call = inc(y)
            return __Call_call
        else:
            raise RuntimeError(f'bad pc: {__pc}')
```

Для `inc`:

```python
def inc(x):
    __pc = 0
    while True:
        if __pc == 0:
            __CallC_call_c = __pir_call_c("CPyTagged_Add", x, 2)
            return __CallC_call_c
        else:
            raise RuntimeError(f'bad pc: {__pc}')
```

### Важное замечание

Сейчас это не восстановление исходника "как был", а восстановление через IR-модель. То есть на выходе получается IR-подобный Python с:

- `__pc`;
- временными регистрами;
- `__pir_*` helpers.

Для этапа `proto -> PIR -> generated py` пайплайн уже рабочий. Но для качественного fuzz-сравнения еще нужно отдельно улучшать эмиттер.

---

## Как улучшить эмиттер для фаззинга

Для фаззинга эмиттер надо улучшать не в сторону "красоты", а в сторону исполняемой эквивалентности.

Сейчас основные проблемы такие.

### 1. Runtime stubs ломают семантику

Сейчас:

- `__pir_call_c()` почти всегда возвращает `None`;
- `__pir_primitive()` кидает `NotImplementedError`;
- `PIRLoadErrorValueExpr` эмитится как `None`;
- `cast/box/unbox` почти no-op.

Для fuzzing это плохой baseline: generated программа часто будет вести себя иначе даже на корректном IR.

### 2. Модульная инициализация не исполняется как у обычного Python-модуля

Сейчас эмиттер печатает `__top_level__`, но не вызывает его автоматически.

Также `module.imports` почти не участвуют в генерации.

Из-за этого:

- глобалы могут быть не инициализированы;
- импортные эффекты могут расходиться с исходником.

### 3. Для фаззинга нужен поддерживаемый IR-профиль, а не "весь mypyc IR"

Если сравнивать generated output с любым Python-кодом, будет много ложных расхождений.

Нужен ограниченный поднабор IR, который эмиттер умеет воспроизводить надежно.

### 4. Нет отдельного fuzz-friendly режима

Сейчас эмиттер одновременно:

- пытается быть читаемым;
- пытается быть исполняемым;
- и местами молча упрощает поведение.

Для фаззинга это неудобно.

---

## Что стоит сделать по порядку

### 1. Ввести отдельный режим эмиттера для фаззинга

Добавить что-то вроде:

```kotlin
data class EmitOptions(
    val mode: Mode = Mode.FUZZ,
    val failOnUnsupported: Boolean = true
)

enum class Mode {
    DEBUG_IR,
    FUZZ
}
```

В `FUZZ`-режиме:

- не замалчивать unsupported behavior;
- либо точно эмулировать;
- либо явно помечать testcase как unsupported.

Для фаззинга лучше не сгенерировать кейс, чем сгенерировать ложный.

### 2. Сделать table-driven runtime для `CallC` и `PrimitiveOp`

Сейчас самый большой источник расхождений в:

- `emitCallC()`;
- `__pir_primitive()`.

Нужно завести таблицы:

- `functionName -> Python semantics`;
- `primitive.name -> Python semantics`.

Минимально полезный набор для первой волны:

- `CPyTagged_Add`
- `CPyTagged_Subtract`
- `CPyTagged_Multiply`
- `PyNumber_Add`
- `PyNumber_Subtract`
- `PyNumber_Multiply`
- `PyNumber_TrueDivide`
- простые comparisons
- tuple/list/dict construction
- `builtins.*`

То есть не пытаться покрыть сразу все, а сначала покрыть IR, который чаще всего будет приходить из фаззера.

### 3. Отделить error sentinel от `None`

Сейчас `LoadErrorValue` и error-check фактически используют `None`.

Это опасно, потому что в Python `None` может быть корректным значением.

Лучше так:

```python
__PIR_ERROR = object()

def __pir_is_error(x):
    return x is __PIR_ERROR
```

И `PIRLoadErrorValueExpr` эмитить как `__PIR_ERROR`.

Для fuzzing это важно.

### 4. Нормально исполнять top-level init

Сейчас `__top_level__` только объявляется.

Нужно либо:

- инлайнить top-level IR в модульный scope;
- либо в конце generated файла вызывать `__top_level__()` под guard.

Например:

```python
__pir_module_initialized = False
if not __pir_module_initialized:
    __pir_module_initialized = True
    __top_level__()
```

### 5. Использовать `module.imports` и `LoadStatic.moduleName`

Сейчас это почти игнорируется.

Для fuzzing нужно:

- добавлять реальные `import ...` в начало файла, где это безопасно;
- различать `LoadStaticExpr` по `moduleName/namespace`, а не только по `identifier`.

### 6. Явно ограничить поддерживаемый IR-профиль

Лучше ввести `supportsForFuzz()` и пропускать неподдержанные кейсы.

На первом этапе поддерживать только:

- top-level functions;
- `Assign`, `Call`, `CallC` из белого списка;
- `Return`, `Goto`, `Branch`;
- `IntOp`, `ComparisonOp`, `FloatOp`;
- `LoadLiteral`, `TupleSet`, `TupleGet`;
- простые imports.

А вот:

- classes,
- properties,
- glue methods,
- trait vtables,
- сложные refcount-specific ops

пока не включать в fuzzing corpus.

### 7. Делать oracle по поведению, а не по тексту

Для фаззинга сравнивать нужно не "текст generated.py похож на original.py", а:

- return value;
- type исключения;
- при необходимости message;
- stdout/stderr;
- побочные эффекты, если они входят в тест.

То есть harness должен:

1. запускать оригинал;
2. запускать generated;
3. подавать одинаковые аргументы;
4. сравнивать нормализованный результат.

### 8. Добавить строгую диагностику unsupported ops

Вместо общих `NotImplementedError` лучше писать:

- функция;
- блок;
- op name;
- line;
- expr kind.

Например:

```python
raise NotImplementedError("unsupported primitive op: CPyFoo in helper.inc line 12")
```

Так будет понятнее, какие конструкции чаще всего ломают coverage.

### 9. Минимизировать nondeterminism

Для фаззинга generated output должен быть стабильным.

Нужно фиксировать:

- порядок обхода функций;
- порядок блоков;
- имена временных регистров;
- порядок helper-функций;
- порядок imports.

### 10. Практический roadmap

Разумный порядок такой:

1. Сделать `FUZZ` mode.
2. Ввести `__PIR_ERROR`.
3. Исправить top-level initialization.
4. Добавить белый список `CallC` и `PrimitiveOp` с реальной семантикой.
5. Добавить `supportsForFuzz()` и skip unsupported IR.
6. Написать отдельный fuzz harness: original vs generated.
7. После этого расширять покрытие IR.

---

## Краткий вывод

Для фаззинга эмиттер должен быть не "красивым декомпилятором", а детерминированным исполнителем ограниченного IR-подмножества.

То есть цель не восстановить исходный Python "как был", а получить:

- стабильный generated код;
- исполняемость;
- предсказуемую семантику;
- понятные причины skip/fail на unsupported кейсах.

# Conditional Pipeline Example

Разбор одного полного round-trip кейса:

- исходный Python
- mypyc IR
- proto/json
- generated Python
- итог выполнения

## Исходный Python

Файлы:

- `seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/main.py`
- `seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/helper.py`
- `seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/math_ops.py`

`main.py`

```python
from helper import transform
from math_ops import shift


def main(y: int) -> int:
    value = transform(y)
    if value >= 20:
        return value - 5
    if value != 13:
        return shift(value)
    return value + 2
```

`helper.py`

```python
from math_ops import scale, shift


def transform(x: int) -> int:
    if x > 10:
        return scale(x) - 4
    if x == 10:
        return shift(x)
    return scale(x) + 1
```

`math_ops.py`

```python
def scale(x: int) -> int:
    return x * 2


def shift(x: int) -> int:
    return x + 3
```

## mypyc IR

Для этого примера `mypyc` строит такой IR.

`main`

```text
def main(y):
    y, r0, value :: int
    r1 :: bit
    r2 :: int
    r3 :: bit
    r4, r5 :: int
L0:
    r0 = transform(y)
    value = r0
    r1 = int_ge value, 40
    if r1 goto L1 else goto L2 :: bool
L1:
    r2 = CPyTagged_Subtract(value, 10)
    return r2
L2:
    r3 = int_ne value, 26
    if r3 goto L3 else goto L4 :: bool
L3:
    r4 = shift(value)
    return r4
L4:
    r5 = CPyTagged_Add(value, 4)
    return r5
```

`helper.transform`

```text
def transform(x):
    x :: int
    r0 :: bit
    r1, r2 :: int
    r3 :: bit
    r4, r5, r6 :: int
L0:
    r0 = int_gt x, 20
    if r0 goto L1 else goto L2 :: bool
L1:
    r1 = scale(x)
    r2 = CPyTagged_Subtract(r1, 8)
    return r2
L2:
    r3 = int_eq x, 20
    if r3 goto L3 else goto L4 :: bool
L3:
    r4 = shift(x)
    return r4
L4:
    r5 = scale(x)
    r6 = CPyTagged_Add(r5, 2)
    return r6
```

`math_ops`

```text
def scale(x):
    x, r0 :: int
L0:
    r0 = CPyTagged_Multiply(x, 4)
    return r0

def shift(x):
    x, r0 :: int
L0:
    r0 = CPyTagged_Add(x, 6)
    return r0
```

Важно:

- в IR многие целые константы уже tagged;
- поэтому `40` в `int_ge value, 40` потом обратно интерпретируется как `20`;
- `10` становится `5`, `6` становится `3`, `4` становится `2`.

## Proto / JSON

После `python_server.py` модули, функции, блоки и операции сериализуются в `CompleteResponse`.

Кусок `output.json` для модуля `main` выглядит так:

```json
{
  "fullname": "main",
  "imports": [
    "builtins",
    "helper",
    "math_ops"
  ],
  "functions": [
    {
      "decl": {
        "name": "main",
        "module_name": "main",
        "line": 5
      },
      "blocks": [
        {
          "label": 0,
          "ops": [
            {
              "name": "Call"
            }
          ]
        }
      ]
    }
  ]
}
```

Полный JSON сохраняется, если запускать pipeline с `--json-out`.

## Generated Python

На Kotlin-стороне `ProtoToPirMapper` строит PIR, а `PIRToPythonEmitter` печатает новый Python.

Ключевой кусок `main_generated.py`:

```python
import builtins
helper = __pir_import_module("helper_generated", "helper")
math_ops = __pir_import_module("math_ops_generated", "math_ops")
if helper is not None and hasattr(helper, "transform"):
    transform = helper.transform
if math_ops is not None and hasattr(math_ops, "shift"):
    shift = math_ops.shift

def main(y):
    __pc = 0
    while True:
        if __pc == 0:
            __Call_call = helper.transform(y)
            value = __Call_call
            __PrimitiveOp_primitive_op = (value >= 20)
            if __PrimitiveOp_primitive_op:
                __pc = 4
            else:
                __pc = 6
            continue
        elif __pc == 4:
            __CallC_call_c = (value - 5)
            return __CallC_call_c
        elif __pc == 6:
            __PrimitiveOp_primitive_op = (value != 13)
            if __PrimitiveOp_primitive_op:
                __pc = 8
            else:
                __pc = 10
            continue
        elif __pc == 8:
            __Call_call = math_ops.shift(value)
            return __Call_call
        elif __pc == 10:
            __CallC_call_c = (value + 2)
            return __CallC_call_c
```

Ключевой кусок `helper_generated.py`:

```python
def transform(x):
    __pc = 0
    while True:
        if __pc == 0:
            __PrimitiveOp_primitive_op = (x > 10)
            if __PrimitiveOp_primitive_op:
                __pc = 2
            else:
                __pc = 5
            continue
        elif __pc == 2:
            __Call_call = math_ops.scale(x)
            __CallC_call_c = (__Call_call - 4)
            return __CallC_call_c
        elif __pc == 5:
            __PrimitiveOp_primitive_op = (x == 10)
            if __PrimitiveOp_primitive_op:
                __pc = 7
            else:
                __pc = 9
            continue
        elif __pc == 7:
            __Call_call = math_ops.shift(x)
            return __Call_call
        elif __pc == 9:
            __Call_call = math_ops.scale(x)
            __CallC_call_c = (__Call_call + 1)
            return __CallC_call_c
```

Ключевой кусок `math_ops_generated.py`:

```python
def scale(x):
    __pc = 0
    while True:
        if __pc == 0:
            __CallC_call_c = (x * 2)
            return __CallC_call_c

def shift(x):
    __pc = 0
    while True:
        if __pc == 0:
            __CallC_call_c = (x + 3)
            return __CallC_call_c
```

## Итог выполнения

Для входа `main(9)`:

```json
{
  "result": 22,
  "result_type": "int",
  "status": "ok"
}
```

И у исходного, и у generated Python результат одинаковый, то есть:

```text
Comparison: MATCH
```

## Как запустить

```bash
cd /Users/near/seqra-ir
/tmp/seqra-ir-pyenv/bin/python seqra-ir-api-py/scripts/py_diff_harness.py \
  --repo-root /Users/near/seqra-ir \
  --server-python /tmp/seqra-ir-pyenv/bin/python \
  --runtime-python python3 \
  --entry-file /Users/near/seqra-ir/seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/main.py \
  --function main \
  --args-json '[9]' \
  --source-root /Users/near/seqra-ir/seqra-ir-api-py/testdata/roundtrip/conditional_pipeline \
  --out-dir /tmp/seqra_conditional_demo \
  --keep-out \
  /Users/near/seqra-ir/seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/main.py \
  /Users/near/seqra-ir/seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/helper.py \
  /Users/near/seqra-ir/seqra-ir-api-py/testdata/roundtrip/conditional_pipeline/math_ops.py
```

После этого в `/tmp/seqra_conditional_demo` будут лежать:

- `output.json`
- `main_generated.py`
- `helper_generated.py`
- `math_ops_generated.py`

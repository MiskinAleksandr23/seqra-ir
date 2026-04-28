# Python Fuzz Unsupported List

Текущий статус относится к `fuzz`-режиму эмиттера в `seqra-ir-api-py`.

## Что уже поддержано

Поддержанные `CallC`:

- `CPyImport_GetNativeAttrs`
- `CPyImport_ImportNative`
- `CPyTagged_Add`
- `CPyTagged_Multiply`
- `CPyTagged_Subtract`
- `PyImport_Import`
- `PyNumber_Add`
- `PyNumber_Multiply`
- `PyNumber_Subtract`
- `PyNumber_TrueDivide`

Поддержанные `PrimitiveOp`:

- `int_eq`
- `int_ge`
- `int_gt`
- `int_le`
- `int_lt`
- `int_ne`

Поддержанные round-trip паттерны по corpus:

- межмодульные вызовы
- `if/elif/else`
- nested module calls
- `while`
- `continue`
- `break`
- `for range`
- nested `while`
- `for range` с `break/continue`
- `while` с несколькими обновляемыми переменными
- `while` с вызовом в другом модуле

## Что сейчас считается unsupported

На уровне `PIRFuzzSupportChecker` сейчас блокируются:

- любой `PrimitiveOp`, которого нет в whitelist выше
- любой `CallC`, которого нет в whitelist выше
- `PIRLoadMemExpr`
- `PIRSetMemExpr`
- `PIRGetElementPtrExpr`
- `PIRSetElementExpr`

Это значит, что в `fuzz`-режиме emitter по умолчанию упадет заранее, если увидит такие конструкции.

## Важные замечания

- `Phi` в emitter уже поддержан, и на него есть synthetic tests.
- Но в текущем `Python -> proto` пути `Phi` пока не был замечен как отдельная сериализуемая операция в реальных кейсах.
- Поэтому `Phi` сейчас закрыт как часть готовности эмиттера, а не как массово встречающаяся real-world операция в corpus.

## Что еще не добито по предметной области

Пока сознательно не покрывались:

- tuples / destructuring
- list / dict / set patterns
- exceptions
- classes / methods / attrs
- comprehensions
- richer import/package cases
- low-level memory/pointer-specific IR

## Как обновлять этот список

Практический процесс такой:

1. Добавить новый corpus-кейс.
2. Прогнать `run_roundtrip_examples.py` или `py_diff_harness.py`.
3. Если `fuzz`-режим падает, посмотреть точный unsupported op.
4. Либо добавить поддержку в emitter, либо оставить пункт в этом списке.

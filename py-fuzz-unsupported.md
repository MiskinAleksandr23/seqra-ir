# Python Fuzz Unsupported List

Текущий статус относится к `fuzz`-режиму эмиттера в `seqra-ir-api-py`.

## Что уже поддержано

Поддержанные `CallC`:

- `CPyImport_GetNativeAttrs`
- `CPyImport_ImportNative`
- `CPyTagged_Add`
- `CPyTagged_Multiply`
- `CPyTagged_Remainder`
- `CPyTagged_Rshift`
- `CPyTagged_Subtract`
- `CPyTagged_TrueDivide`
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
- `for range` с floor division в теле
- `for range` с отрицательным шагом
- `for range` с multiply/subtract обновлениями
- `for ... else`
- nested `while`
- `for range` с `break/continue`
- `while` с несколькими обновляемыми переменными
- `while` с вызовом в другом модуле
- `while` с modulo-фильтрацией
- `while ... else`
- `while` с float accumulation через true division
- nested search loops с межмодульным вызовом и break propagation
- рекурсивные self-calls
- взаимную рекурсию
- простой DFS по неявному дереву
- цикл с рекурсивным helper
- `range` с положительным шагом больше 1
- линейную рекурсию-суммирование

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

## Что всплыло и было закрыто в последней loop-итерации

На реальных loop-heavy кейсах дополнительно пришлось закрыть:

- `CPyTagged_Remainder` для `%`
- `CPyTagged_Rshift` для `// 2`
- `CPyTagged_TrueDivide` для true division в loop-кейсах
- коллизии synthetic register names для нескольких `CallC` в одной функции
- неправильный порядок регистрации synthetic results, из-за которого nested `ValueCase.OP` мог ссылаться на текущий op, а не на предыдущий
- weak nested-op ссылки, где protobuf сохраняет только упрощенный `Op`-stub без аргументов, из-за чего понадобилось разрешение по хвосту истории текущего op

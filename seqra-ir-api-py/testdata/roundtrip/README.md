# Round-Trip Corpus

Небольшой набор примеров для проверки `original -> mypy IR -> proto -> PIR -> generated Python`.

Структура одного кейса:

- `case.json` — метаданные запуска
- `*.py` — исходные модули примера

Поля `case.json`:

- `description` — краткое описание кейса
- `entry_file` — файл, содержащий entrypoint
- `function` — функция для сравнения
- `args_json` — JSON-массив позиционных аргументов

Эти кейсы запускаются через:

```bash
python seqra-ir-api-py/scripts/run_roundtrip_examples.py
```

Небольшой generator для случайных кейсов в уже поддержанном подмножестве запускается так:

```bash
python seqra-ir-api-py/scripts/run_python_fuzz.py --count 20 --seed 7
```

Сейчас corpus покрывает:

- простые межмодульные вызовы
- ветвления `if/elif/else`
- несколько модулей с вложенными вызовами
- `while`
- `continue`
- `break`
- `for range`
- `for range` с floor division в теле
- `for range` с отрицательным шагом
- `for range` с multiply/subtract обновлениями
- `for ... else`
- nested `while`
- `for range` c `break/continue`
- `while` с несколькими обновляемыми переменными
- `while` с межмодульным вызовом в теле
- `while` с modulo-фильтрацией
- `while ... else`
- `while` с float accumulation через true division
- nested search loops с межмодульным вызовом и ранним выходом
- рекурсивные self-calls на одном модуле
- взаимную рекурсию
- простой DFS по неявному дереву
- цикл с рекурсивным helper
- `range` с положительным шагом больше 1
- линейную рекурсию-суммирование

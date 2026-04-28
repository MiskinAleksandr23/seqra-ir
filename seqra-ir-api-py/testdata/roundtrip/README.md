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

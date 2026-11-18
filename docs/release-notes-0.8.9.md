AA Mazda Monitor 0.8.9

**Русский**

- Добавлены новые датчики SkyActiv-D: перепад давления на сажевом, EGR (A/B), заслонка впуска, общий пробег, заряд АКБ и накопление сажи (22F47A, 22F469, 22F411, 22DD01, 224028, 22042B).
- Температура масла двигателя теперь обновляется как медленный датчик — меньше нагрузка на адаптер.
- «Проверка датчиков» теперь опрашивает датчики при подключении и помечает «Нет ответа» для тех, что не отвечают.
- Добавлен бенчмарк адаптера: измерение скорости ответа и оценка качества (отлично/хорошо/средне/медленно).
- Добавлен резервный разбор ISO-TP: работает с адаптерами, которые не убирают служебные байты.
- Пользовательские PID: можно добавлять свои датчики, вводить формулу в стиле Torque, тестировать ответ в реальном времени, импортировать из CSV.
- Улучшено логирование RegenTracker: более точный учёт интервалов прожига.
- Версия 0.8.9 — ставится поверх 0.8.8 без удаления.

**English**

- New SkyActiv-D sensors: DPF differential pressure, EGR (A/B), intake shutter, total distance, battery SOC and soot accumulation (22F47A, 22F469, 22F411, 22DD01, 224028, 22042B).
- Engine oil temperature is now a slow sensor — less load on the adapter.
- “Sensor check” now probes sensors on connect and marks “No answer” for those that do not respond.
- Adapter benchmark added: measures response speed and rates quality (excellent/good/moderate/slow).
- ISO-TP fallback parser added: works with adapters that keep service bytes.
- Custom PIDs: add your own sensors, enter a Torque-style formula, test live, import from CSV.
- RegenTracker logging improved: more accurate regeneration interval tracking.
- Version 0.8.9 — installs over 0.8.8 without uninstalling.

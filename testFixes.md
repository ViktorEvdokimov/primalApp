# Исправления тестов

## MonsterTest.kt — отсутствует обязательный параметр name, неверный вызов endRound, перепутаны assertTrue/assertFalse

**Проблемы:**
1. `Monster()` вызывается без обязательного параметра `name` в тестах `toggleHardened`, `resetPhase`, `addRage`, `endRound`
2. `com.primalapp.model.ext.endRound(monster, 4)` — попытка вызвать пакет как значение (ошибка компиляции)
3. Тест `toggleHardened flips status` — перепутаны `assertTrue`/`assertFalse` в цепочке вызовов (после первого вызова isHardened меняется с false на true, поэтому первое утверждение должно быть assertTrue)

**Исправление:**
- Добавлен `name = "Test"` во все вызовы Monster()
- `endRound` импортирован и вызван как extension-функция
- Исправлен порядок assertTrue/assertFalse в toggleHardened тесте

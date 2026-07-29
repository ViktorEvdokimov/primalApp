# Исправления реализации

## 1. PlatformContext — expect/actual typealias несовместим с Kotlin 2.0.21

**Проблема:** Компиляция падает с ошибкой:
```
'actual typealias PlatformContext = Context' has no corresponding expected declaration
The following declaration is incompatible because modality is different:
    expect class PlatformContext : Any
```

**Причина:** В Kotlin 2.0 `expect class PlatformContext` считается финальным (modality=FINAL), а `android.content.Context` — абстрактный (modality=ABSTRACT). Typealias не может разрешить это несоответствие.

**Решение:** Заменить `actual typealias PlatformContext = Context` на `actual class PlatformContext(val context: Context)` в androidMain. Обновить использование в `MainActivity.kt` и `createPrimalDatabase()`.

**Изменённые файлы:**
- `shared/src/androidMain/kotlin/com/primalapp/database/Platform.android.kt`
- `androidApp/src/main/kotlin/com/primalapp/android/MainActivity.kt`

package com.primalapp.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Колонки таблицы в текущей схеме БД. */
internal fun SQLiteConnection.columnsOf(table: String): Set<String> {
    val statement = prepare("PRAGMA table_info($table)")
    try {
        return buildSet {
            while (statement.step()) add(statement.getText(1))
        }
    } finally {
        statement.close()
    }
}

/**
 * Вставляет строку seed-каталога только в колонки, уже существующие в схеме: ранние миграции
 * (10→11, 12→13) вызывают тот же seed, что и последняя версия, а колонки добавляются позже.
 * [values] — имя колонки → готовый SQL-литерал.
 */
internal fun SQLiteConnection.insertExistingColumns(table: String, columns: Set<String>, values: Map<String, String>) {
    val present = values.filterKeys { it in columns }
    execSQL("INSERT INTO $table (${present.keys.joinToString(", ")}) VALUES (${present.values.joinToString(", ")})")
}

internal fun sqlText(value: String): String = "'$value'"

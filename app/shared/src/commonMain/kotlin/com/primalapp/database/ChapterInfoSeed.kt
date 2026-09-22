package com.primalapp.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Каталог глав из doc/compainInfo.md.
 * Форматы колонок:
 * - rewards / reward_plants: "NAME:qty;NAME2:qty"
 * - open_quests / expire_quests: "1,2,36"
 * - conditional_open_quests: "Достижение1,Достижение2:quest|elseQuest|requireAll(0/1)|negated(0/1);..."
 * - decisions: "Вопрос?вариант1|вариант2|достижение|вариантДляДостижения;..."
 * - messages: "текст;текст2"
 * - conditional_messages: "Достижение:текст;..."
 * Ссылки на задания 41/46 оставлены как есть (по решению пользователя, qa 50).
 * Задания 41-49 реализованы в TaskInfoSeed; условные открытия/истечения 42-49 добавлены (задача 40.3).
 */
private val CHAPTER_INFO_ROWS = listOf(
    arrayOf(
        "1",
        "BONES:1;SCALES:1;BLOOD:2",
        "ALBALACEA:1;ANTHEMON:1;MELLIS:1;NILLEA:2",
        "1,2,36", "", "", "0", "0", "0", "", "", ""
    ),
    arrayOf(
        "2",
        "", "",
        "3,41,46", "", "", "0", "0", "0", "", "", ""
    ),
    arrayOf(
        "3",
        "", "",
        "", "", "2,36", "0", "0", "1", "", "", ""
    ),
    arrayOf(
        "4",
        "", "",
        "11", "Народ Золотых гор:7:8:0:0;Затишье:9::0:0", "1,3,4,5,31,41,46", "1", "1", "0",
        "", "", "Яд Пазиса:Получите награду 25"
    ),
    arrayOf(
        "5",
        "", "",
        "", "Тайны прошлого:12::0:0;Пыль аркеума:13::0:0;Лагерь в джунглях:37:38:0:0;Упавшая звезда:15:47:0:0", "", "0", "0", "1", "", "", ""
    ),
    arrayOf(
        "6",
        "", "",
        "22", "Змеиная кровь:42:43:0:0", "10", "0", "0", "0", "", "", ""
    ),
    arrayOf(
        "7",
        "", "",
        "", "Гербарий:24::0:0;Фолиант о чудовищах:33::0:0", "7,9,11", "0", "0", "1",
        "Тренироваться у подножья Волтьяра?Да|Нет?Голос Волтьяра?Да", "", ""
    ),
    arrayOf(
        "8",
        "", "",
        "25", "Гербарий:44::0:0", "37,47", "1", "1", "1", "", "", ""
    ),
    arrayOf(
        "9",
        "", "",
        "", "Звезда дракона:28::0:1;Упавшая звезда,Звезда дракона:49::1:0", "8,15,20,48", "0", "0", "1", "", "", ""
    ),
    arrayOf(
        "10",
        "", "",
        "", "Три копья:29::0:0;Эхо водопада:40::0:0;Неоплаченный долг,Гербарий:35::1:0", "13", "0", "0", "1", "", "", ""
    ),
    arrayOf(
        "11",
        "", "",
        "", "", "", "0", "0", "0", "", "", ""
    )
)

fun seedChapterInfo(db: SQLiteConnection) {
    CHAPTER_INFO_ROWS.forEach { row ->
        db.execSQL(
            "INSERT INTO chapter_info (chapter, rewards, reward_plants, open_quests, conditional_open_quests, expire_quests, forge_upgrade, lab_upgrade, hunter_kit_upgrade, decisions, messages, conditional_messages) " +
                "VALUES (${row[0]}, '${row[1]}', '${row[2]}', '${row[3]}', '${row[4]}', '${row[5]}', ${row[6]}, ${row[7]}, ${row[8]}, '${row[9]}', '${row[10]}', '${row[11]}')"
        )
    }
}

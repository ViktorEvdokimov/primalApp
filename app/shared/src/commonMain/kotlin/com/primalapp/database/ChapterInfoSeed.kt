package com.primalapp.database

import androidx.sqlite.SQLiteConnection
import com.primalapp.database.entity.ChapterInfoEntity

/**
 * Каталог глав из doc/compainInfo.md.
 * Форматы колонок:
 * - rewards / reward_plants: "NAME:qty;NAME2:qty"
 * - open_quests / expire_quests: "1,2,36"
 * - conditional_open_quests: "Достижение1,Достижение2:quest|elseQuest|requireAll(0/1)|negated(0/1);..."
 * - decisions: "Вопрос?вариант1|вариант2|достижение|вариантДляДостижения;..."
 * - messages: "текст;текст2"
 * - conditional_messages: "Достижение:текст;..."
 * - [12] улучшение набора охотника только при достижении (C-9), [13] истекают все задания (0/1),
 *   [14] босс финального боя (R-6) — необязательные колонки.
 * Ссылки на задания 41/46 оставлены как есть (по решению пользователя, qa 50).
 * Задания 41-49 реализованы в TaskInfoSeed; условные открытия/истечения 42-49 добавлены (задача 40.3).
 * «Заезда Дракона» из гл. 9 источника — опечатка «Звезда дракона» (подтверждено пользователем, qa 93).
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
        "25", "Гербарий:44::0:0", "37,47", "1", "1", "1", "", "", "",
        "Голос Волтьяра"
    ),
    arrayOf(
        "9",
        "", "",
        "", "Звезда дракона:28::0:1;Упавшая звезда,Звезда дракона:49::1:0", "8,15,20,48", "0", "0", "1", "", "", ""
    ),
    arrayOf(
        "10",
        "", "",
        "", "Три копья:29::0:0;Эхо водопада:40::0:0;Неоплаченный долг,Гербарий:35::1:0;Три копья,Эхо водопада:30::1:1",
        "13", "0", "0", "1", "", "", "",
        "Голос Волтьяра"
    ),
    arrayOf(
        "11",
        "", "",
        "", "", "", "0", "0", "0", "",
        "Истекло время всех заданий. Следующий бой — финальный: Пробуждённый", "",
        "", "1", "Пробуждённый"
    )
)

/** Каталог глав в виде сущностей — для сквозной проверки seed-данных в тестах (задача 42.1). */
internal fun chapterInfoSeedEntities(): List<ChapterInfoEntity> = CHAPTER_INFO_ROWS.map { row ->
    ChapterInfoEntity(
        chapter = row[0].toInt(),
        rewards = row[1],
        rewardPlants = row[2],
        openQuests = row[3],
        conditionalOpenQuests = row[4],
        expireQuests = row[5],
        forgeUpgrade = row[6] == "1",
        labUpgrade = row[7] == "1",
        hunterKitUpgrade = row[8] == "1",
        decisions = row[9],
        messages = row[10],
        conditionalMessages = row[11],
        hunterKitUpgradeAchievement = row.getOrNull(12).orEmpty(),
        expireAllQuests = row.getOrNull(13) == "1",
        finalBoss = row.getOrNull(14).orEmpty()
    )
}

/** Колонки 12–14 появились в версии 15: ранние миграции вставляют только существующие колонки. */
fun seedChapterInfo(db: SQLiteConnection) {
    val columns = db.columnsOf("chapter_info")
    CHAPTER_INFO_ROWS.forEach { row ->
        db.insertExistingColumns(
            "chapter_info", columns,
            linkedMapOf(
                "chapter" to row[0],
                "rewards" to sqlText(row[1]),
                "reward_plants" to sqlText(row[2]),
                "open_quests" to sqlText(row[3]),
                "conditional_open_quests" to sqlText(row[4]),
                "expire_quests" to sqlText(row[5]),
                "forge_upgrade" to row[6],
                "lab_upgrade" to row[7],
                "hunter_kit_upgrade" to row[8],
                "decisions" to sqlText(row[9]),
                "messages" to sqlText(row[10]),
                "conditional_messages" to sqlText(row[11]),
                "hunter_kit_upgrade_achievement" to sqlText(row.getOrNull(12).orEmpty()),
                "expire_all_quests" to if (row.getOrNull(13) == "1") "1" else "0",
                "final_boss" to sqlText(row.getOrNull(14).orEmpty())
            )
        )
    }
}

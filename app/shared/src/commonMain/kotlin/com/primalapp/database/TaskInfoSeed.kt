package com.primalapp.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Каталог заданий из doc/taskInfo.md.
 * Формат колонок: материи/растения "NAME:qty;NAME2:qty", списки номеров через запятую.
 * Формат условий: `kind|achievement|chapterSet|quest|else|rewardAchievement` через ';' между условиями.
 * Очевидные опечатки исправлены (см. doc/qa.md). Составное условие зад. 25 —
 * `ACHIEVEMENT_OWNED_IN_CHAPTER` (достижение И глава), условные достижения зад. 29/40 — поле rewardAchievement.
 */
private val TASK_INFO_ROWS = listOf(
    // 1 Память пустыни
    arrayOf(
        "1", "Память пустыни", "Торамат", "HORN",
        "BONES:2;ZLATIA:2",
        "NILLEA:2;TARMARET:1;ALBALACEA:1;SELICORNIA:1",
        "", "CHAPTER_IN||1,2|4|6", "", "", "", "6", ""
    ),
    // 2 Полёт в вечную бурю
    arrayOf(
        "2", "Полёт в вечную бурю", "Озев", "LIGHTNING",
        "BLOOD:2;ZIMIA:1;IRIDIA:1",
        "ALBALACEA:2;MELLIS:1;ANTHEMON:1;SELICORNIA:1",
        "", "CHAPTER_IN||1,2|5|", "", "", "", "", "CHAPTER_IN||3|1|1"
    ),
    // 3 Рёв моря
    arrayOf(
        "3", "Рёв моря", "Коровон", "CORAL",
        "SCALES:1;ZIMIA:2;ZLATIA:1",
        "NILLEA:1;MELLIS:2;ANTHEMON:2",
        "", "", "Затишье", "", "", "10", ""
    ),
    // 4 Вожак стаи
    arrayOf(
        "4", "Вожак стаи", "Фелаксир", "CRYSTAL",
        "BONES:1;ZIMIA:1;IRIDIA:2",
        "TARMARET:2;MELLIS:1;SELICORNIA:2",
        "", "", "Тайны прошлого", "", "", "6", ""
    ),
    // 5 Серебряные когти
    arrayOf(
        "5", "Серебряные когти", "Юром", "METAL",
        "SCALES:2;IRIDIA:1;ZLATIA:1",
        "TARMARET:2;ALBALACEA:1;ANTHEMON:1;SELICORNIA:1",
        "", "", "Пыль аркеума;Народ золотых гор", "1", "", "", ""
    ),
    // 6 Охота меж двух пустынь
    arrayOf(
        "6", "Охота меж двух пустынь", "Фелаксир", "CRYSTAL",
        "BONES:1;ZIMIA:2;IRIDIA:1",
        "TARMARET:1;ALBALACEA:1;MELLIS:2;SELICORNIA:1",
        "14", "", "", "2", "", "", ""
    ),
    // 7 Храм Зарка
    arrayOf(
        "7", "Храм Зарка", "Таррагуа", "METAL",
        "BONES:1;IRIDIA:2;ZLATIA:1",
        "TARMARET:1;ALBALACEA:1;ANTHEMON:1;SELICORNIA:2",
        "", "", "Горящий уголёк;Фолиант о чудовищах", "3", "", "26", ""
    ),
    // 8 Пожиратель железа
    arrayOf(
        "8", "Пожиратель железа", "Таррагуа", "METAL",
        "BONES:1;IRIDIA:1;ZLATIA:2",
        "TARMARET:2;ALBALACEA:1;SELICORNIA:2",
        "16", "", "", "", "", "26", ""
    ),
    // 9 Звёздная пещера
    arrayOf(
        "9", "Звёздная пещера", "Оруксен", "CORAL",
        "SCALES:1;BONES:1;ZIMIA:2",
        "NILLEA:1;TARMARET:1;MELLIS:2;ANTHEMON:1",
        "15", "", "", "", "", "", ""
    ),
    // 10 Затопленные земли
    arrayOf(
        "10", "Затопленные земли", "Оруксен", "CORAL",
        "SCALES:1;BONES:1;BLOOD:1;ZIMIA:1",
        "NILLEA:1;MELLIS:2;ANTHEMON:2",
        "", "", "", "4", "", "", ""
    ),
    // 11 Город памяти
    arrayOf(
        "11", "Город памяти", "Харджа", "FIRE",
        "BONES:1;BLOOD:2;IRIDIA:1",
        "NILLEA:2;TARMARET:1;ALBALACEA:1;MELLIS:1",
        "", "ACHIEVEMENT_OWNED|Грибной лес||32|17", "Гербарий", "", "", "", ""
    ),
    // 12 Древняя Каэр Мага
    arrayOf(
        "12", "Древняя Каэр Мага", "Дигоракс", "HORN",
        "SCALES:1;BONES:2;ZLATIA:1",
        "NILLEA:2;TARMARET:1;ALBALACEA:1;SELICORNIA:1",
        "18", "", "", "", "", "", ""
    ),
    // 13 Королева Озевов
    arrayOf(
        "13", "Королева Озевов", "Озев", "LIGHTNING",
        "BLOOD:2;ZIMIA:1;IRIDIA:1",
        "ALBALACEA:2;MELLIS:2;SELICORNIA:1",
        "19", "", "Три копья", "5", "", "", ""
    ),
    // 14 Тысячеликий дракон
    arrayOf(
        "14", "Тысячеликий дракон", "Дигоракс", "HORN",
        "SCALES:1;BONES:1;ZLATIA:1",
        "NILLEA:2;TARMARET:1;ALBALACEA:1;SELICORNIA:1",
        "", "", "", "6", "", "", ""
    ),
    // 15 Странные кристаллы
    arrayOf(
        "15", "Странные кристаллы", "Фелаксир", "CRYSTAL",
        "BONES:1;ZIMIA:2;IRIDIA:1",
        "NILLEA:1;TARMARET:1;MELLIS:1;ANTHEMON:1",
        "", "CHAPTER_IN||5,6,7|20|", "", "", "", "", ""
    ),
    // 16 Святилище бури
    arrayOf(
        "16", "Святилище бури", "Озев", "LIGHTNING",
        "BLOOD:2;ZIMIA:1;IRIDIA:1",
        "TARMARET:1;ALBALACEA:1;ANTHEMON:2;SELICORNIA:1",
        "21", "", "Неоплаченный долг", "5", "", "", ""
    ),
    // 17 Цветок в тумане
    arrayOf(
        "17", "Цветок в тумане", "Вираксен", "FIRE",
        "SCALES:2;BLOOD:2",
        "NILLEA:1;ALBALACEA:1;MELLIS:2;ANTHEMON:1",
        "", "", "", "7", "", "", ""
    ),
    // 18 Залы памяти
    arrayOf(
        "18", "Залы памяти", "Торамат", "HORN",
        "BONES:2;ZLATIA:2",
        "NILLEA:1;TARMARET:2;ALBALACEA:1;SELICORNIA:1",
        "", "", "Копье драконоборца", "8", "", "", ""
    ),
    // 19 Трон в недрах горы
    arrayOf(
        "19", "Трон в недрах горы", "Юром", "METAL",
        "SCALES:1;IRIDIA:1;ZLATIA:2",
        "TARMARET:1;ALBALACEA:2;ANTHEMON:1;SELICORNIA:1",
        "", "", "", "9", "", "", ""
    ),
    // 20 Кристаллизация
    arrayOf(
        "20", "Кристаллизация", "Моркраас", "CRYSTAL",
        "SCALES:1;ZIMIA:1;IRIDIA:2",
        "NILLEA:1;TARMARET:1;ALBALACEA:1;SELICORNIA:2",
        "", "", "Звезда дракона", "10,11,12,13", "", "", ""
    ),
    // 21 Трон дракона
    arrayOf(
        "21", "Трон дракона", "Юром", "METAL",
        "SCALES:1;IRIDIA:1;ZLATIA:2",
        "TARMARET:1;ALBALACEA:1;ANTHEMON:2;SELICORNIA:1",
        "", "", "", "9", "", "", ""
    ),
    // 22 В сиянии кораллов
    arrayOf(
        "22", "В сиянии кораллов", "Коровон", "CORAL",
        "SCALES:1;ZIMIA:2;ZLATIA:1",
        "NILLEA:1;MELLIS:2;ANTHEMON:2",
        "23", "", "", "14", "", "", ""
    ),
    // 23 Пещера красных кораллов
    arrayOf(
        "23", "Пещера красных кораллов", "Оруксен", "CORAL",
        "SCALES:1;BONES:1;ZIMIA:1;ZLATIA:1",
        "NILLEA:1;MELLIS:2;ANTHEMON:2",
        "", "", "", "15", "", "", ""
    ),
    // 24 Вечная гробница
    arrayOf(
        "24", "Вечная гробница", "Дигоракс", "HORN",
        "SCALES:1;BONES:1;ZLATIA:2",
        "NILLEA:1;TARMARET:1;ALBALACEA:1;ANTHEMON:1;SELICORNIA:1",
        "", "", "Кости дракона", "16", "", "", ""
    ),
    // 25 Горящее солнце
    arrayOf(
        "25", "Горящее солнце", "Харджа", "FIRE",
        "BONES:1;BLOOD:2;IRIDIA:1",
        "NILLEA:2;ALBALACEA:1;MELLIS:2",
        "", "ACHIEVEMENT_OWNED_IN_CHAPTER|Горящий уголёк|8|34|;ACHIEVEMENT_OWNED|Горящий уголёк||27|", "", "17", "", "", ""
    ),
    // 26 Голос гор
    arrayOf(
        "26", "Голос гор", "Таррагуа", "METAL",
        "BONES:1;IRIDIA:1;ZLATIA:2",
        "TARMARET:1;ALBALACEA:1;ANTHEMON:1;SELICORNIA:2",
        "", "", "", "18,19,20,21", "", "", ""
    ),
    // 27 Негасимое пламя
    arrayOf(
        "27", "Негасимое пламя", "Вираксен", "FIRE",
        "SCALES:2;BLOOD:2",
        "NILLEA:2;TARMARET:1;ALBALACEA:2",
        "", "", "", "22", "", "", ""
    ),
    // 28 Гора из плоти и кристаллов
    arrayOf(
        "28", "Гора из плоти и кристаллов", "Моркраас", "CRYSTAL",
        "SCALES:1;ZIMIA:1;IRIDIA:2",
        "TARMARET:1;ALBALACEA:1;ANTHEMON:1;SELICORNIA:1",
        "", "", "", "10,11,12,13", "", "", ""
    ),
    // 29 Пещеры эха
    arrayOf(
        "29", "Пещеры эха", "Иекорос", "LIGHTNING",
        "SCALES:1;BLOOD:1;ZIMIA:1;IRIDIA:1",
        "TARMARET:1;ALBALACEA:1;MELLIS:1;ANTHEMON:1;SELICORNIA:1",
        "", "ACHIEVEMENT_OWNED|Голос Волтьяра||||Уробборос", "", "", "", "", ""
    ),
    // 30 Голос бури
    arrayOf(
        "30", "Голос бури", "Иекорос", "LIGHTNING",
        "SCALES:1;BLOOD:2;ZIMIA:1",
        "TARMARET:1;MELLIS:1;ANTHEMON:2;SELICORNIA:1",
        "", "", "", "", "", "", ""
    ),
    // 31 Крушение дирижабля
    arrayOf(
        "31", "Крушение дирижабля", "Зекат", "LIGHTNING",
        "SCALES:1;BLOOD:1;ZIMIA:1;IRIDIA:1",
        "TARMARET:1;MELLIS:1;ANTHEMON:1;SELICORNIA:2",
        "", "", "Грибной лес", "", "", "", ""
    ),
    // 32 Подземный цветок
    arrayOf(
        "32", "Подземный цветок", "Зекалит", "LIGHTNING",
        "SCALES:1;BLOOD:1;ZIMIA:1;IRIDIA:1",
        "TARMARET:1;MELLIS:1;ANTHEMON:1;SELICORNIA:2",
        "", "", "", "7", "", "", ""
    ),
    // 33 Последняя страница
    arrayOf(
        "33", "Последняя страница", "Кситерос", "FEATHER",
        "BONES:1;ZIMIA:2;IRIDIA:1",
        "NILLEA:2;MELLIS:2;ANTHEMON:1",
        "", "", "", "", "Каждый охотник получает 2 карты крови изначального", "", ""
    ),
    // 34 Дыхание дракона
    arrayOf(
        "34", "Дыхание дракона", "Тараск", "FIRE",
        "SCALES:1;BONES:1;BLOOD:1;IRIDIA:1",
        "NILLEA:1;TARMARET:1;ALBALACEA:2;ANTHEMON:1",
        "", "", "", "22", "", "", ""
    ),
    // 35 В жерле вулкана
    arrayOf(
        "35", "В жерле вулкана", "Тараск", "FIRE",
        "SCALES:1;BONES:1;BLOOD:1;IRIDIA:1",
        "TARMARET:1;ALBALACEA:2;MELLIS:1",
        "", "", "", "24", "", "", ""
    ),
    // 36 Чудовище «Муары»
    arrayOf(
        "36", "Чудовище «Муары»", "Пазис", "FEATHER",
        "BONES:1;ZIMIA:2;ZLATIA:1",
        "NILLEA:2;TARMARET:1;MELLIS:1;ANTHEMON:1",
        "", "", "Лагерь в джунглях;Яд пазиса", "", "", "", ""
    ),
    // 37 Речной дракон
    arrayOf(
        "37", "Речной дракон", "Нагарджас", "FEATHER",
        "SCALES:1;BONES:1;ZIMIA:1;ZLATIA:1",
        "NILLEA:2;MELLIS:2;ANTHEMON:1",
        "", "", "Хозяйка фонаря;Эхо водопада", "", "", "", ""
    ),
    // 38 Охота в джунглях
    arrayOf(
        "38", "Охота в джунглях", "Пазис", "FEATHER",
        "BONES:1;ZIMIA:2;ZLATIA:1",
        "NILLEA:2;MELLIS:1;ANTHEMON:2",
        "39", "", "", "25", "", "", ""
    ),
    // 39 Камни изначальных
    arrayOf(
        "39", "Камни изначальных", "Нагарджас", "FEATHER",
        "SCALES:1;BONES:1;ZIMIA:2",
        "NILLEA:2;MELLIS:1;ANTHEMON:2",
        "", "", "Хозяйка фонаря", "26,27,28,29", "", "", ""
    ),
    // 40 Бездна под водопадом
    arrayOf(
        "40", "Бездна под водопадом", "Иекорос", "LIGHTNING",
        "SCALES:1;BLOOD:1;ZIMIA:1;IRIDIA:1",
        "TARMARET:2;ALBALACEA:1;ANTHEMON:1;SELICORNIA:1",
        "", "ACHIEVEMENT_OWNED|Голос Волтьяра||||Уробборос", "", "", "", "", ""
    ),
    // 41 Умирающий лес
    arrayOf(
        "41", "Умирающий лес", "Гидар", "POISON",
        "SCALES:1;BLOOD:2;IRIDIA:1",
        "NILLEA:1;TARMARET:1;MELLIS:2;ANTHEMON:1",
        "", "", "Змеиная кровь", "", "", "", ""
    ),
    // 42 Тёмная трясина
    arrayOf(
        "42", "Тёмная трясина", "Рейкал", "POISON",
        "SCALES:1;BLOOD:2;IRIDIA:1",
        "TARMARET:1;MELLIS:1;ANTHEMON:2;SELICORNIA:1",
        "", "QUEST_NOT_AVAILABLE||18|45||", "", "30,31", "", "", ""
    ),
    // 43 Воплощение ночи
    arrayOf(
        "43", "Воплощение ночи", "Рейкал", "POISON",
        "SCALES:2;BLOOD:2",
        "TARMARET:1;MELLIS:2;ANTHEMON:1;SELICORNIA:1",
        "", "", "", "32", "", "", ""
    ),
    // 44 Луноцвет
    arrayOf(
        "44", "Луноцвет", "Гидар", "POISON",
        "SCALES:1;BLOOD:1;ZIMIA:1;IRIDIA:1",
        "NILLEA:1;TARMARET:1;MELLIS:2;ANTHEMON:1",
        "", "", "", "33", "", "", ""
    ),
    // 45 Залы памяти
    arrayOf(
        "45", "Залы памяти", "Торамат", "HORN",
        "BONES:2;ZLATIA:2",
        "NILLEA:2;TARMARET:1;ALBALACEA:1;SELICORNIA:1",
        "", "", "Копьё драконоборца", "8", "", "", ""
    ),
    // 46 Ненасытная зима
    arrayOf(
        "46", "Ненасытная зима", "Сиркаадж", "ICE",
        "BONES:1;IRIDIA:1;ZLATIA:2",
        "TARMARET:1;ALBALACEA:2;SELICORNIA:2",
        "", "", "Упавшая звезда", "", "", "", ""
    ),
    // 47 Морозный укус
    arrayOf(
        "47", "Морозный укус", "Сиркаадж", "ICE",
        "BONES:1;BLOOD:1;IRIDIA:1;ZLATIA:1",
        "TARMARET:1;ALBALACEA:2;SELICORNIA:2",
        "48", "", "", "35", "", "", "", "Оледенение"
    ),
    // 48 Ледяная гробница
    arrayOf(
        "48", "Ледяная гробница", "Мумараак", "ICE",
        "BONES:1;IRIDIA:1;ZLATIA:2",
        "TARMARET:1;ALBALACEA:2;SELICORNIA:2",
        "", "", "", "36,37", "", "", "", "Оледенение"
    ),
    // 49 Звёздные врата
    arrayOf(
        "49", "Звёздные врата", "Мумараак", "ICE",
        "BONES:1;BLOOD:1;IRIDIA:1;ZLATIA:1",
        "TARMARET:1;ALBALACEA:2;SELICORNIA:2",
        "", "", "Озеро Небесного", "34", "", "", ""
    )
)

fun seedTaskInfo(db: SQLiteConnection) {
    TASK_INFO_ROWS.forEach { row ->
        db.execSQL(
            "INSERT INTO task_info (quest_number, name, boss_name, boss_element, victory_materials, victory_plants, victory_open_quests, victory_open_quest_conditions, victory_achievements, victory_reward_cards, victory_special, defeat_open_quests, defeat_open_quest_conditions, defeat_achievements) " +
                "VALUES (${row[0]}, '${row[1]}', '${row[2]}', '${row[3]}', '${row[4]}', '${row[5]}', '${row[6]}', '${row[7]}', '${row[8]}', '${row[9]}', '${row[10]}', '${row[11]}', '${row[12]}', '${row.getOrNull(13).orEmpty()}')"
        )
    }
}

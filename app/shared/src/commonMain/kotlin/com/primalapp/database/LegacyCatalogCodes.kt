package com.primalapp.database

/**
 * Коды каталога для переноса кампаний старых версий приложения (миграция 17→18): старая БД хранила боссов и
 * достижения названиями. Список соответствует каталогу на момент переноса; новые боссы и достижения появляются
 * уже с кодами.
 */
internal object LegacyCatalogCodes {

    val bossCodes: Map<String, String> = mapOf(
        "Коровон" to "KOROVON",
        "Оруксен" to "ORUXEN",
        "Моркраас" to "MORKRAAS",
        "Фелаксир" to "FELAXIR",
        "Мумараак" to "MUMARAAK",
        "Сиркаадж" to "SIRKAAJ",
        "Таррагуа" to "TARRAGUA",
        "Юром" to "YUROM",
        "Зекалит" to "ZEKALIT",
        "Зекат" to "ZEKAT",
        "Иекорос" to "IEKOROS",
        "Озев" to "OZEV",
        "Вираксен" to "VIRAXEN",
        "Тараск" to "TARASK",
        "Харджа" to "KHARJA",
        "Кситерос" to "XITEROS",
        "Нагарджас" to "NAGARJAS",
        "Пазис" to "PAZIS",
        "Дигоракс" to "DIGORAX",
        "Торамат" to "TORAMAT",
        "Гидар" to "GIDAR",
        "Рейкал" to "REIKAL",
        "Пробуждённый" to "AWAKENED"
    )

    val achievementCodes: Map<String, String> = mapOf(
        "Эхо водопада" to "EKHO_VODOPADA",
        "Фолиант о чудовищах" to "FOLIANT_O_CHUDOVISHCHAKH",
        "Гербарий" to "GERBARIY",
        "Голос Волтьяра" to "GOLOS_VOLTYARA",
        "Горящий уголёк" to "GORYASHCHIY_UGOLEK",
        "Грибной лес" to "GRIBNOY_LES",
        "Хозяйка фонаря" to "KHOZYAYKA_FONARYA",
        "Копьё драконоборца" to "KOPE_DRAKONOBORTSA",
        "Кости дракона" to "KOSTI_DRAKONA",
        "Лагерь в джунглях" to "LAGER_V_DZHUNGLYAKH",
        "Народ Золотых гор" to "NAROD_ZOLOTYKH_GOR",
        "Неоплаченный долг" to "NEOPLACHENNYY_DOLG",
        "Оледенение" to "OLEDENENIE",
        "Озеро Небесного" to "OZERO_NEBESNOGO",
        "Пыль аркеума" to "PYL_ARKEUMA",
        "Тайны прошлого" to "TAYNY_PROSHLOGO",
        "Три копья" to "TRI_KOPYA",
        "Упавшая звезда" to "UPAVSHAYA_ZVEZDA",
        "Уробборос" to "UROBBOROS",
        "Яд Пазиса" to "YAD_PAZISA",
        "Затишье" to "ZATISHE",
        "Змеиная кровь" to "ZMEINAYA_KROV",
        "Звезда дракона" to "ZVEZDA_DRAKONA"
    )
}

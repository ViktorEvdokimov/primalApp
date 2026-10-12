package com.primalapp.info

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Разделы «Инфо» в порядке меню; у символов реакций названий нет — только картинка и описание. */
enum class InfoSectionCode(val title: String, val titled: Boolean) {
    KEYWORDS("Ключевые слова", true),
    REACTIONS("Символы реакций монстров", false),
    TOKENS("Жетоны окружения", true);

    /** Разделы-карточки: картинка видна сразу (символы реакций, жетоны); ключевые слова раскрываются. */
    val cards: Boolean get() = this != KEYWORDS
}

/**
 * Статья «Инфо».
 *
 * @param imageKey ключ картинки: адрес на сервере (`/api/v1/info/images/…`) или `default:N` — картинка из файла
 *   по умолчанию; `null` — без картинки
 */
data class InfoEntry(
    val id: Long,
    val section: InfoSectionCode,
    val title: String?,
    val body: String,
    val imageKey: String?
)

data class InfoSection(val code: InfoSectionCode, val title: String, val titled: Boolean, val entries: List<InfoEntry>)

/** «Инфо» целиком: разделы по порядку меню, статьи — по алфавиту (символы реакций — в порядке добавления). */
data class InfoData(val sections: List<InfoSection>, val version: String) {
    val entries: List<InfoEntry> get() = sections.flatMap { it.entries }

    fun section(code: InfoSectionCode): InfoSection? = sections.firstOrNull { it.code == code }

    fun entry(id: Long): InfoEntry? = entries.firstOrNull { it.id == id }
}

/** Разбор «Инфо»: ответ `GET /api/v1/info` и файл по умолчанию (`default-info.json`, формат выгрузки сервера). */
object InfoJson {

    private val json = Json { ignoreUnknownKeys = true }

    const val DEFAULT_IMAGE_PREFIX = "default:"

    /** Ответ сервера `GET /info`. */
    fun parseServer(text: String): InfoData {
        val root = json.parseToJsonElement(text).jsonObject
        val sections = root["sections"]?.jsonArray.orEmpty().mapNotNull { node ->
            val section = node.jsonObject
            val code = InfoSectionCode.entries.firstOrNull { it.name == section.string("code") } ?: return@mapNotNull null
            InfoSection(
                code = code,
                title = section.string("title") ?: code.title,
                titled = (section["titled"] as? JsonPrimitive)?.booleanOrNull ?: code.titled,
                entries = section["entries"]?.jsonArray.orEmpty().map { entryNode ->
                    val entry = entryNode.jsonObject
                    InfoEntry(
                        id = (entry["id"] as? JsonPrimitive)?.longOrNull ?: 0,
                        section = code,
                        title = entry.string("title"),
                        body = entry.string("body").orEmpty(),
                        imageKey = entry.string("imageUrl")
                    )
                }
            )
        }
        return InfoData(complete(sections), root.string("version").orEmpty())
    }

    /** Картинки файла по умолчанию: ключ `default:N` → байты (PNG, JPEG, WebP, GIF). */
    class DefaultInfo(val data: InfoData, val images: Map<String, ByteArray>)

    /** Файл по умолчанию: статьи в порядке добавления, картинки — Base64 внутри файла. */
    @OptIn(ExperimentalEncodingApi::class)
    fun parseDefault(text: String): DefaultInfo {
        val root = json.parseToJsonElement(text).jsonObject
        val images = mutableMapOf<String, ByteArray>()
        val entries = root["entries"]?.jsonArray.orEmpty().mapIndexedNotNull { index, node ->
            val entry = node.jsonObject
            val section = InfoSectionCode.entries.firstOrNull { it.name == entry.string("section") } ?: return@mapIndexedNotNull null
            val image = entry["image"] as? JsonObject
            val key = image?.string("data")?.let { data ->
                val imageKey = "$DEFAULT_IMAGE_PREFIX${index + 1}"
                images[imageKey] = Base64.decode(data)
                imageKey
            }
            InfoEntry(
                id = (index + 1).toLong(),
                section = section,
                title = entry.string("title")?.takeIf { section.titled },
                body = entry.string("body").orEmpty(),
                imageKey = key
            )
        }
        val sections = InfoSectionCode.entries.map { code ->
            val inSection = entries.filter { it.section == code }
            InfoSection(code, code.title, code.titled, if (code.titled) inSection.sortedWith(titleOrder) else inSection)
        }
        return DefaultInfo(InfoData(sections, "default"), images)
    }

    /** Все три раздела есть всегда, даже пустые («скоро»). */
    private fun complete(sections: List<InfoSection>): List<InfoSection> = InfoSectionCode.entries.map { code ->
        sections.firstOrNull { it.code == code } ?: InfoSection(code, code.title, code.titled, emptyList())
    }

    /** По алфавиту без учёта регистра и различия «ё/е» — как сортирует сервер. */
    private val titleOrder: Comparator<InfoEntry> = compareBy { InfoText.titleKey(it.title.orEmpty()) }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

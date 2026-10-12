package com.primalapp.info

/** Кусок абзаца статьи: текст, жирный текст или ссылка на статью. */
sealed interface InfoSegment {
    val text: String

    data class Plain(override val text: String) : InfoSegment
    data class Bold(override val text: String) : InfoSegment
    data class Link(override val text: String, val entry: InfoEntry) : InfoSegment
}

/**
 * Разметка текста статей «Инфо» — как на сайте (`web/frontend/src/features/info/infoModel.ts`): абзацы через пустую
 * строку, `**жирный**`, в «(См. также «…» .)» названия — ссылки на статьи; поиск по словам.
 */
object InfoText {

    private val seeAlso = Regex("""См\. также(.*?)\.\s*\)""")
    private val quoted = Regex("""(\*\*)?«([^»]+)»(\*\*)?""")
    private val bold = Regex("""\*\*(.+?)\*\*""")
    private val spaces = Regex("""\s+""")

    /**
     * Название для сравнения: без регистра и оформления, «ё» как «е», без пустых скобок значков «( )» — так «Защита /
     * жетон защиты ( )» и ссылка «Защита» сходятся.
     */
    fun titleKey(title: String): String =
        removeEmptyParens(title.replace("**", "").replace('ё', 'е').replace('Ё', 'Е'))
            .replace(spaces, " ")
            .trim()
            .lowercase()

    /** Скобки без букв и цифр внутри («( )», «(  )») — место значка в правилах. */
    private fun removeEmptyParens(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '(') {
                val close = text.indexOf(')', index + 1)
                if (close > 0) {
                    val inside = text.substring(index + 1, close)
                    if (inside.none { it.isLetterOrDigit() || it == '(' }) {
                        index = close + 1
                        continue
                    }
                }
            }
            result.append(char)
            index++
        }
        return result.toString()
    }

    /**
     * Статья по ссылке «См. также «…»»: точное название; иначе — первая часть названия (до « / » или « (»); иначе —
     * название, начинающееся со ссылки.
     */
    fun resolver(entries: List<InfoEntry>): (String) -> InfoEntry? {
        val keyed = entries.mapNotNull { entry -> entry.title?.let { entry to titleKey(it) } }
        return { reference ->
            val wanted = titleKey(reference)
            if (wanted.isEmpty()) {
                null
            } else {
                keyed.firstOrNull { it.second == wanted }?.first
                    ?: keyed.firstOrNull { it.second.split(" / ", " (").first().trim() == wanted }?.first
                    ?: keyed.firstOrNull { it.second.startsWith(wanted) }?.first
            }
        }
    }

    /** Абзацы текста статьи: разделены пустой строкой. */
    fun paragraphs(body: String): List<String> =
        body.split(Regex("""\n\s*\n"""))
            .map { it.replace(Regex("""\s*\n\s*"""), " ").trim() }
            .filter { it.isNotEmpty() }

    /** Абзац на куски: в «См. также» названия в «ёлочках» — ссылки (если статья нашлась), остальное — текст и жирный. */
    fun segments(paragraph: String, resolve: (String) -> InfoEntry?): List<InfoSegment> {
        val result = mutableListOf<InfoSegment>()
        var last = 0
        for (match in seeAlso.findAll(paragraph)) {
            val list = match.groupValues[1]
            val listStart = match.range.first + match.value.indexOf(list)
            for (reference in quoted.findAll(list)) {
                val name = reference.groupValues[2]
                val entry = resolve(name) ?: continue
                val start = listStart + reference.range.first
                if (start < last) continue
                result += plain(paragraph.substring(last, start))
                result += InfoSegment.Link("«${name.trim()}»", entry)
                last = start + reference.value.length
            }
        }
        result += plain(paragraph.substring(last))
        return result
    }

    private fun plain(text: String): List<InfoSegment> {
        val segments = mutableListOf<InfoSegment>()
        var last = 0
        for (match in bold.findAll(text)) {
            if (match.range.first > last) segments += InfoSegment.Plain(text.substring(last, match.range.first))
            segments += InfoSegment.Bold(match.groupValues[1])
            last = match.range.last + 1
        }
        if (last < text.length) segments += InfoSegment.Plain(text.substring(last))
        return segments
    }

    /** Подпись статьи в списках: название, а у символа реакции без названия — начало описания. */
    fun label(entry: InfoEntry, max: Int = 60): String {
        entry.title?.let { return it }
        val text = paragraphs(entry.body).firstOrNull()?.replace("**", "").orEmpty()
        if (text.isEmpty()) return "№ ${entry.id}"
        return if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
    }

    /** Поиск: все слова запроса есть в названии или тексте (без регистра, «ё» как «е»). */
    fun matches(entry: InfoEntry, query: String): Boolean {
        val words = titleKey(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = "${titleKey(entry.title.orEmpty())} ${titleKey(entry.body)}"
        return words.all { it in haystack }
    }
}

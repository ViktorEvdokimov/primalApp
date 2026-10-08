package com.primal.info;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор раздела «Ключевые слова» из Markdown-файла правил (qa № 142). Файл — перевод PDF в Markdown: статьи
 * в нём оформлены по-разному, поэтому названия распознаются по нескольким признакам:
 * <ul>
 *   <li>строка целиком жирная — {@code **Название**}, в том числе перенесённая на следующую строку;</li>
 *   <li>первая строка блока {@code ```…```} (колонка вёрстки), если следующая строка начинается с заглавной;
 *       короткая первая строка с продолжением со строчной — перенесённое название;</li>
 *   <li>строка после конца статьи, совпадающая со ссылкой «См. также «…»» из этого же раздела.</li>
 * </ul>
 * Текст статьи: переносы со знаком «-» склеиваются, строки — в абзацы; новый абзац — пустая строка,
 * «Примечание», «Пример», «(См. также», пункт списка. Разбор не идеален — статьи правит администратор.
 */
final class RulesKeywordParser {

    /** Статья раздела: название и текст (абзацы через пустую строку, {@code **жирный**} сохраняется). */
    record ParsedEntry(String title, String body) {
    }

    private static final Pattern SECTION = Pattern.compile("^#+\\s*Ключевые слова\\s*$");
    private static final Pattern NEXT_SECTION = Pattern.compile("^#{1,4}\\s");
    private static final Pattern BOLD_LINE = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    /** «(См. также «A» , «B (уточнение) » .)» — до «.)»: в названиях бывают свои скобки. */
    static final Pattern SEE_ALSO = Pattern.compile("См\\. также(.*?)\\.\\s*\\)");
    private static final Pattern QUOTED = Pattern.compile("«([^»]+)»");
    /** Перенесённое название в колонке: короткая первая строка без знаков препинания и короткое продолжение. */
    private static final int WRAPPED_TITLE_MAX = 35;
    private static final int WRAPPED_TITLE_TAIL_MAX = 25;
    private static final int TITLE_MAX = 120;

    private RulesKeywordParser() {
    }

    /** Статьи раздела «Ключевые слова»; раздела нет — пустой список. */
    static List<ParsedEntry> parse(String markdown) {
        List<String> section = section(markdown.replace("\r\n", "\n").split("\n", -1));
        if (section.isEmpty()) {
            return List.of();
        }
        List<String> lines = mergeBoldTitles(section);
        Set<String> references = references(lines);

        List<ParsedEntry> entries = new ArrayList<>();
        String title = null;
        List<String> body = new ArrayList<>();
        boolean inFence = false;
        boolean fenceStart = false;
        String previous = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.startsWith("```")) {
                inFence = !inFence;
                fenceStart = inFence;
                continue;
            }
            String next = nextText(lines, i);
            String found = null;
            boolean skipNext = false;
            Matcher bold = BOLD_LINE.matcher(line);
            if (bold.matches() && !line.startsWith("**Примечание") && !line.startsWith("**Пример")) {
                found = bold.group(1);
            } else if (fenceStart && !line.isEmpty()) {
                if (!startsLower(next)) {
                    found = line;
                } else if (line.length() <= WRAPPED_TITLE_MAX && next.length() <= WRAPPED_TITLE_TAIL_MAX
                        && line.indexOf(',') < 0 && line.indexOf('.') < 0) {
                    found = line + " " + next;
                    skipNext = true;
                }
            } else if (!line.isEmpty() && endsEntry(previous) && references.contains(normalize(line))) {
                found = line;
            }
            if (!line.isEmpty()) {
                fenceStart = false;
            }
            if (found != null && found.strip().length() <= TITLE_MAX) {
                add(entries, title, body);
                title = clean(found);
                body = new ArrayList<>();
                if (skipNext) {
                    i = indexOfNextText(lines, i);
                }
            } else if (title != null) {
                body.add(lines.get(i));
            }
            if (!line.isEmpty()) {
                previous = line;
            }
        }
        add(entries, title, body);
        return entries;
    }

    /** Строки между заголовком «Ключевые слова» и следующим заголовком уровня 1–4. */
    private static List<String> section(String[] lines) {
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (SECTION.matcher(lines[i].strip()).matches()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return List.of();
        }
        List<String> section = new ArrayList<>();
        for (int i = start + 1; i < lines.length && !NEXT_SECTION.matcher(lines[i]).find(); i++) {
            section.add(lines[i]);
        }
        return section;
    }

    /** {@code **Название\nпродолжение**} — одна строка. */
    private static List<String> mergeBoldTitles(List<String> lines) {
        List<String> merged = new ArrayList<>();
        StringBuilder pending = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (pending != null) {
                pending.append(' ').append(line);
                if (line.endsWith("**")) {
                    merged.add(pending.toString());
                    pending = null;
                }
                continue;
            }
            if (line.startsWith("**") && !line.endsWith("**") && !line.startsWith("**Пример")
                    && line.indexOf("**", 2) < 0) {
                pending = new StringBuilder(line);
                continue;
            }
            merged.add(raw);
        }
        if (pending != null) {
            merged.add(pending.toString());
        }
        return merged;
    }

    /** Названия из «См. также «…»» раздела — по ним узнаются заголовки без оформления. */
    private static Set<String> references(List<String> lines) {
        String text = String.join("\n", lines).replaceAll("-\n(?=\\p{Ll})", "").replace('\n', ' ');
        Set<String> references = new HashSet<>();
        Matcher seeAlso = SEE_ALSO.matcher(text);
        while (seeAlso.find()) {
            Matcher quoted = QUOTED.matcher(seeAlso.group(1));
            while (quoted.find()) {
                references.add(normalize(quoted.group(1)));
            }
        }
        return references;
    }

    private static int indexOfNextText(List<String> lines, int index) {
        for (int i = index + 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (!line.isEmpty() && !line.startsWith("```")) {
                return i;
            }
        }
        return index;
    }

    private static String nextText(List<String> lines, int index) {
        int next = indexOfNextText(lines, index);
        return next == index ? "" : lines.get(next).strip();
    }

    private static boolean startsLower(String line) {
        return !line.isEmpty() && Character.isLowerCase(line.codePointAt(0));
    }

    private static boolean endsEntry(String line) {
        return line.endsWith(".") || line.endsWith(")") || line.endsWith("»");
    }

    private static void add(List<ParsedEntry> entries, String title, List<String> body) {
        if (title != null && !title.isBlank()) {
            entries.add(new ParsedEntry(title, body(body)));
        }
    }

    /** Строки статьи — в абзацы: переносы со знаком «-» склеиваются, остальные строки — через пробел. */
    static String body(List<String> lines) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                flush(paragraphs, current);
                continue;
            }
            if (startsParagraph(line)) {
                flush(paragraphs, current);
            }
            if (current.isEmpty()) {
                current.append(line);
            } else if (current.charAt(current.length() - 1) == '-' && startsLower(line)) {
                current.setLength(current.length() - 1);
                current.append(line);
            } else {
                current.append(' ').append(line);
            }
        }
        flush(paragraphs, current);
        return String.join("\n\n", paragraphs);
    }

    private static boolean startsParagraph(String line) {
        return line.startsWith("Примечание") || line.startsWith("**Примечание") || line.startsWith("Пример")
                || line.startsWith("**Пример") || line.startsWith("(См. также") || line.startsWith("• ")
                || line.startsWith("y ") || line.startsWith("- ");
    }

    private static void flush(List<String> paragraphs, StringBuilder current) {
        if (!current.isEmpty()) {
            paragraphs.add(current.toString().replaceAll("\\s+", " ").strip());
            current.setLength(0);
        }
    }

    private static String clean(String title) {
        return title.replace("**", "").replaceAll("\\s+", " ").strip();
    }

    /** Для сравнения названий: без регистра и оформления, «ё» как «е». */
    static String normalize(String text) {
        return text.replace("**", "").replace('ё', 'е').replace('Ё', 'Е').replaceAll("\\s+", " ").strip()
                .toLowerCase(Locale.ROOT);
    }
}

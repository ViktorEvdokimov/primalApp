package com.primal.identity;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Короткая подпись браузера для списка устройств: «Chrome, Android». */
final class UserAgents {

    static final String UNKNOWN = "Неизвестный браузер";

    /** Порядок важен: Edge, Opera и Яндекс Браузер тоже называют себя Chrome и Safari. */
    private static final List<Map.Entry<Pattern, String>> BROWSERS = List.of(
            Map.entry(Pattern.compile("YaBrowser/"), "Яндекс Браузер"),
            Map.entry(Pattern.compile("Edg(e|A|iOS)?/"), "Edge"),
            Map.entry(Pattern.compile("OPR/|Opera"), "Opera"),
            Map.entry(Pattern.compile("SamsungBrowser/"), "Samsung Internet"),
            Map.entry(Pattern.compile("Firefox/|FxiOS/"), "Firefox"),
            Map.entry(Pattern.compile("Chrome/|CriOS/"), "Chrome"),
            Map.entry(Pattern.compile("Safari/"), "Safari"));

    private static final List<Map.Entry<Pattern, String>> SYSTEMS = List.of(
            Map.entry(Pattern.compile("Android"), "Android"),
            Map.entry(Pattern.compile("iPhone|iPad|iPod"), "iOS"),
            Map.entry(Pattern.compile("Windows"), "Windows"),
            Map.entry(Pattern.compile("Mac OS X|Macintosh"), "macOS"),
            Map.entry(Pattern.compile("Linux"), "Linux"));

    private UserAgents() {
    }

    static String shortName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String browser = find(BROWSERS, userAgent);
        String system = find(SYSTEMS, userAgent);
        if (browser == null) {
            return system == null ? UNKNOWN : system;
        }
        return system == null ? browser : browser + ", " + system;
    }

    private static String find(List<Map.Entry<Pattern, String>> candidates, String userAgent) {
        return candidates.stream()
                .filter(candidate -> candidate.getKey().matcher(userAgent).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}

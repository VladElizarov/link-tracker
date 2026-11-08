package ru.tbank.bot.util;

import java.util.ArrayList;
import java.util.List;

public final class TextUtils {

    public static final String SKIP = "-";

    private TextUtils() {
    }

    public static List<String> parseCommaSeparated(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().equals(SKIP)) {
            return result;
        }
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public static String argumentsOf(String text) {
        String trimmed = text.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex == -1) {
            return "";
        }
        return trimmed.substring(spaceIndex + 1).trim();
    }
}

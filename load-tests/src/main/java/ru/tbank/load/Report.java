package ru.tbank.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Report {

    private Report() {
    }

    public static String build(
        String label,
        String baseUrl,
        int threads,
        int rampUpSec,
        int stageSec,
        int chats,
        Map<String, Stats> stats,
        List<String> order
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(label).append("\n\n");
        sb.append(String.format(Locale.ROOT,
            "Конфигурация: %s, %d параллельных пользователей, ramp-up %d с, stage %d с, "
                + "%d чатов x 100 ссылок = %d ссылок в БД, 1 изменение на %d чтений.%n%n",
            baseUrl, threads, rampUpSec, stageSec, chats, chats * 100, 100));
        sb.append("| Запрос | Кол-во | RPS | Среднее, мс | p50, мс | p99, мс | 200 | 502/504 | 500 | Другие | Частые ошибки |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String name : order) {
            Stats s = stats.get(name);
            if (s == null) {
                continue;
            }
            sb.append(String.format(Locale.ROOT, "| %s | %d | %.1f | %.2f | %.2f | %.2f | %d | %d | %d | %d | %s |%n",
                name, s.count(), s.count() / (double) stageSec, s.averageMillis(),
                s.percentileMillis(50), s.percentileMillis(99),
                s.ok(), s.gateway(), s.serverError(), s.other(), topErrors(s)));
        }
        return sb.toString();
    }

    private static String topErrors(Stats stats) {
        if (stats.errors().isEmpty()) {
            return "нет";
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(stats.errors().entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, entries.size()); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            String text = entries.get(i).getKey().replace("|", "/");
            if (text.length() > 60) {
                text = text.substring(0, 60) + "...";
            }
            sb.append(text).append(" (").append(entries.get(i).getValue()).append(")");
        }
        return sb.toString();
    }
}

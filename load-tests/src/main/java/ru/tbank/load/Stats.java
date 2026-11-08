package ru.tbank.load;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Stats {

    private long[] times = new long[1024];
    private int count;
    private long ok;
    private long gateway;
    private long serverError;
    private long other;
    private final Map<String, Long> errors = new HashMap<>();

    public void add(long micros, int status, String outcome) {
        if (count == times.length) {
            times = Arrays.copyOf(times, count * 2);
        }
        times[count++] = micros;
        if (status == 200) {
            ok++;
            return;
        }
        if (status == 502 || status == 504) {
            gateway++;
        } else if (status == 500) {
            serverError++;
        } else {
            other++;
        }
        errors.merge(outcome, 1L, Long::sum);
    }

    public void merge(Stats another) {
        for (int i = 0; i < another.count; i++) {
            if (count == times.length) {
                times = Arrays.copyOf(times, count * 2);
            }
            times[count++] = another.times[i];
        }
        ok += another.ok;
        gateway += another.gateway;
        serverError += another.serverError;
        other += another.other;
        another.errors.forEach((key, value) -> errors.merge(key, value, Long::sum));
    }

    public int count() {
        return count;
    }

    public double averageMillis() {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += times[i];
        }
        return count == 0 ? 0 : sum / 1000.0 / count;
    }

    public double percentileMillis(double percent) {
        if (count == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(times, count);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percent / 100.0 * count) - 1;
        return sorted[Math.max(0, index)] / 1000.0;
    }

    public long ok() {
        return ok;
    }

    public long gateway() {
        return gateway;
    }

    public long serverError() {
        return serverError;
    }

    public long other() {
        return other;
    }

    public Map<String, Long> errors() {
        return errors;
    }
}

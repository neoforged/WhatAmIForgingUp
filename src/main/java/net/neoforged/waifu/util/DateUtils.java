package net.neoforged.waifu.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Some utility methods for working with time.
 */
public class DateUtils {
    private static final TemporalUnit MONTHS = exactDays(30);
    private static final TemporalUnit YEARS = exactDays(365);

    /**
     * Decodes a duration from an {@code input}, supporting multiple time specifiers (e.g. {@code 1w2d}).
     */
    public static Duration getDurationFromInput(String input) {
        final List<String> data = splitInput(input);
        Duration duration = Duration.ofSeconds(0);
        for (final String dt : data) {
            if (dt.charAt(0) == '-') {
                duration = duration.minusSeconds(decode(dt.substring(1)).toSeconds());
            } else {
                duration = duration.plusSeconds(decode(dt).toSeconds());
            }
        }
        return duration;
    }

    private static List<String> splitInput(String str) {
        final var list = new ArrayList<String>();
        var builder = new StringBuilder();
        for (final var ch : str.toCharArray()) {
            builder.append(ch);
            if (ch != '-' && !Character.isDigit(ch)) {
                list.add(builder.toString());
                builder = new StringBuilder();
            }
        }
        return list;
    }

    /**
     * Decodes time from a string.
     *
     * @param time the time to decode
     * @return the decoded time.
     */
    public static Duration decode(final String time) {
        final var unit = switch (time.charAt(time.length() - 1)) {
            case 'n' -> ChronoUnit.NANOS;
            case 'l' -> ChronoUnit.MILLIS;
            case 's' -> ChronoUnit.SECONDS;
            case 'h' -> ChronoUnit.HOURS;
            case 'd' -> ChronoUnit.DAYS;
            case 'w' -> ChronoUnit.WEEKS;
            case 'M' -> MONTHS;
            case 'y' -> YEARS;
            default -> ChronoUnit.MINUTES;
        };
        final long tm = Long.parseLong(time.substring(0, time.length() - 1));
        return of(tm, unit);
    }

    /**
     * An alternative of {@link Duration#of(long, TemporalUnit)} that can handle estimated durations,
     * by using their estimated seconds count.
     */
    public static Duration of(long time, TemporalUnit unit) {
        return unit.isDurationEstimated() ? Duration.ofSeconds(time * unit.getDuration().getSeconds()) : Duration.of(time, unit);
    }

    private static TemporalUnit exactDays(long amount) {
        var dur = Duration.ofDays(amount);
        return new TemporalUnit() {
            @Override
            public Duration getDuration() {
                return dur;
            }

            @Override
            public boolean isDurationEstimated() {
                return false;
            }

            @Override
            public boolean isDateBased() {
                return false;
            }

            @Override
            public boolean isTimeBased() {
                return false;
            }

            @Override
            public boolean isSupportedBy(Temporal temporal) {
                return temporal.isSupported(this);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <R extends Temporal> R addTo(R temporal, long amount) {
                return (R) temporal.plus(amount, this);
            }

            @Override
            public long between(Temporal temporal1Inclusive, Temporal temporal2Exclusive) {
                return temporal1Inclusive.until(temporal2Exclusive, this);
            }
        };
    }
}

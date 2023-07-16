/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;

import java.util.Map;

public class DiscordLogbackLayout extends LayoutBase<ILoggingEvent> {
    private static final String UNKNOWN_EMOTE = ":radio_button:";

    public static final Map<Level, String> LEVEL_TO_EMOTE = Map.of(
            Level.ERROR, ":red_square:",
            Level.WARN, ":yellow_circle:",
            Level.INFO, ":white_medium_small_square:",
            Level.DEBUG, ":large_blue_diamond:",
            Level.TRACE, ":small_orange_diamond:"
    );

    private static final int MAXIMUM_STACKTRACE_LENGTH = 1750;

    @Override
    public String doLayout(final ILoggingEvent event) {
        final StringBuilder builder = new StringBuilder(2000);
        builder.append(LEVEL_TO_EMOTE.getOrDefault(event.getLevel(), UNKNOWN_EMOTE))
                .append(" [**")
                .append(event.getLoggerName());

        if (event.getMarkerList() != null && !event.getMarkerList().isEmpty()) {
            builder.append("**/**")
                    .append(event.getMarkerList().get(0).getName());
        }

        builder.append("**] - ")
                .append(event.getFormattedMessage())
                .append(System.lineSeparator());

        if (event.getThrowableProxy() != null) {
            final IThrowableProxy t = event.getThrowableProxy();
            builder.append(t.getClassName())
                    .append(": ")
                    .append(t.getMessage())
                    .append(System.lineSeparator());

            final StringBuilder stacktrace = buildStacktrace(t);
            String stacktraceCutoff = null;
            builder.append("Stacktrace: ");
            if (stacktrace.length() > MAXIMUM_STACKTRACE_LENGTH) {
                stacktraceCutoff = stacktrace.substring(MAXIMUM_STACKTRACE_LENGTH, stacktrace.length());
                stacktrace.delete(MAXIMUM_STACKTRACE_LENGTH, stacktrace.length());
            }

            builder.append(System.lineSeparator())
                    .append("```ansi")
                    .append(System.lineSeparator())
                    .append(stacktrace)
                    .append("```");

            if (stacktraceCutoff != null) {
                builder.append("*Too long to fully display. ")
                        .append(stacktraceCutoff.length())
                        .append(" characters or ")
                        .append(stacktraceCutoff.lines().count())
                        .append(" lines were truncated.*");
            }
        }
        return builder.toString();
    }

    private StringBuilder buildStacktrace(IThrowableProxy exception) {
        final StringBuilder builder = new StringBuilder();
        for (final StackTraceElementProxy element : exception.getStackTraceElementProxyArray()) {
            builder.append("\t ").append(element.toString())
                    .append(System.lineSeparator());
        }
        return builder;
    }
}
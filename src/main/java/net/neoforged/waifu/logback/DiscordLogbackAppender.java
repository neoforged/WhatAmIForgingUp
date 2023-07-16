/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

public class DiscordLogbackAppender extends AppenderBase<ILoggingEvent> {

    private Layout<ILoggingEvent> layout;

    private MessageChannel channel;

    public void setup(Layout<ILoggingEvent> layout, MessageChannel channel) {
        this.layout = layout;
        this.channel = channel;
    }

    public static void setup(@Nullable MessageChannel channel) throws ClassCastException {
        if (channel == null) return;

        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final DiscordLogbackLayout layout = new DiscordLogbackLayout();
        layout.setContext(context);
        layout.start();

        final ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        ((DiscordLogbackAppender) rootLogger.getAppender("DISCORD")).setup(layout, channel);
    }

    @Override
    protected void append(final ILoggingEvent eventObject) {
        if (channel == null) return;
        channel.sendMessage(getMessageContent(eventObject))
                .setAllowedMentions(List.of())
                .queue();
    }

    protected String getMessageContent(final ILoggingEvent event) {
        return layout != null ? layout.doLayout(event) : event.getFormattedMessage();
    }
}

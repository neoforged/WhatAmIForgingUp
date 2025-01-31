package net.neoforged.waifu.discord;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

public record FilteredCommandClient(EventListener client) implements EventListener {
    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event.getClass() != MessageReceivedEvent.class) {
            client.onEvent(event);
        }
    }
}

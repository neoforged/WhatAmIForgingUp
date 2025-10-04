package net.neoforged.waifu.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ComponentManager implements EventListener {
    private final String prefix = "cm/" + UUID.randomUUID() + "/";

    private final Cache<String, Consumer<GenericInteractionCreateEvent>> components = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    public Button button(ButtonStyle style, String label, Consumer<ButtonInteractionEvent> consumer) {
        var id = "b/" + UUID.randomUUID();
        components.put(id, (Consumer)consumer);
        return Button.of(style, prefix + id, label);
    }

    public Modal.Builder modal(String title, Consumer<ModalInteractionEvent> consumer) {
        var id = "m/" + UUID.randomUUID();
        components.put(id, (Consumer)consumer);
        return Modal.create(prefix + id, title);
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        String id = switch (event) {
            case GenericComponentInteractionCreateEvent ev when ev.getComponentId().startsWith(prefix) ->
                ev.getComponentId().substring(prefix.length());
            case ModalInteractionEvent ev when ev.getModalId().startsWith(prefix) ->
                ev.getModalId().substring(prefix.length());
            default -> null;
        };

        if (id != null) {
            var cons = components.getIfPresent(id);
            if (cons != null) {
                cons.accept((GenericInteractionCreateEvent) event);
            }
        }
    }
}

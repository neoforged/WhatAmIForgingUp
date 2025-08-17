package net.neoforged.waifu.discord;

import com.google.common.primitives.Ints;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.neoforged.waifu.web.WebService;
import net.neoforged.waifu.web.api.TokenManager;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TokensCommand extends SlashCommand {
    private final TokenManager manager;
    private final ComponentManager components;

    public TokensCommand(TokenManager manager, ComponentManager components) {
        this.components = components;
        this.name = "tokens";
        this.help = "Manage tokens used to access the WAIFU API";

        this.manager = manager;
        this.children = new SlashCommand[] {
                new Create(), new Manage(), new Revoke(), new ListCmd()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    public class Create extends SlashCommand {
        public Create() {
            this.name = "create";
            this.help = "Create a new API token which will be inactive by default. You may manage it after creation";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "name", "The name used to identify the token", true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var name = event.optString("name");
            if (manager.getToken(name) != null) {
                event.reply("A token with this name already exists!")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            var token = manager.createToken(name);
            event.replyComponents(createTokenManagement(token))
                    .useComponentsV2()
                    .setEphemeral(true)
                    .queue();
        }
    }

    public class Manage extends SlashCommand {
        public Manage() {
            this.name = "manage";
            this.help = "Manage an API token";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "name", "The name used to identify the token", true)
                            .setAutoComplete(true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var name = event.optString("name");
            var token = manager.getToken(name);
            if (token == null) {
                event.reply("Unknown token `" + name + "`!")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            event.replyComponents(createTokenManagement(token))
                    .useComponentsV2()
                    .setEphemeral(true)
                    .queue();
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            var cur = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
            event.replyChoices(manager.getTokens()
                            .stream()
                            .filter(t -> t.name().toLowerCase(Locale.ROOT).startsWith(cur))
                            .map(t -> new Command.Choice(t.name(), t.name()))
                            .limit(OptionData.MAX_CHOICES)
                            .toList())
                    .queue();
        }
    }

    public class Revoke extends SlashCommand {
        public Revoke() {
            this.name = "revoke";
            this.help = "Revoke an API token";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "name", "The name of the token to revoke", true)
                            .setAutoComplete(true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var name = event.optString("name");
            if (manager.removeToken(name)) {
                event.reply("Token revoked successfully!").setEphemeral(true).queue();
            } else {
                event.reply("Unknown token with name `" + name + "`!").setEphemeral(true).queue();
            }
        }

        @Override
        public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
            var cur = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
            event.replyChoices(manager.getTokens()
                            .stream()
                            .filter(t -> t.name().toLowerCase(Locale.ROOT).startsWith(cur))
                            .map(t -> new Command.Choice(t.name(), t.name()))
                            .limit(OptionData.MAX_CHOICES)
                            .toList())
                    .queue();
        }
    }

    public class ListCmd extends SlashCommand {
        public ListCmd() {
            this.name = "list";
            this.help = "List known API tokens";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var tokens = manager.getTokens();
            var embed = new EmbedBuilder().setTitle("Current API tokens")
                    .setTimestamp(Instant.now());

            var desc = tokens.stream()
                    .map(t -> {
                        var str = new StringBuilder("- `" + t.name() + "`");
                        if (t.limit() == null) {
                            str.append(" (unlimited)");
                        } else {
                            str.append(" (").append(t.limit()).append(')');
                        }
                        return str.toString();
                    })
                    .collect(Collectors.joining("\n"));

            embed.setDescription(desc);

            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }

    private List<MessageTopLevelComponent> createTokenManagement(TokenManager.Token token) {
        return List.of(
                Container.of(
                        TextDisplay.of("### Manage token `" + token.name() + "`"),
                        Section.of(
                                components.button(
                                        token.active() ? ButtonStyle.DANGER : ButtonStyle.SUCCESS,
                                        token.active() ? "Deactivate" : "Activate",
                                        flipActive(token)
                                ).withDisabled(token.tokenLastGenerated() == null),
                                TextDisplay.of("Status: **" + (token.active() ? "active" : "inactive") + "**\n" +
                                        (token.tokenLastGenerated() == null ? "You must generate the token before it can be activated" : "Inactive tokens cannot be used until they're activated")
                                )
                        ),
                        Section.of(
                                components.button(
                                        token.tokenLastGenerated() == null ? ButtonStyle.PRIMARY : ButtonStyle.DANGER,
                                        token.tokenLastGenerated() == null ? "Generate" : "Regenerate",
                                        generate(token)
                                ),
                                TextDisplay.of(token.tokenLastGenerated() == null ? "Token is yet to be generated" : "API token was last generated " + TimeFormat.RELATIVE.format(token.tokenLastGenerated()))
                        ),
                        Section.of(
                                components.button(
                                        ButtonStyle.SECONDARY,
                                        "Update rate limit",
                                        updateRateLimit(token)
                                ),
                                TextDisplay.of("Rate limit: " + (token.limit() == null ? "**none**" : token.limit().toString()))
                        ),
                        Section.of(
                                components.button(
                                        ButtonStyle.SECONDARY,
                                        "Update execution timeout",
                                        updateTimeout(token)
                                ),
                                TextDisplay.of("Execution timeout: " + (
                                        token.executionTimeout() == null ? "*default* (" + WebService.getDefaultTimeout() + " seconds)" : (token.executionTimeout() <= 0 ? "**none**" : token.executionTimeout() + " seconds")
                                ))
                        )
                )
        );
    }

    private Consumer<ButtonInteractionEvent> flipActive(TokenManager.Token token) {
        return event -> {
            updatePanel(event, manager.update(token)
                    .setActive(!token.active())
                    .execute());
            event.deferEdit().queue();
        };
    }

    private Consumer<ButtonInteractionEvent> generate(TokenManager.Token token) {
        return event -> {
            var regenerated = manager.regenerate(token.name());
            updatePanel(event, regenerated, TextDisplay.of("Token regenerated successfully: `" + regenerated.token() + "`! You will not be able to see this again without regenerating the token!"));
            event.deferEdit().queue();
        };
    }

    private Consumer<ButtonInteractionEvent> updateRateLimit(TokenManager.Token token) {
        return topEvent -> {
            topEvent.replyModal(components.modal("Update token rate limit", event -> {
                                var input = event.getValue("limit").getAsString();

                                TokenManager.RateLimit rateLimit = null;

                                if (!input.isBlank()) {
                                    try {
                                        rateLimit = TokenManager.RateLimit.parse(input);
                                    } catch (Exception exception) {
                                        event.reply("Invalid rate limit provided: `" + input + "`").setEphemeral(true).queue();
                                        return;
                                    }
                                }

                                updatePanel(topEvent, manager.update(token)
                                        .setRateLimit(rateLimit)
                                        .execute());
                                event.deferEdit().queue();
                            })
                            .addComponents(ActionRow.of(
                                    TextInput.create("limit", "Rate limit", TextInputStyle.PARAGRAPH)
                                            .setRequired(false)
                                            .setPlaceholder("Example: 12/10m (12 requests every 10 minutes).\nLeave empty to remove rate limit.")
                                            .build()
                            ))
                            .build())
                    .queue();
        };
    }

    private Consumer<ButtonInteractionEvent> updateTimeout(TokenManager.Token token) {
        return topEvent -> {
            topEvent.replyModal(components.modal("Update token execution timeout", event -> {
                                var input = event.getValue("timeout").getAsString();
                                var timeout = input.isBlank() ? null : Ints.tryParse(input);
                                updatePanel(topEvent, manager.update(token)
                                        .setTimeout(timeout)
                                        .execute());
                                event.deferEdit().queue();
                            })
                            .addComponents(ActionRow.of(
                                    TextInput.create("timeout", "Execution timeout", TextInputStyle.PARAGRAPH)
                                            .setRequired(false)
                                            .setPlaceholder("Execution timeout in seconds.\nZero is interpreted as indefinite.\nLeave empty to set to default.")
                                            .build()
                            ))
                            .build())
                    .queue();
        };
    }

    private void updatePanel(ButtonInteractionEvent event, TokenManager.Token newToken, MessageTopLevelComponent... extraComponents) {
        var elements = new ArrayList<>(createTokenManagement(newToken));
        elements.addAll(Arrays.asList(extraComponents));
        event.getInteraction().getHook()
                .editOriginalComponents(elements)
                .useComponentsV2()
                .queue();
    }
}

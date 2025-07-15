package net.neoforged.waifu.discord;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.waifu.web.api.TokenManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TokensCommand extends SlashCommand {
    private final TokenManager manager;
    public TokensCommand(TokenManager manager) {
        this.name = "tokens";
        this.help = "Manage tokens used to access the WAIFU API";

        this.manager = manager;
        this.children = new SlashCommand[] {
                new Create(), new Revoke(), new Regenerate(), new ListCmd()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    public class Create extends SlashCommand {
        public Create() {
            this.name = "create";
            this.help = "Create a new API token";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "name", "The name used to identify the token", true),
                    new OptionData(OptionType.STRING, "ratelimit", "An optional rate limit for the token. Example: 12/10m (12 requests every 10 minutes)", false),
                    new OptionData(OptionType.INTEGER, "timeout", "An optional timeout in seconds the token will have. This will override the default timeout.", false)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var name = event.optString("name");
            var token = manager.createToken(name, event.optString("ratelimit"), event.getOption("timeout", OptionMapping::getAsInt));
            event.reply("Token with name `" + name + "` generated!\nThe token is `" + token + "`. You won't be able to see it again.")
                    .setEphemeral(true).queue();
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

    public class Regenerate extends SlashCommand {
        public Regenerate() {
            this.name = "regenerate";
            this.help = "Regenerate an API token";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "name", "The name of the token to regenerate", true)
                            .setAutoComplete(true)
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            var name = event.optString("name");
            var regen = manager.regenerate(name);
            if (regen == null) {
                event.reply("Unknown token with name `" + name + "`!").setEphemeral(true).queue();
            } else {
                event.reply("Token regenerated successfully: `" + regen + "`!").setEphemeral(true).queue();
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
}

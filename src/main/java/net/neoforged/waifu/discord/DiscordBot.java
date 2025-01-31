package net.neoforged.waifu.discord;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.waifu.GameVersionIndexService;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.ModIndexer;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Counter;
import net.neoforged.waifu.util.ProgressMonitor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DiscordBot implements GameVersionIndexService.ListenerFactory {

    private final JDA jda;
    private final long channelId;
    private final ScheduledExecutorService messageUpdateService;
    private final MainDatabase database;

    public DiscordBot(String token, MainDatabase database) throws InterruptedException {
        this.database = database;

        this.jda = JDABuilder.createLight(token)
                .addEventListeners(new FilteredCommandClient((EventListener) createCommandClient()))
                .build();
        jda.awaitReady();

        channelId = Long.parseLong(System.getProperty("discord.channel.id"));
        messageUpdateService = Executors.newScheduledThreadPool(3, Thread.ofVirtual().name("discord-update-service-", 0).factory());
    }

    private CommandClient createCommandClient() {
        var builder = new CommandClientBuilder();
        builder.setOwnerId("0");
        builder.setActivity(Activity.of(Activity.ActivityType.WATCHING, "naughty modders"));
        builder.addSlashCommand(new SlashCommand() {
            {
                name = "index-version";
                help = "Add a version to be indexed";
                options = List.of(
                        new OptionData(OptionType.STRING, "version", "The version to index")
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                var version = event.optString("version", "");
                database.addGameVersion(version);
                event.reply("Started indexing version `" + version + "`")
                        .setEphemeral(true)
                        .queue();

                Main.schedule(version, DiscordBot.this, 30);
            }
        });
        return builder.build();
    }

    private MessageChannel getChannel() {
        return jda.getChannelById(MessageChannel.class, channelId);
    }

    @Override
    public void informError(String error) {
        getChannel().sendMessage(error).queue();
    }

    @Override
    public GameVersionIndexService.Listener startIndexingListener(String gameVersion, ModPlatform platform) {
        var message = getChannel().sendMessage("Started indexing game version `" + gameVersion + "`, platform " + platform.getName() + "...").complete();
        var start = Instant.now();
        class Listener implements GameVersionIndexService.Listener, Runnable {
            Future<?> task;

            @Override
            public void run() {
                editMessage(embed -> {
                    if (success) {
                        embed.addField("Step", "Success", false);
                        embed.setDescription(indexed.get() + " mods indexed (JiJ included).");

                        if (failed.get() != 0) {
                            embed.appendDescription("\n**" + failed.get() + " failures**. Check console for more information.");
                        }
                    } else if (startedIndex) {
                        embed.addField("Step", "Indexing mods", false);
                        embed.addField("Found mods", currentCounter.getAmount() + " mods found", false);
                        embed.appendDescription("Indexed: %s/%s".formatted(indexed.get(), expected.get()));
                        embed.appendDescription("Stored: %s/%s".formatted(stored.get(), expected.get()));

                        if (failed.get() != 0) {
                            embed.appendDescription("Failed: %s".formatted(failed.get()));
                        }
                    } else if (currentCounter != null) {
                        embed.addField("Step", "Searching mods", false);
                        embed.addField("Found mods", currentCounter.getAmount() + " mods currently found", false);

                        embed.appendDescription("Last 5 found mods:\n");
                        for (PlatformModFile element : currentCounter.getElements()) {
                            embed.appendDescription("- " + element.getUrl() + "\n");
                        }
                    }
                });
            }

            private volatile Counter<PlatformModFile> currentCounter;

            private volatile boolean startedIndex, success;

            private final AtomicInteger expected = new AtomicInteger(), indexed = new AtomicInteger(), stored = new AtomicInteger(), failed = new AtomicInteger();

            @Override
            public ProgressMonitor<ModIndexer.IndexCandidate> startIndex() {
                startedIndex = true;
                expected.set(0);
                indexed.set(0);
                stored.set(0);
                failed.set(0);
                return new ProgressMonitor<>() {
                    @Override
                    public void setExpected(List<ModIndexer.IndexCandidate> elements) {
                        expected.set(elements.size());
                    }

                    @Override
                    public void markAsIndexed(ModIndexer.IndexCandidate element) {
                        indexed.incrementAndGet();
                    }

                    @Override
                    public void markAsStored(ModIndexer.IndexCandidate element) {
                        stored.incrementAndGet();
                    }

                    @Override
                    public void raiseError(ModIndexer.IndexCandidate element, Throwable exception) {
                        Main.LOGGER.error("Error indexing candidate {}:", element.file().getDisplayName() + (element.platformFile() != null ? element.platformFile().getUrl() : ""), exception);
                        failed.incrementAndGet();
                    }
                };
            }

            @Override
            public Counter<PlatformModFile> startPlatformScan(ModPlatform platform) {
                return currentCounter = new Counter<>(new AtomicInteger(), new PlatformModFile[5]);
            }

            @Override
            public void markFinish(int scanned) {
                task.cancel(true);
                indexed.set(scanned);
                success = true;
                run();
            }

            @Override
            public void raiseFatalException(Exception exception) {
                task.cancel(true);

                editMessage(embed -> embed.setDescription("Fatal failure, check console for more details: **" + exception.getMessage() + "**"));
            }

            private void editMessage(Consumer<EmbedBuilder> consumer) {
                var embed = new EmbedBuilder();
                embed.setTitle("Indexing version `" + gameVersion + "`, platform " + platform.getName());

                embed.setTimestamp(start);

                var secs = Instant.now().getEpochSecond() - start.getEpochSecond();

                embed.setFooter("Time elapsed: " + secs / 60 + " minutes and " + (secs % 60) + " seconds");

                consumer.accept(embed);

                message.editMessage(MessageEditData.fromEmbeds(embed.build())).complete();
            }
        }

        var list = new Listener();
        list.task = messageUpdateService.scheduleWithFixedDelay(list, 5, 10, TimeUnit.SECONDS);
        return list;
    }
}

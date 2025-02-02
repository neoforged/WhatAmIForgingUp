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
import net.dv8tion.jda.api.interactions.commands.Command;
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
import net.neoforged.waifu.util.Utils;

import java.awt.Color;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
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

        channelId = Long.parseLong(System.getenv("DISCORD_CHANNEL_ID"));
        messageUpdateService = Executors.newScheduledThreadPool(3, Thread.ofVirtual().name("discord-update-service-", 0).factory());

        var commits = Utils.getCommits();
        if (commits.isEmpty()) {
            getChannel().sendMessage("Hello world, WAIFU is available again!").queue();
        } else {
            getChannel().sendMessage("Hello world, WAIFU is available again! Latest commit: " + commits.get(0).getDiscordReference()).queue();
        }

        Runtime.getRuntime().addShutdownHook(new Thread("discord-shutdown") {
            @Override
            public void run() {
                getChannel().sendMessage("Bye!").complete();
            }
        });
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
                        new OptionData(OptionType.STRING, "version", "The version to index", true)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                var version = event.optString("version", "");
                database.addGameVersion(version);
                event.reply("Started indexing version `" + version + "`").queue();

                Main.schedule(version, DiscordBot.this, 30);
            }
        });
        builder.addSlashCommand(new SlashCommand() {
            {
                name = "index-files";
                help = "Force a list of files to be indexed";
                options = List.of(
                        new OptionData(OptionType.STRING, "version", "The game version of the files", true),
                        new OptionData(OptionType.STRING, "platform", "The platform of the files", true)
                                .addChoices(Main.PLATFORMS.stream().map(p -> new Command.Choice(p.getName(), p.getName())).toList()),
                        new OptionData(OptionType.STRING, "files", "Comma-separated files to index", true)
                );
            }
            @Override
            protected void execute(SlashCommandEvent event) {
                var platformName = event.optString("platform");
                ModPlatform platform = Main.PLATFORMS.stream().filter(p -> p.getName().equals(platformName))
                        .findFirst().orElseThrow();

                event.reply("Started manual index...").complete();

                var fileIds = Arrays.stream(event.optString("files", "").split(","))
                        .map(s -> (Object) s.trim()).toList();
                var files = platform.getFiles(fileIds);
                platform.bulkFillData(files);

                var indexer = new ModIndexer<>(Main.PLATFORM_CACHE, Main.createDatabase(event.optString("version")));
                var counter = new Counter<>(new AtomicInteger(), new PlatformModFile[5]);
                try (var exec = Executors.newFixedThreadPool(10, Thread.ofVirtual().name("mod-downloader-manual-", 0).factory())) {
                    indexer.downloadAndConsiderConcurrently(files, exec, counter);
                }

                var scanned = indexer.index(platform, GameVersionIndexService.VIRTUAL_THREAD_EXECUTOR, GameVersionIndexService.CONCURRENCY, new ProgressMonitor<>() {
                    @Override
                    public void setExpected(List<ModIndexer.IndexCandidate> elements) {

                    }

                    @Override
                    public void unexpect(ModIndexer.IndexCandidate element) {

                    }

                    @Override
                    public void markAsIndexed(ModIndexer.IndexCandidate element) {

                    }

                    @Override
                    public void markAsStored(ModIndexer.IndexCandidate element) {

                    }

                    @Override
                    public void raiseError(ModIndexer.IndexCandidate element, Throwable exception) {
                        Main.LOGGER.error("Error indexing candidate {}:", element.file().getDisplayName() + (element.platformFile() != null ? " " + element.platformFile().getUrl() : ""), exception);
                    }
                }, Main.SANITIZER);

                for (ModIndexer.IndexCandidate indexCandidate : scanned) {
                    try {
                        indexCandidate.file().close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                event.getHook().editOriginal("Manual index successful. Indexed " + scanned.size() + " mods!").complete();
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
                        embed.setDescription(indexed.get() + " mods indexed (JiJ included).\nLast indexed mods:\n");

                        printToEmbed(stored, embed);

                        if (failed.getAmount() != 0) {
                            embed.appendDescription("\n**" + failed.getAmount() + " failures**. Check console for more information.");
                            printToEmbed(failed, embed);
                            embed.setColor(Color.RED);
                        } else {
                            embed.setColor(Color.GREEN);
                        }
                    } else if (startedIndex) {
                        embed.addField("Step", "Indexing mods", false);
                        embed.addField("Found mods", searchCounter.getAmount() + " mods found", false);
                        embed.appendDescription("Indexed: %s/%s\n".formatted(indexed.get(), expected.get()));
                        embed.appendDescription("Stored: %s/%s\n".formatted(stored.getAmount(), expected.get()));

                        embed.appendDescription("Last stored mods:\n");
                        printToEmbed(stored, embed);

                        if (failed.getAmount() != 0) {
                            embed.appendDescription("\nFailed: %s".formatted(failed.getAmount()));
                        }
                    } else if (downloadCounter != null) {
                        embed.addField("Step", "Downloading mods", false);
                        embed.addField("Downloaded mods", downloadCounter.getAmount() + "/" + searchCounter.getAmount() + " mods currently downloaded", false);

                        embed.appendDescription("Last 5 downloaded mods:\n");
                        for (PlatformModFile element : downloadCounter.getElements()) {
                            if (element != null) {
                                embed.appendDescription("- " + element.getUrl() + "\n");
                            }
                        }
                    } else if (searchCounter != null) {
                        embed.addField("Step", "Searching mods", false);
                        embed.addField("Found mods", searchCounter.getAmount() + " mods currently found", false);

                        embed.appendDescription("Last 5 found mods:\n");
                        for (PlatformModFile element : searchCounter.getElements()) {
                            if (element != null) {
                                embed.appendDescription("- " + element.getUrl() + "\n");
                            }
                        }
                    }
                });
            }

            private void printToEmbed(Counter<ModIndexer.IndexCandidate> counter, EmbedBuilder embed) {
                for (ModIndexer.IndexCandidate element : counter.getElements()) {
                    if (element != null) {
                        var text = "`" + element.file().getDisplayName() + "`";
                        if (element.platformFile() != null) {
                            text = "[" + text + "](" + element.platformFile().getUrl() + ")";
                        }
                        embed.appendDescription("- " + text + "\n");
                    }
                }
            }

            private volatile Counter<PlatformModFile> searchCounter;
            private volatile Counter<PlatformModFile> downloadCounter;

            private volatile boolean startedIndex, success;

            private final AtomicInteger expected = new AtomicInteger(), indexed = new AtomicInteger();
            private final Counter<ModIndexer.IndexCandidate> failed = new Counter<>(new AtomicInteger(), new ModIndexer.IndexCandidate[10]);
            private final Counter<ModIndexer.IndexCandidate> stored = new Counter<>(new AtomicInteger(), new ModIndexer.IndexCandidate[15]);

            @Override
            public ProgressMonitor<ModIndexer.IndexCandidate> startIndex() {
                startedIndex = true;
                expected.set(0);
                indexed.set(0);
                return new ProgressMonitor<>() {
                    @Override
                    public void setExpected(List<ModIndexer.IndexCandidate> elements) {
                        expected.set(elements.size());
                    }

                    @Override
                    public void unexpect(ModIndexer.IndexCandidate element) {
                        expected.decrementAndGet();
                    }

                    @Override
                    public void markAsIndexed(ModIndexer.IndexCandidate element) {
                        indexed.incrementAndGet();
                    }

                    @Override
                    public void markAsStored(ModIndexer.IndexCandidate element) {
                        stored.add(element);
                    }

                    @Override
                    public void raiseError(ModIndexer.IndexCandidate element, Throwable exception) {
                        Main.LOGGER.error("Error indexing candidate {}:", element.file().getDisplayName() + (element.platformFile() != null ? " " + element.platformFile().getUrl() : ""), exception);
                        failed.add(element);
                    }
                };
            }

            @Override
            public Counter<PlatformModFile> startPlatformScan() {
                return searchCounter = new Counter<>(new AtomicInteger(0), new PlatformModFile[5]);
            }

            @Override
            public Counter<PlatformModFile> startDownload() {
                return downloadCounter = new Counter<>(new AtomicInteger(0), new PlatformModFile[5]);
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
                embed.setAuthor(platform.getName(), null, platform.getLogoUrl());

                embed.setTimestamp(start);

                var secs = Instant.now().getEpochSecond() - start.getEpochSecond();

                embed.setFooter("Time elapsed: " + secs / 60 + " minutes and " + (secs % 60) + " seconds");

                consumer.accept(embed);

                message.editMessage(MessageEditData.fromEmbeds(embed.build())).setContent(null).complete();
            }
        }

        var list = new Listener();
        list.task = messageUpdateService.scheduleWithFixedDelay(list, 5, 10, TimeUnit.SECONDS);
        return list;
    }
}

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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.waifu.GameVersionIndexService;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.ModIndexer;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformModFile;
import net.neoforged.waifu.util.Counter;
import net.neoforged.waifu.util.ProgressMonitor;
import net.neoforged.waifu.util.Utils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.plexus.util.StringUtils;

import java.awt.Color;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiscordBot implements GameVersionIndexService.ListenerFactory {
    private static final List<Command.Choice> LOADERS = Arrays.stream(ModLoader.values())
            .map(l -> new Command.Choice(StringUtils.capitalise(l.name().toLowerCase(Locale.ROOT)), l.name()))
            .toList();

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

        var message = new StringBuilder("Hello world, WAIFU is available again");
        var versions = database.getIndexedGameVersions();
        if (!versions.isEmpty()) {
            message.append(" and indexing versions ")
                    .append(versions.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        }
        message.append("! ");

        var commits = Utils.getCommits();
        if (!commits.isEmpty()) {
            message.append("Latest commit: ").append(commits.get(0).getDiscordReference());
        }

        getChannel().sendMessage(message.toString()).queue();

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

        var trackVersionCommand = new SlashCommand() {
            {
                name = "track";
                help = "Add a version to be tracked and indexed";
                options = List.of(
                        new OptionData(OptionType.STRING, "version", "The version to index", true),
                        new OptionData(OptionType.STRING, "loader", "The loader to index", true)
                                .addChoices(LOADERS)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                var version = event.optString("version", "");
                var loader = ModLoader.valueOf(event.optString("loader"));
                database.addGameVersion(version, loader);
                event.reply("Started indexing version `" + version + "`").queue();

                Main.schedule(version, loader, DiscordBot.this, 30);
            }
        };

        var untrackVersionCommand = new SlashCommand() {
            {
                name = "untrack";
                help = "Untrack a version to stop it from being idexed";
                options = List.of(
                        new OptionData(OptionType.STRING, "version", "The version to untrack", true),
                        new OptionData(OptionType.STRING, "loader", "The loader to untrack", true)
                                .addChoices(LOADERS),
                        new OptionData(OptionType.BOOLEAN, "force-cancel", "Force the current run to cancel", false)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                var version = event.optString("version", "");
                var loader = ModLoader.valueOf(event.optString("loader"));
                if (database.deleteVersion(version, loader)) {
                    event.reply("Stopped indexing version `" + version + "` for loader `" + loader.name().toLowerCase(Locale.ROOT) + "`.").queue();

                    var future = Main.getService(version, loader);
                    if (future != null) {
                        future.cancel(event.optBoolean("force-cancel", false));
                    }
                } else {
                    event.reply("Version is not tracked for that loader!").setEphemeral(true).queue();
                }
            }
        };

        builder.addSlashCommand(new SlashCommand() {
            {
                name = "game-version";
                help = "Track and untrack game versions";
                children = new SlashCommand[] {
                        trackVersionCommand, untrackVersionCommand
                };
            }

            @Override
            protected void execute(SlashCommandEvent event) {

            }
        });
        builder.addSlashCommand(new SlashCommand() {
            {
                name = "index-files";
                help = "Force a list of files to be indexed";
                options = List.of(
                        new OptionData(OptionType.STRING, "version", "The game version of the files", true),
                        new OptionData(OptionType.STRING, "loader", "The loader of the files", true).addChoices(LOADERS),
                        new OptionData(OptionType.STRING, "platform", "The platform of the files", true)
                                .addChoices(Main.PLATFORMS.stream().map(p -> new Command.Choice(p.getName(), p.getName())).toList()),
                        new OptionData(OptionType.STRING, "files", "Comma-separated files to index", true)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                var loader = ModLoader.valueOf(event.optString("loader"));

                var platformName = event.optString("platform");
                ModPlatform platform = Main.PLATFORMS.stream().filter(p -> p.getName().equals(platformName))
                        .findFirst().orElseThrow();

                event.reply("Started manual index...").complete();

                var fileIds = Arrays.stream(event.optString("files", "").split(","))
                        .map(s -> (Object) s.trim()).toList();
                var files = platform.getFiles(fileIds);
                platform.bulkFillData(files);

                var gv = event.optString("version");
                // TODO - we need remapping here
                var indexer = new ModIndexer<>(Main.PLATFORM_CACHE, Main.createDatabase(gv, loader), gv, loader);
                var counter = new Counter<>(new AtomicInteger(), new PlatformModFile[5]);
                try (var exec = Executors.newFixedThreadPool(10, Thread.ofVirtual().name("mod-downloader-manual-", 0)
                        .uncaughtExceptionHandler(Utils.LOG_EXCEPTIONS).factory())) {
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
        builder.addSlashCommand(new SlashCommand() {
            {
                this.name = "merge-mods";
                this.help = "Merge 2 distinct mods in the database that are the same mod available on both platforms";
                this.options = List.of(
                        new OptionData(OptionType.STRING, "version", "Game version to merge for", true),
                        new OptionData(OptionType.STRING, "loader", "Loader to merge for", true)
                                .addChoices(LOADERS),
                        new OptionData(OptionType.INTEGER, "curseforge", "CurseForge project ID", true),
                        new OptionData(OptionType.STRING, "modrinth", "Modrinth project ID", true)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                event.deferReply().complete();
                var loader = ModLoader.valueOf(event.optString("loader"));
                execute(event, loader, Main.createDatabase(event.optString("version"), loader));
            }

            private <T extends IndexDatabase.DatabaseMod<T>> void execute(SlashCommandEvent event, ModLoader loader, IndexDatabase<T> db) {
                var gameVersion = event.optString("version");
                var cfMod = Main.CURSE_FORGE_PLATFORM.getModById(event.getOption("curseforge", OptionMapping::getAsInt)).getLatestFile(gameVersion, loader);
                var mrMod = Main.MODRINTH_PLATFORM.getModById(event.optString("modrinth")).getLatestFile(gameVersion, loader);

                var cfDb = db.getMod(cfMod);
                var mrDb = db.getMod(mrMod);

                // We prefer keeping the newest version of the mod
                if (new DefaultArtifactVersion(cfDb.getVersion()).compareTo(new DefaultArtifactVersion(mrDb.getVersion())) >= 0) {
                    ModIndexer.merge(db, cfDb, mrMod);
                } else {
                    ModIndexer.merge(db, mrDb, cfMod);
                }

                event.getHook().sendMessage("Successfully linked mods!").complete();
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
    public GameVersionIndexService.Listener startIndexingListener(String gameVersion, ModLoader loaderType, ModPlatform platform) {
        var loader = loaderType.name().toLowerCase(Locale.ROOT);
        var message = getChannel().sendMessage("Started indexing game version `" + gameVersion + "`, loader `" + loader + "`, platform " + platform.getName() + "...").complete();
        var start = Instant.now();
        class Listener implements GameVersionIndexService.Listener, Runnable {
            Future<?> task;

            @Override
            public void run() {
                editMessage(embed -> {
                    if (success) {
                        embed.addField("Step", "Success", false);
                        embed.setDescription("**" + indexed.get() + "** mods indexed (JiJ included).\nLast indexed mods:\n");

                        printToEmbed(stored, embed);

                        if (failed.getAmount() != 0) {
                            embed.appendDescription("\n**" + failed.getAmount() + " failures**. Check console for more information.\n");
                            printToEmbed(failed, embed);
                            embed.setColor(Color.RED);

                            message.pin().queue(); // Pin failures for visibility
                        } else {
                            embed.setColor(indexed.get() == 0 ? Color.GRAY : Color.GREEN);
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
                        embed.appendDescription("- " + text);
                        if (newMods.contains(element)) {
                            embed.appendDescription(" (**new**)");
                        }
                        embed.appendDescription("\n");
                    }
                }
            }

            private volatile Counter<PlatformModFile> searchCounter;
            private volatile Counter<PlatformModFile> downloadCounter;

            private volatile boolean startedIndex, success;

            private final AtomicInteger expected = new AtomicInteger(), indexed = new AtomicInteger();
            private final Counter<ModIndexer.IndexCandidate> failed = new Counter<>(new AtomicInteger(), new ModIndexer.IndexCandidate[15]);
            private final Counter<ModIndexer.IndexCandidate> stored = new Counter<>(new AtomicInteger(), new ModIndexer.IndexCandidate[15]);

            private final Set<ModIndexer.IndexCandidate> newMods = new LinkedHashSet<>();

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
                    public void markAsNew(ModIndexer.IndexCandidate element) {
                        newMods.add(element);
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
                message.pin().queue(); // Pin failures for visibility
            }

            private void editMessage(Consumer<EmbedBuilder> consumer) {
                var embed = new EmbedBuilder();
                embed.setTitle("Indexing version `" + gameVersion + "`, loader `" + loader + "`, platform " + platform.getName());
                embed.setAuthor(platform.getName(), null, platform.getLogoUrl());

                embed.setTimestamp(start);

                var secs = Instant.now().getEpochSecond() - start.getEpochSecond();

                embed.setFooter("Time elapsed: " + secs / 60 + " minutes and " + (secs % 60) + " seconds", loaderType.getLogoUrl());

                consumer.accept(embed);

                message.editMessage(MessageEditData.fromEmbeds(embed.build())).setContent(null).complete();
            }
        }

        var list = new Listener();
        list.task = messageUpdateService.scheduleWithFixedDelay(list, 5, 10, TimeUnit.SECONDS);
        return list;
    }
}

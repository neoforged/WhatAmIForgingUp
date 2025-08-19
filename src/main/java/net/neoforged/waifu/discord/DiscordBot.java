package net.neoforged.waifu.discord;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionHook;
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
import net.neoforged.waifu.platform.PlatformProject;
import net.neoforged.waifu.platform.PlatformProjectFile;
import net.neoforged.waifu.util.Counter;
import net.neoforged.waifu.util.DateUtils;
import net.neoforged.waifu.util.ProgressMonitor;
import net.neoforged.waifu.util.Utils;
import net.neoforged.waifu.web.api.TokenManager;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.awt.Color;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DiscordBot implements GameVersionIndexService.ListenerFactory {
    private static final List<Command.Choice> LOADERS = Arrays.stream(ModLoader.values())
            .map(l -> new Command.Choice(l.getDisplayName(), l.name()))
            .toList();
    private static final List<Command.Choice> PLATFORMS = Main.PLATFORMS.stream()
            .map(p -> new Command.Choice(p.getName(), p.getName()))
            .toList();

    private final JDA jda;
    private final ComponentManager components;
    private final long channelId;
    private final ScheduledExecutorService messageUpdateService;
    private final MainDatabase database;
    private final TokenManager tokens;

    public DiscordBot(String token, MainDatabase database, TokenManager tokens) throws InterruptedException {
        this.database = database;
        this.tokens = tokens;

        this.jda = JDABuilder.createLight(token)
                .addEventListeners(components = new ComponentManager())
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

        builder.addSlashCommand(new TokensCommand(tokens, components));

        var trackVersionCommand = new SlashCommand() {
            {
                name = "track";
                help = "Add a version to be tracked and indexed";
                options = List.of(
                        new OptionData(OptionType.STRING, "version", "The version to index", true),
                        new OptionData(OptionType.STRING, "loader", "The loader to index", true)
                                .addChoices(LOADERS),
                        new OptionData(OptionType.STRING, "interval", "The index interval in time notation (like 1h30m). Defaults to the bot's global configuration time", false)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                long interval = 0;
                if (event.hasOption("interval")) {
                    interval = DateUtils.getDurationFromInput(event.optString("interval", ""))
                            .getSeconds();
                }

                var version = event.optString("version", "");
                var loader = ModLoader.valueOf(event.optString("loader"));
                database.addGameVersion(version, loader, interval == 0 ? null : interval);
                event.reply("Started indexing version `" + version + "`").queue();

                Main.schedule(version, loader, interval, DiscordBot.this, 30);
            }
        };

        var untrackVersionCommand = new SlashCommand() {
            {
                name = "untrack";
                help = "Untrack a version to stop it from being indexed";
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

        var listVersionsCommand = new SlashCommand() {
            {
                name = "list";
                help = "List tracked versions";
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                var versions = database.getIndexedGameVersions();
                var embed = new EmbedBuilder().setTitle("Versions currently indexed")
                        .setTimestamp(Instant.now());

                var desc = versions.stream()
                        .map(t -> {
                            var str = new StringBuilder("- `" + t.gameVersion() + "` / " + t.loader());
                            if (t.indexInterval() == 0) {
                                str.append(" - *interval not set* (default is ").append(Main.DEFAULT_INTERVAL_SEC).append(" seconds)");
                            } else {
                                str.append(" - ").append(t.indexInterval()).append(" seconds");
                            }
                            return str.toString();
                        })
                        .collect(Collectors.joining("\n"));

                embed.setDescription(desc);

                event.replyEmbeds(embed.build()).queue();
            }
        };

        builder.addSlashCommand(new SlashCommand() {
            {
                name = "game-version";
                help = "Track and untrack game versions";
                children = new SlashCommand[] {
                        trackVersionCommand, untrackVersionCommand, listVersionsCommand
                };
            }

            @Override
            protected void execute(SlashCommandEvent event) {

            }
        });

        builder.addSlashCommand(new IndexCommand() {
            {
                name = "index-files";
                help = "Force a list of files to be indexed";
                options.add(new OptionData(OptionType.STRING, "files", "Comma-separated files to index", true));
            }

            @Override
            protected void findFiles(SlashCommandEvent event, ModPlatform platform, String gameVersion, ModLoader loader, Consumer<PlatformProjectFile> fileConsumer) {
                var fileIds = Arrays.stream(event.optString("files", "").split(","))
                        .map(s -> (Object) s.trim()).toList();
                platform.getFiles(fileIds).forEach(fileConsumer);
            }
        });
        builder.addSlashCommand(new IndexCommand() {
            {
                name = "index-sample";
                help = "Force a sample of files to be indexed. The amount of mods will be indexed with gaps of at most 25";
                options.add(new OptionData(OptionType.INTEGER, "mods", "The amount of mods to index", true));
            }

            @Override
            protected void findFiles(SlashCommandEvent event, ModPlatform platform, String gameVersion, ModLoader loader, Consumer<PlatformProjectFile> fileConsumer) {
                var random = new Random();

                var modCount = event.optLong("mods");

                int skip = 0;

                var itr = platform.searchProjects(gameVersion, loader, ModPlatform.ProjectType.MOD, ModPlatform.SearchSortField.LAST_UPDATED);
                while (itr.hasNext()) {
                    var next = itr.next();
                    if (!next.isAvailable()) continue;

                    var file = next.getLatestFile(gameVersion, loader);
                    if (file == null) continue;

                    if (skip == 0) {
                        fileConsumer.accept(file);

                        if (--modCount == 0) {
                            break;
                        }

                        skip = random.nextInt(0, 25) + 1;
                    } else {
                        skip--;
                    }
                }
            }
        });
        builder.addSlashCommand(new IndexCommand() {
            {
                name = "index-modpack";
                help = "Force the mods contained in a modpack to be indexed";
                options.add(new OptionData(OptionType.STRING, "modpack", "The slug of the modpack to index", true)
                        .setAutoComplete(true));
            }

            @Override
            protected void findFiles(SlashCommandEvent event, ModPlatform platform, String gameVersion, ModLoader loader, Consumer<PlatformProjectFile> fileConsumer) {
                var modpack = platform.getProjectBySlug(event.optString("modpack"), ModPlatform.ProjectType.MODPACK);
                if (modpack == null) {
                    throw new IllegalArgumentException("Unknown modpack with slug `" + event.optString("modpack") + "`!");
                }

                var file = modpack.getFilesForVersion(gameVersion, loader).next();
                platform.getModsInPack(file).forEach(fileConsumer);
            }

            @Override
            public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
                var platform = Main.getPlatform(event.getOption("platform", OptionMapping::getAsString));
                var gameVersion = event.getOption("version", OptionMapping::getAsString);
                var loader = event.getOption("loader", o -> ModLoader.valueOf(o.getAsString()));
                if (platform == null || gameVersion == null || loader == null) {
                    event.replyChoices().queue();
                    return;
                }

                var query = event.getFocusedOption().getValue();
                var options = StreamSupport.stream(Spliterators.spliteratorUnknownSize(platform.searchProjects(gameVersion, loader, ModPlatform.ProjectType.MODPACK, ModPlatform.SearchSortField.POPULARITY, query), 0), false)
                        .limit(OptionData.MAX_CHOICES)
                        .map(m -> new Command.Choice(m.getTitle(), m.getSlug()))
                        .toList();

                event.replyChoices(options).queue();
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
                var cfMod = Main.CURSE_FORGE_PLATFORM.getProjectById(event.getOption("curseforge", OptionMapping::getAsInt)).getLatestFile(gameVersion, loader);
                var mrMod = Main.MODRINTH_PLATFORM.getProjectById(event.optString("modrinth")).getLatestFile(gameVersion, loader);

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
        builder.addSlashCommand(new SlashCommand() {
            {
                this.name = "delete-mod";
                this.help = "Delete a mod with the given platform ID";
                this.options = List.of(
                        new OptionData(OptionType.STRING, "version", "Game version to delete mod in", true),
                        new OptionData(OptionType.STRING, "loader", "Loader to delete mod in", true)
                                .addChoices(LOADERS),
                        new OptionData(OptionType.INTEGER, "curseforge", "CurseForge project ID", false),
                        new OptionData(OptionType.STRING, "modrinth", "Modrinth project ID", false)
                );
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                event.deferReply().complete();

                PlatformProject mod;
                if (event.hasOption("curseforge")) {
                    mod = Main.CURSE_FORGE_PLATFORM.getProjectById(event.getOption("curseforge", OptionMapping::getAsInt));
                } else {
                    mod = Main.MODRINTH_PLATFORM.getProjectById(event.optString("modrinth"));
                }
                if (mod == null) {
                    event.getHook().sendMessage("Cannot find a mod with the given ID!").queue();
                    return;
                }

                try (var db = Main.createDatabase(event.optString("version"), ModLoader.valueOf(event.optString("loader")))) {
                    var dbMod = db.getMod(mod);
                    if (dbMod == null) {
                        event.getHook().sendMessage("Mod is not indexed!").queue();
                    } else {
                        dbMod.delete();
                        event.getHook().sendMessage("Mod `" + dbMod.getName() + "` deleted!").queue();
                    }
                }
            }
        });

        builder.addSlashCommand(new SlashCommand() {
            {
                name = "shutdown";
                help = "Shut down the bot";
            }

            @Override
            protected void execute(SlashCommandEvent event) {
                event.reply("Shutting down...")
                        .queue($ -> System.exit(0));
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
        var message = getChannel().sendMessage("Started indexing game version `" + gameVersion + "`, loader `" + loaderType.name().toLowerCase(Locale.ROOT) + "`, platform " + platform.getName() + "...").complete();
        return createIndexingListener(message, gameVersion, loaderType, platform);
    }

    public GameVersionIndexService.Listener createIndexingListener(Message message, String gameVersion, ModLoader loaderType, ModPlatform platform) {
        var loader = loaderType.name().toLowerCase(Locale.ROOT);
        var start = Instant.now();
        class Listener implements GameVersionIndexService.Listener, Runnable {
            Future<?> task;

            @Override
            public void run() {
                editMessage(embed -> {
                    if (success) {
                        embed.addField("Step", "Success", false);
                        embed.setDescription("**" + indexed.get() + "** mods indexed (JiJ included).\n");

                        for (ModIndexer.IndexCandidate element : stored.getElements()) {
                            if (element != null) {
                                embed.appendDescription("Last indexed mods:\n");
                            }
                            break;
                        }
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
                        for (PlatformProjectFile element : downloadCounter.getElements()) {
                            if (element != null) {
                                embed.appendDescription("- " + element.getUrl() + "\n");
                            }
                        }
                    } else if (searchCounter != null) {
                        embed.addField("Step", "Searching mods", false);
                        embed.addField("Found mods", searchCounter.getAmount() + " mods currently found", false);

                        embed.appendDescription("Last 5 found mods:\n");
                        for (PlatformProjectFile element : searchCounter.getElements()) {
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

            private volatile Counter<PlatformProjectFile> searchCounter;
            private volatile Counter<PlatformProjectFile> downloadCounter;

            private volatile boolean startedIndex, success;

            private final AtomicInteger expected = new AtomicInteger(), indexed = new AtomicInteger();
            private final Counter<ModIndexer.IndexCandidate> failed = new Counter<>(new AtomicInteger(), new ModIndexer.IndexCandidate[25]);
            private final Counter<ModIndexer.IndexCandidate> stored = new Counter<>(new AtomicInteger(), new ModIndexer.IndexCandidate[25]);

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
            public Counter<PlatformProjectFile> startPlatformScan() {
                return searchCounter = new Counter<>(new AtomicInteger(0), new PlatformProjectFile[5]);
            }

            @Override
            public Counter<PlatformProjectFile> startDownload() {
                return downloadCounter = new Counter<>(new AtomicInteger(0), new PlatformProjectFile[5]);
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
                // Pin failures for visibility
                message.pin().queue(null, err -> Main.LOGGER.warn("Failed to pin failure message because the bot is missing required permissions"));
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

    private abstract class IndexCommand extends SlashCommand {
        protected IndexCommand() {
            options.add(new OptionData(OptionType.STRING, "version", "The game version of the files", true));
            options.add(new OptionData(OptionType.STRING, "loader", "The loader of the files", true).addChoices(LOADERS));
            options.add(new OptionData(OptionType.STRING, "platform", "The platform of the files", true).addChoices(PLATFORMS));
        }

        @Override
        protected final void execute(SlashCommandEvent event) {
            var gameVersion = event.optString("version");
            var loader = ModLoader.valueOf(event.optString("loader"));

            ModPlatform platform = Main.getPlatform(event.optString("platform"));

            var response = event.reply("Started index...")
                    .flatMap(InteractionHook::retrieveOriginal)
                    .complete();

            var listener = createIndexingListener(response, gameVersion, loader, platform);

            try {
                var remapper = loader.createRemapper(gameVersion);

                var files = new ArrayList<PlatformProjectFile>();
                var counter = listener.startPlatformScan();

                findFiles(event, platform, gameVersion, loader, file -> {
                    files.add(file);
                    counter.add(file);
                });

                platform.bulkFillFiles(files);

                var indexer = new ModIndexer<>(Main.PLATFORM_CACHE, Main.createDatabase(gameVersion, loader), gameVersion, loader, remapper, ModIndexer.DEFAULT_INDEXERS);
                try (var exec = Executors.newFixedThreadPool(10, Thread.ofVirtual().name("mod-downloader-manual-", 0)
                        .uncaughtExceptionHandler(Utils.LOG_EXCEPTIONS).factory())) {
                    indexer.downloadAndConsiderConcurrently(files, exec, listener.startDownload());
                }

                var scanned = indexer.index(platform, GameVersionIndexService.VIRTUAL_THREAD_EXECUTOR, GameVersionIndexService.CONCURRENCY, listener.startIndex(), Main.SANITIZER);

                for (ModIndexer.IndexCandidate indexCandidate : scanned) {
                    try {
                        indexCandidate.file().close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                listener.markFinish(scanned.size());

                event.getHook().editOriginal("Index completed. Indexed " + scanned.size() + " mods!").complete();
            } catch (Exception exception) {
                listener.raiseFatalException(exception);
                Main.LOGGER.error("Failed manual scan using /{} for loader {}, game version {}", event.getFullCommandName(), loader, gameVersion);
            }
        }

        protected abstract void findFiles(SlashCommandEvent event, ModPlatform platform, String gameVersion, ModLoader loader, Consumer<PlatformProjectFile> fileConsumer);
    }
}

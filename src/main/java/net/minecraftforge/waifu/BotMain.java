/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.request.Method;
import io.github.matyrobbrt.curseforgeapi.request.Request;
import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileIndex;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.matyrobbrt.curseforgeapi.util.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraftforge.metabase.MetabaseClient;
import net.minecraftforge.waifu.collect.CollectorRule;
import net.minecraftforge.waifu.collect.DefaultDBCollector;
import net.minecraftforge.waifu.collect.DiscordProgressMonitor;
import net.minecraftforge.waifu.collect.StatsCollector;
import net.minecraftforge.waifu.db.InheritanceDB;
import net.minecraftforge.waifu.db.ModIDsDB;
import net.minecraftforge.waifu.db.ProjectsDB;
import net.minecraftforge.waifu.db.RefsDB;
import net.minecraftforge.waifu.logback.DiscordLogbackAppender;
import net.minecraftforge.waifu.util.ByteConversion;
import net.minecraftforge.waifu.util.MappingUtils;
import net.minecraftforge.waifu.util.Remapper;
import net.minecraftforge.waifu.util.SavedTrackedData;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BotMain {
    public static final Path ROOT = Path.of(System.getProperty("waifu.rootdir", "./"));

    public static final Logger LOGGER = LoggerFactory.getLogger(BotMain.class);

    static {
        final Path propsPath = Path.of(System.getProperty("waifu.propsFile", "bot.properties"));
        if (Files.exists(propsPath)) {
            final Properties props = new Properties();
            try (final var reader = Files.newBufferedReader(propsPath)) {
                props.load(reader);
            } catch (Exception ex) {
                LOGGER.error("Could not read properties:", ex);
            }
            props.forEach((o, o2) -> System.setProperty(o.toString(), o2.toString()));
        }
    }

    private static final int CF_PAGINATION_LIMIT = 10_000;

    public static final CurseForgeAPI CF = Utils.rethrowSupplier(() -> CurseForgeAPI.builder()
            .apiKey(System.getProperty("curseforge.token"))
            .build()).get();

    private static final MetabaseClient METABASE = new MetabaseClient(
            System.getProperty("metabase.url"),
            System.getProperty("metabase.user"),
            System.getProperty("metabase.password")
    );

    public static final SavedTrackedData<Set<Integer>> PACKS = new SavedTrackedData<>(
            new com.google.gson.reflect.TypeToken<>() {},
            HashSet::new, ROOT.resolve("data/modpacks.json")
    );

    public static final SavedTrackedData<Set<String>> GAME_VERSIONS = new SavedTrackedData<>(
            new com.google.gson.reflect.TypeToken<>() {},
            HashSet::new, ROOT.resolve("data/game_versions.json")
    );

    static final Set<String> CURRENTLY_COLLECTED = new CopyOnWriteArraySet<>();

    public static final String VERSION = Objects.requireNonNullElse(BotMain.class.getPackage().getImplementationVersion(), "UNKNOWN");

    private static JDA jda;

    public static void main(String[] args) throws Exception {
        final ExecutorService rescanner = Executors.newFixedThreadPool(3, Thread.ofPlatform()
                .daemon(true).name("stats-scanner", 0).uncaughtExceptionHandler((t, e) -> LOGGER.error("Encountered exception on scanner thread: ", e))
                .factory());

        jda = JDABuilder.createLight(System.getProperty("bot.token"), EnumSet.of(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES))
                .addEventListeners((EventListener) gevent -> {
                    if (!(gevent instanceof ReadyEvent event)) return;

                    event.getJDA().updateCommands()
                            .addCommands(
                                    Commands.slash("modpacks", "Command used to manage collection of stats in modpacks")
                                            .addSubcommands(new SubcommandData("add", "Collect stats from the given modpack")
                                                    .addOption(OptionType.INTEGER, "modpack", "The ID of the modpack to collect stats from", true))
                                            .addSubcommands(new SubcommandData("list", "List all watched modpacks"))
                                            .addSubcommands(new SubcommandData("remove", "Remove a modpack from stats collection")
                                                    .addOption(OptionType.INTEGER, "modpack", "The ID of the modpack to remove", true)
                                                    .addOption(OptionType.BOOLEAN, "removedb", "Whether to remove the modpack from the database", true)),

                                    Commands.slash("gameversion", "Command used to manage collection of stats for specific game versions")
                                            .addSubcommands(new SubcommandData("add", "Watch a game version")
                                                    .addOption(OptionType.STRING, "version", "The game version to watch", true))
                                            .addSubcommands(new SubcommandData("list", "List all watched game versions"))
                                            .addSubcommands(new SubcommandData("remove", "Un-watch a game version")
                                                    .addOption(OptionType.INTEGER, "version", "The game version to remove", true)
                                                    .addOption(OptionType.BOOLEAN, "removedb", "Whether to remove the game version from the database", true)),

                                    Commands.slash("delete-cache", "Deletes the CurseForge downloads cache"),
                                    Commands.slash("help", "Information about the bot"),
                                    Commands.slash("data-size", "Shows the size of the data stored (in the database and the file cache)"))
                            .queue();
                }, (EventListener) gevent -> {
                    if (!(gevent instanceof SlashCommandInteractionEvent event)) return;

                    try {
                        net.minecraftforge.waifu.Commands.onSlashCommandInteraction(event, rescanner);
                    } catch (Exception ex) {
                        event.getHook().sendMessage("Encountered exception executing command: " + ex).queue();
                        LOGGER.error("Encountered exception executing command '{}': ", event.getCommandString(), ex);
                    }
                })
                .setActivity(Activity.watching("naughty mods"))
                .build()
                .awaitReady();

        DiscordLogbackAppender.setup(jda.getChannelById(MessageChannel.class, System.getProperty("bot.loggingChannel", "0")));

        LOGGER.info("Bot started! Version: {}", VERSION);

        final ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
        scheduler.setKeepAliveTime(1, TimeUnit.MINUTES);
        scheduler.allowCoreThreadTimeOut(true);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (final Mod mod : CF.makeRequest(getMods(PACKS.read())).orElse(List.of())) {
                    if (!CURRENTLY_COLLECTED.add(String.valueOf(mod.id()))) return;
                    rescanner.submit(() -> trigger(mod));
                }
            } catch (CurseForgeException e) {
                LOGGER.error("Encountered error initiating pack stats collection:", e);
            }
        }, 2, 60, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(() -> {
            for (final String version : GAME_VERSIONS.read()) {
                if (!CURRENTLY_COLLECTED.add(version)) return;

                rescanner.submit(() -> {
                    try {
                        triggerGameVersion(version);
                    } catch (Exception ex) {
                        LOGGER.error("Encountered exception collecting statistics for game version '{}':", version, ex);
                    }
                });
            }
         }, 2, 60 * 24, TimeUnit.MINUTES);
    }

    public static Request<List<Mod>> getMods(Iterable<Integer> modIds) {
        final JsonObject body = new JsonObject();
        final JsonArray array = new JsonArray();
        for (final int id : modIds) {
            array.add(id);
        }
        body.add("modIds", array);
        return new Request<>("/v1/mods", Method.POST, body, "data", Requests.Types.MOD_LIST);
    }

    static void trigger(Mod pack) {
        try {
            final String schemaName = "pack_" + pack.id();
            final var connection = Database.createDatabaseConnection(schemaName);
            if (connection.getKey().initialSchemaVersion == null) { // Schema was created
                Database.updateMetabase(METABASE, schemaName);
            }

            final Jdbi jdbi = connection.getValue();
            final ProjectsDB projects = jdbi.onDemand(ProjectsDB.class);

            final ModCollector collector = new ModCollector(CF);
            final Response<File> mainFileResponse = CF.getHelper().getModFile(pack.id(), pack.mainFileId());
            if (mainFileResponse.isEmpty()) {
                LOGGER.info("Could not query file {}. Status code: {}", pack.mainFileId(), mainFileResponse.getStatusCode());
                return;
            }

            final File mainFile = mainFileResponse.get();
            if (Objects.equals(projects.getFileId(pack.id()), mainFile.id())) {
                LOGGER.trace("Pack {} is up-to-date.", pack.id());
                return;
            }

            final Message logging = jda.getChannelById(MessageChannel.class, System.getProperty("bot.loggingChannel"))
                            .sendMessage("Status of collection of statistics of **" + pack.name() + "**, file ID: " + mainFile.id())
                            .complete();
            LOGGER.info("Found new file ({}) for pack {}: started stats collection.", mainFile.id(), pack.id());

            final DiscordProgressMonitor progressMonitor = new DiscordProgressMonitor(
                    logging, (id, ex) -> LOGGER.error("Collection for mod '{}' in pack {} failed:", id, pack.id(), ex)
            );
            progressMonitor.markCollection(-1);

            collector.fromModpack(mainFile, progressMonitor);

            final Remapper remapper = Remapper.fromMappings(MappingUtils.srgToMoj(mainFile.sortableGameVersions()
                    .stream().filter(g -> !g.gameVersion().isBlank()).max(Comparator.comparing(g -> Instant.parse(g.gameVersionReleaseDate()))).orElseThrow().gameVersion()));

            StatsCollector.collect(
                    collector.getJarsToProcess(),
                    CollectorRule.collectAll(),
                    projects,
                    jdbi.onDemand(InheritanceDB.class),
                    jdbi.onDemand(RefsDB.class),
                    jdbi.onDemand(ModIDsDB.class),
                    (mid) -> new DefaultDBCollector(mid, jdbi, remapper, true),
                    progressMonitor,
                    true
            );

            LOGGER.info("Finished stats collection of pack {}", pack.id());
            CURRENTLY_COLLECTED.remove(String.valueOf(pack.id()));
            projects.insert(pack.id(), mainFile.id());
        } catch (Exception ex) {
            LOGGER.error("Encountered exception collecting stats of pack: ", ex);
        }
    }

    static void triggerGameVersion(String gameVersion) throws Exception {
        final String schemaName = computeVersionSchema(gameVersion);
        final var connection = Database.createDatabaseConnection(schemaName);
        if (connection.getKey().initialSchemaVersion == null) { // Schema was created
            Database.updateMetabase(METABASE, schemaName);
        }

        final Jdbi jdbi = connection.getValue();
        final ProjectsDB projects = jdbi.onDemand(ProjectsDB.class);

        final Set<Integer> fileIds = projects.getFileIDs();

        final List<FileIndex> newMods = new ArrayList<>();
        int idx = 0;
        int maxItems = CF_PAGINATION_LIMIT;

        modsquery:
        while (idx < maxItems) {
            final var response = CF.getHelper().searchModsPaginated(ModSearchQuery.of(Constants.GameIDs.MINECRAFT)
                    .gameVersion(gameVersion).classId(6) // We're interested in mods
                    .sortField(ModSearchQuery.SortField.LAST_UPDATED)
                    .sortOrder(ModSearchQuery.SortOrder.DESCENDENT)
                    .modLoaderType(ModLoaderType.FORGE)
                    .pageSize(50).index(idx))
                    .orElse(null);
            if (response == null) break;

            idx = response.pagination().index() + 50;
            maxItems = Math.min(response.pagination().totalCount(), CF_PAGINATION_LIMIT);
            for (final Mod mod : response.data()) {
                final FileIndex matching = mod.latestFilesIndexes().stream()
                        .filter(f -> f.gameVersion().equals(gameVersion) && f.modLoader() != null && f.modLoaderType() == ModLoaderType.FORGE)
                        .limit(1)
                        .findFirst().orElse(null);
                if (matching == null) continue;

                if (fileIds.contains(matching.fileId())) {
                    final var latestFiles = getLatestModFiles(mod);
                    if (latestFiles.isEmpty() || latestFiles.get(0).id() == matching.fileId()) { // Make sure it's actually the latest file of the project
                        break modsquery;
                    }
                } else {
                    newMods.add(matching);
                }
            }
        }

        if (newMods.isEmpty()) {
            LOGGER.info("Found no new mods to collect stats on for game version {}.", gameVersion);
            return;
        }

        final Message logging = jda.getChannelById(MessageChannel.class, System.getProperty("bot.loggingChannel"))
                .sendMessage("Status of collection of statistics for game version '"+ gameVersion + "'")
                .complete();
        LOGGER.info("Started stats collection for game version '{}'. Found {} mods to scan.", gameVersion, newMods.size());

        final DiscordProgressMonitor progressMonitor = new DiscordProgressMonitor(
                logging, (id, ex) -> LOGGER.error("Collection for mod '{}' in game version '{}' failed:", id, gameVersion, ex)
        );
        progressMonitor.markCollection(newMods.size());

        final ModCollector collector = new ModCollector(CF);
        final List<File> toDownload = CF.getHelper().getFiles(newMods.stream()
                .mapToInt(FileIndex::fileId)
                .toArray()).orElse(List.of()).stream()
                .filter(f -> f.downloadUrl() != null)
                .filter(distinct(File::id))
                .toList();
        progressMonitor.setDownloadTarget(toDownload.size());
        try (final ExecutorService executor = Executors.newFixedThreadPool(3, Thread.ofPlatform()
                .name("mod-downloader", 0)
                .daemon(true)
                .factory())) {
            for (final File file : toDownload) {
                executor.submit(() -> {
                    try {
                        collector.considerFile(file);
                    } finally {
                        progressMonitor.downloadEnded(file);
                    }
                    return null;
                });
            }
        }

        final Remapper remapper = Remapper.fromMappings(MappingUtils.srgToMoj(gameVersion));

        StatsCollector.collect(
                collector.getJarsToProcess(),
                CollectorRule.collectAll(),
                projects,
                jdbi.onDemand(InheritanceDB.class),
                jdbi.onDemand(RefsDB.class),
                jdbi.onDemand(ModIDsDB.class),
                (mid) -> new DefaultDBCollector(mid, jdbi, remapper, true),
                progressMonitor,
                false
        ); // TODO - Delete data of deleted mods every week or so

        LOGGER.info("Finished stats collection for game version '{}'", gameVersion);
        CURRENTLY_COLLECTED.remove(gameVersion);
    }

    public static String computeVersionSchema(String version) {
        return "gv_" + version.replace('.', '_').replace('-', '_');
    }

    public static <T, Z> Predicate<T> distinct(Function<T, Z> extractor) {
        final Set<Z> values = new HashSet<>();
        return t -> values.add(extractor.apply(t));
    }

    private static List<File> getLatestModFiles(Mod mod) throws IOException {
        record Response(List<File> data) {}
        try (final var reader = new InputStreamReader(URI.create("https://www.curseforge.com/api/v1/mods/%s/files?pageIndex=0&pageSize=50&sort=dateCreated&sortDescending=true&removeAlphas=false".formatted(mod.id())).toURL().openStream())) {
            return CF.getGson().fromJson(reader, Response.class).data;
        }
    }
}

package net.neoforged.waifu;

import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.javalin.Javalin;
import net.neoforged.waifu.db.DataSanitizer;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.db.SQLDatabase;
import net.neoforged.waifu.discord.DiscordBot;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.impl.cf.CurseForgePlatform;
import net.neoforged.waifu.platform.impl.mr.ModrinthPlatform;
import net.neoforged.waifu.util.DateUtils;
import net.neoforged.waifu.util.Utils;
import net.neoforged.waifu.web.WebService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static final Path CACHE = Path.of(".cache");
    public static final Path PLATFORM_CACHE = Main.CACHE.resolve("platform");
    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
            3, Thread.ofPlatform().name("indexer-").uncaughtExceptionHandler(Utils.LOG_EXCEPTIONS).factory()
    );
    public static final DataSanitizer SANITIZER = DataSanitizer.of(
            DataSanitizer.REMOVE_OWN_DIRECT_REFERENCES, DataSanitizer.REMOVE_PRIVATE_MEMBERS
    );
    public static final long DEFAULT_INTERVAL_SEC = DateUtils.getDurationFromInput(System.getenv().getOrDefault("DEFAULT_INDEX_INTERVAL", "1h")).getSeconds();

    public static final CurseForgeAPI CF_API;
    public static final CurseForgePlatform CURSE_FORGE_PLATFORM;
    public static final ModrinthPlatform MODRINTH_PLATFORM;
    public static final List<ModPlatform> PLATFORMS;

    private static final Map<String, Map<ModLoader, Future<?>>> SERVICES = new ConcurrentHashMap<>();

    static {
        try {
            CF_API = CurseForgeAPI.builder().apiKey(System.getenv("CF_API_KEY")).build();
            CURSE_FORGE_PLATFORM = new CurseForgePlatform(CF_API);

            MODRINTH_PLATFORM = new ModrinthPlatform(System.getenv("MODRINTH_API_TOKEN"));

            PLATFORMS = List.of(CURSE_FORGE_PLATFORM, MODRINTH_PLATFORM);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        var db = new MainDatabase(Path.of("data.db"));
        db.runFlyway();

        var bot = new DiscordBot(System.getenv("DISCORD_TOKEN"), db);

        long initialDelay = 15;
        for (var version : db.getIndexedGameVersions()) {
            schedule(version.gameVersion(), version.loader(), version.indexInterval(), bot, initialDelay);

            initialDelay += 60 * 10;
        }

        WebService web = new WebService(Javalin.create(cfg -> cfg.useVirtualThreads = true));
        web.start();
    }

    public static void schedule(String version, ModLoader loader, long intervalSeconds, DiscordBot bot, long initialDelaySeconds) {
        var indexDb = createDatabase(version, loader);
        var future = EXECUTOR.scheduleWithFixedDelay(
                new GameVersionIndexService(version, loader, PLATFORMS, indexDb, SANITIZER, bot),
                initialDelaySeconds,
                intervalSeconds == 0 ? DEFAULT_INTERVAL_SEC : intervalSeconds,
                TimeUnit.SECONDS
        );

        SERVICES.computeIfAbsent(version, k -> new ConcurrentHashMap<>())
                .put(loader, future);
    }

    @Nullable
    public static Future<?> getService(String version, ModLoader loader) {
        return SERVICES.getOrDefault(version, Map.of()).get(loader);
    }

    public static IndexDatabase<?> createDatabase(String version, ModLoader loader) {
        var indexDb = new SQLDatabase("jdbc:postgresql://" + System.getenv("POSTGRES_DB_URL") + "?currentSchema=" + version + "-" + loader.name().toLowerCase(Locale.ROOT),
                System.getenv("POSTGRES_DB_USERNAME"), System.getenv("POSTGRES_DB_PASSWORD"));
        indexDb.runFlyway();
        return indexDb;
    }
}

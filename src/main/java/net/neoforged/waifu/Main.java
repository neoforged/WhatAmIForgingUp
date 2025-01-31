package net.neoforged.waifu;

import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import net.neoforged.waifu.db.DataSanitizer;
import net.neoforged.waifu.db.SQLDatabase;
import net.neoforged.waifu.discord.DiscordBot;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.impl.cf.CurseForgePlatform;
import net.neoforged.waifu.platform.impl.mr.ModrinthPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static final Path CACHE = Path.of(".cache");
    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
            3, Thread.ofPlatform().name("indexer-").factory()
    );
    public static final DataSanitizer SANITIZER = DataSanitizer.of(
            DataSanitizer.REMOVE_OWN_DIRECT_REFERENCES, DataSanitizer.REMOVE_PRIVATE_MEMBERS
    );
    public static final long DELAY_SEC = 60 * 60;

    private static final List<ModPlatform> PLATFORMS;

    static {
        try {
            PLATFORMS = List.of(
                    new CurseForgePlatform(CurseForgeAPI.builder().apiKey(System.getenv("CF_API_KEY")).build()),
                    new ModrinthPlatform()
            );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        var db = new MainDatabase(Path.of("data.db"));
        db.runFlyway();

        var bot = new DiscordBot(System.getenv("DISCORD_TOKEN"), db);

        long initialDelay = 30;
        for (String version : db.getIndexedGameVersions()) {
            schedule(version, bot, initialDelay);

            initialDelay += 60 * 10;
        }
    }

    public static void schedule(String version, DiscordBot bot, long initialDelaySeconds) {
        var indexDb = new SQLDatabase("jdbc:postgresql://" + System.getenv("POSTGRES_DB_URL") + "?currentSchema=" + version, System.getenv("POSTGRES_DB_USERNAME"), System.getenv("POSTGRES_DB_PASSWORD"));

        indexDb.runFlyway();

        EXECUTOR.scheduleWithFixedDelay(
                new GameVersionIndexService(version, PLATFORMS, indexDb, SANITIZER, bot),
                initialDelaySeconds,
                DELAY_SEC,
                TimeUnit.SECONDS
        );
    }
}

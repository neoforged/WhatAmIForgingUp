package net.neoforged.waifu.web;

import io.javalin.Javalin;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.db.IndexDatabase;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.PlatformMod;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VersionWebService {
    public VersionWebService(Javalin javalin) {
        final Map<String, IndexDatabase<?>> databases = new ConcurrentHashMap<>();
        javalin.get("/<version>/mod/<mod>", ctx -> {
            var db = databases.computeIfAbsent(ctx.pathParam("version"), ver -> {
                var spl = ver.split("-", 2);
                return Main.createDatabase(spl[0], ModLoader.valueOf(spl[1].toUpperCase(Locale.ROOT)));
            });

            var modId = ctx.pathParam("mod");

            Map<String, PlatformModResponse> platformMods = new HashMap<>(2);
            IndexDatabase.DatabaseMod<?> mod;
            if (isInt(modId)) {
                var cf = Main.CURSE_FORGE_PLATFORM.getModById(Integer.valueOf(modId));
                platformMods.put("curseforge", new PlatformModResponse(cf));
                mod = db.getMod(cf);

                if (mod != null && mod.getModrinthProjectId() != null) {
                    platformMods.put("modrinth", new PlatformModResponse(Main.MODRINTH_PLATFORM.getModById(mod.getModrinthProjectId())));
                }
            } else {
                var mr = Main.MODRINTH_PLATFORM.getModById(modId);
                platformMods.put("modrinth", new PlatformModResponse(mr));
                mod = db.getMod(mr);

                if (mod != null && mod.getCurseForgeProjectId() != null) {
                    platformMods.put("curseforge", new PlatformModResponse(Main.CURSE_FORGE_PLATFORM.getModById(mod.getCurseForgeProjectId())));
                }
            }

            if (mod == null) {
                ctx.status(404);
                return;
            }

            record GetModResponse(
                    String name,
                    String latestIndexVersion,
                    @Nullable String coordinate,
                    Map<String, PlatformModResponse> platforms
            ) {}

            var res = new GetModResponse(
                    mod.getName(), mod.getVersion(),
                    mod.getMavenCoordinate(),
                    platformMods
            );
            ctx.json(res);
        });
    }

    public record PlatformModResponse(
            String title, String description, String icon,
            long downloads, Instant date
    ) {
        public PlatformModResponse(PlatformMod mod) {
            this(mod.getTitle(), mod.getDescription(), mod.getIconUrl(), mod.getDownloads(), mod.getReleasedDate());
        }
    }

    private static boolean isInt(String mod) {
        try {
            Integer.valueOf(mod);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}

package net.neoforged.waifu.web;

import io.javalin.Javalin;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.platform.PlatformMod;
import net.neoforged.waifu.platform.PlatformModFile;

import java.util.List;

public class PlatformWebService {
    public PlatformWebService(Javalin javalin) {
        javalin.get("/platform/<platform>/pack/<pid>", ctx -> {
            var plat = Main.PLATFORMS.stream().filter(p -> p.getName().equals(ctx.pathParam("platform"))).findFirst().orElseThrow();
            PlatformMod mod;
            try {
                var pid = Integer.valueOf(ctx.pathParam("pid"));
                mod = plat.getModById(pid);
            } catch (NumberFormatException nr) {
                mod = plat.getModBySlug(ctx.pathParam("pid"));
            }

            PlatformModFile file;

            var versionFilter = ctx.queryParam("mc-version");
            if (versionFilter != null) {
                file = mod.getLatestFile(versionFilter, null);
            } else {
                file = mod.getAllFiles().next();
            }

            var mods = plat.getModsInPack(file);

            record FileRef(Object projectId, Object fileId) {}
            record Response(
                    List<FileRef> mods
            ) {}

            ctx.json(new Response(mods.stream()
                    .map(m -> new FileRef(m.getModId(), m.getId())).toList()));
        });
    }
}

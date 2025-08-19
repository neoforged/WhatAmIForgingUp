package net.neoforged.waifu.web;

import io.javalin.Javalin;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.platform.ModPlatform;
import net.neoforged.waifu.platform.PlatformProject;
import net.neoforged.waifu.platform.PlatformProjectFile;

import java.util.List;

public class PlatformWebService {
    public PlatformWebService(Javalin javalin) {
        javalin.get("/platform/<platform>/pack/<pid>", ctx -> {
            var plat = Main.getPlatform(ctx.pathParam("platform"));
            PlatformProject mod;
            try {
                var pid = Integer.valueOf(ctx.pathParam("pid"));
                mod = plat.getProjectById(pid);
            } catch (NumberFormatException nr) {
                mod = plat.getProjectBySlug(ctx.pathParam("pid"), ModPlatform.ProjectType.MOD);
            }

            PlatformProjectFile file;

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
                    .map(m -> new FileRef(m.getProjectId(), m.getId())).toList()));
        });
    }
}

package net.neoforged.waifu.util;

import net.neoforged.art.api.IdentifierFixerConfig;
import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.SourceFixerConfig;
import net.neoforged.art.api.Transformer;
import net.neoforged.binarypatcher.Patcher;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFilePath;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NeoForgeJarProvider {
    private static final URI NFRT_JAR = URI.create("https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/1.0.19/neoform-runtime-1.0.19-all.jar");
    private static final String LATEST_VERSION_URL = "https://maven.neoforged.net/api/maven/latest/version/releases/net%2Fneoforged%2Fneoforge?filter=%s&type=json";
    private static final String DOWNLOAD_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/${version}/neoforge-${version}-${type}.jar";

    public static String getLatestVersion(String mcVersion) {
        var split = mcVersion.split("\\.");
        var neoPrefix = split[1] + "." + (split.length == 2 ? "0" : split[2]);
        record Response(String version) {}
        return Utils.getJson(URI.create(LATEST_VERSION_URL.formatted(neoPrefix)), Response.class).version();
    }

    public static List<ModFileInfo> provide(String neoVersion) throws IOException {
        var path = Main.CACHE.resolve("loader/neoforge-" + neoVersion + ".jar");
        var neoSplit = neoVersion.split("\\.");
        var mcVersion = "1." + neoSplit[0] + (neoSplit[1].equals("0") ? "" : ("." + neoSplit[1]));
        var patched = Main.CACHE.resolve("loader/neoforge-patched-mc-" + neoVersion + ".jar");
        if (!Files.exists(path)) {
            var installer = Main.CACHE.resolve("loader/neoforge-" + neoVersion + "-installer.jar");
            Utils.download(URI.create(DOWNLOAD_URL.replace("${version}", neoVersion).replace("${type}", "installer")), installer);

            InstallProfile installProfile;
            try (var installerFs = FileSystems.newFileSystem(installer);
                var is = Files.newBufferedReader(installerFs.getPath("install_profile.json"))) {
                installProfile = Utils.GSON.fromJson(is, InstallProfile.class);
            }

            var neoFormVersion = installProfile.data().get("MAPPINGS").client().replace("[net.neoforged:neoform:", "")
                    .replace(":mappings@txt]", "");

            var neoformMcJar = Main.CACHE.resolve("minecraft/" + neoFormVersion + ".jar");

            if (!Files.exists(neoformMcJar)) {
                var nfrt = Utils.download(NFRT_JAR, Main.CACHE.resolve("tools/nfrt.jar"));

                var mcJarSlim = Main.CACHE.resolve("minecraft/" + mcVersion + "-" + neoFormVersion + "-slim.jar");

                var stripClientJar = Main.CACHE.resolve("minecraft/" + neoFormVersion + "-strip.jar");
                var rawJar = Main.CACHE.resolve("minecraft/" + neoFormVersion + "-raw.jar");
                var mappings = Main.CACHE.resolve("minecraft/" + neoFormVersion + "-mappings.txt");

                Files.createDirectories(mappings.getParent());

                execJar(nfrt,
                        "run", "--neoform",
                        "net.neoforged:neoform:" + neoFormVersion + "@zip",
                        "--dist", "joined",
                        "--write-result=node.mergeMappings.output.output:" + mappings.toAbsolutePath(),
                        "--write-result=node.stripClient.output.output:" + stripClientJar.toAbsolutePath(),
                        "--write-result=node.downloadClient.output.output:" + rawJar.toAbsolutePath());

                Renamer.builder()
                        .logger(s -> {})
                        .map(mappings.toFile())
                        .add(Transformer.parameterAnnotationFixerFactory())
                        .add(Transformer.recordFixerFactory())
                        .add(Transformer.identifierFixerFactory(IdentifierFixerConfig.ALL))
                        .add(Transformer.sourceFixerFactory(SourceFixerConfig.JAVA))
                        .build()
                        .run(stripClientJar.toFile(), mcJarSlim.toFile());

                merge(neoformMcJar, rawJar, mcJarSlim, mcVersion);
            }

            Utils.download(URI.create(DOWNLOAD_URL.replace("${version}", neoVersion).replace("${type}", "universal")), path);

            var lzma = Main.CACHE.resolve("loader/neoforge-" + neoVersion + ".lzma");

            try (var is = new ZipInputStream(URI.create(DOWNLOAD_URL.replace("${version}", neoVersion).replace("${type}", "installer")).toURL().openStream());
                var os = Files.newOutputStream(lzma)) {
                ZipEntry entry;
                while ((entry = is.getNextEntry()) != null) {
                    if (entry.getName().equals("data/client.lzma")) {
                        is.transferTo(os);
                        break;
                    }
                }
            }

            Patcher patcher = new Patcher(neoformMcJar.toFile(), patched.toFile())
                    .keepData(true)
                    .includeUnpatched(true)
                    .pack200(false)
                    .legacy(false);

            patcher.loadPatches(lzma.toFile(), null);

            patcher.process();

            Files.delete(lzma);
            Files.delete(installer);
        }

        var neoMod = Objects.requireNonNull(ModFileInfo.read(ModFilePath.create(path), "net.neoforged:neoforge", neoVersion));
        var mcMod = Objects.requireNonNull(ModFileInfo.read(ModFilePath.create(patched), "net.minecraft:minecraft", mcVersion));

        return List.of(neoMod, mcMod);
    }

    private record InstallProfile(Map<String, Data> data) {
        private record Data(String client) {}
    }

    private static int execJar(Path jar, Object... args) throws IOException {
        var execPath = ProcessHandle.current()
                .info()
                .command()
                .orElseThrow();
        var command = new ArrayList<String>();
        command.add(execPath);
        command.add("-jar");
        command.add(jar.toAbsolutePath().toString());
        for (Object arg : args) {
            command.add(arg.toString());
        }

        var workingDir = Files.createTempDirectory("nfrt_invoke");
        Files.createDirectories(workingDir.getParent());

        var proc = new ProcessBuilder()
                .directory(workingDir.toFile())
                .command(command)
                .redirectErrorStream(true)
                .redirectOutput(workingDir.resolve("log").toFile())
                .start();

        try {
            return proc.waitFor();
        } catch (InterruptedException e) {
            Utils.sneakyThrow(e);
            throw null;
        }
    }

    public static void merge(Path out, Path mcAssets, Path patchedMc, String mcVersion) throws IOException {
        var man = new Manifest();

        var ma = man.getMainAttributes();
        ma.put(Attributes.Name.MANIFEST_VERSION, "1.2");
        ma.putValue("FMLModType", "GAMELIBRARY");
        ma.put(Attributes.Name.IMPLEMENTATION_VERSION, mcVersion);

        try (var zout = new JarOutputStream(Files.newOutputStream(out), man);
             var assetsIn = new ZipInputStream(Files.newInputStream(mcAssets));
             var patchedIn = new ZipInputStream(Files.newInputStream(patchedMc))) {

            ZipEntry entry;
            while ((entry = assetsIn.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    zout.putNextEntry(entry);
                    assetsIn.transferTo(zout);
                    zout.closeEntry();
                }
            }

            while ((entry = patchedIn.getNextEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    zout.putNextEntry(entry);
                    patchedIn.transferTo(zout);
                    zout.closeEntry();
                }
            }
        }
    }
}

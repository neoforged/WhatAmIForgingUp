package net.neoforged.waifu.util;

import net.neoforged.waifu.Main;
import net.neoforged.waifu.meta.ModFileInfo;
import net.neoforged.waifu.meta.ModFilePath;
import net.neoforged.waifu.meta.ModFileReader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NeoForgeJarProvider {
    private static final String LATEST_VERSION_URL = "https://maven.neoforged.net/api/maven/latest/version/releases/net/neoforged/neoforge?filter=%s&type=json";
    private static final String DOWNLOAD_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/${version}/neoforge-${version}-${type}.jar";

    public static String getLatestVersion(String mcVersion) {
        var split = mcVersion.split("\\.");
        var neoPrefix = split[1] + "." + (split.length == 2 ? "0" : split[2]) + ".";
        record Response(String version) {}
        return Utils.getJson(URI.create(LATEST_VERSION_URL.formatted(neoPrefix)), Response.class).version();
    }

    public static List<ModFileInfo> provide(String neoVersion) throws IOException {
        var mcVersion = getMcVersion(neoVersion);

        var installationFolder = Main.CACHE.resolve("loader/neoforge").toAbsolutePath();
        Files.createDirectories(installationFolder);

        var launcherProfilePath = installationFolder.resolve("launcher_profiles.json");
        if (Files.notExists(launcherProfilePath)) {
            Files.writeString(launcherProfilePath, "{}");
        }

        var installer = installationFolder.resolve(neoVersion + "-installer.jar");
        Utils.download(URI.create(DOWNLOAD_URL.replace("${version}", neoVersion).replace("${type}", "installer")), installer);

        execJar(installer, installationFolder,
                "--install-client", installationFolder);

        var neoJar = installationFolder.resolve("libraries/net/neoforged/neoforge/" + neoVersion + "/neoforge-" + neoVersion + "-universal.jar");

        Path mcJarPath;

        // This is the new combined Jar, which includes unpatched, patched and resources for Minecraft
        // This has been the new way since NeoForge 21.10.37-beta
        var combinedMinecraftJar = installationFolder.resolve("libraries/net/neoforged/minecraft-client-patched/" + neoVersion + "/minecraft-client-patched-" + neoVersion + ".jar");
        if (Files.exists(combinedMinecraftJar)) {
            mcJarPath = combinedMinecraftJar;
        } else {
            InstallProfile installProfile;
            try (var installerFs = FileSystems.newFileSystem(installer);
                var is = Files.newBufferedReader(installerFs.getPath("install_profile.json"))) {
                installProfile = Utils.GSON.fromJson(is, InstallProfile.class);
            }

            // [net.neoforged:neoform:<version>:mappings@txt]
            var neoFormVersion = installProfile.data().get("MAPPINGS").client().split(":")[2];

            var mcJarOut = installationFolder.resolve(neoVersion + "-mc.jar");
            var clientJar = installationFolder.resolve("libraries/net/neoforged/neoforge/" + neoVersion + "/neoforge-" + neoVersion + "-client.jar");
            var srgJar = installationFolder.resolve("libraries/net/minecraft/client/" + neoFormVersion + "/client-" + neoFormVersion + "-srg.jar");
            var assetsJar = installationFolder.resolve("libraries/net/minecraft/client/" + neoFormVersion + "/client-" + neoFormVersion + "-extra.jar");
            merge(mcJarOut, assetsJar, mcVersion, clientJar, srgJar);
            Files.deleteIfExists(clientJar);
            mcJarPath = mcJarOut;
        }

        var neoMod = Objects.requireNonNull(ModFileReader.NEOFORGE.read(ModFilePath.create(neoJar, neoJar), "net.neoforged:neoforge", neoVersion));
        var mcMod = Objects.requireNonNull(ModFileReader.NEOFORGE.read(ModFilePath.create(mcJarPath, mcJarPath), "net.minecraft:minecraft", mcVersion));

        return List.of(neoMod, mcMod);
    }

    private static String getMcVersion(String neoVersion) {
        var neoSplit = neoVersion.split("\\.");
        return "1." + neoSplit[0] + (neoSplit[1].equals("0") ? "" : ("." + neoSplit[1]));
    }

    private static int execJar(Path jar, Path workingDir, Object... args) throws IOException {
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

        var proc = new ProcessBuilder()
                .directory(workingDir.toFile())
                .command(command)
                .redirectErrorStream(true)
                .redirectOutput(workingDir.resolve(DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-") + ".log").toFile())
                .start();

        try {
            return proc.waitFor();
        } catch (InterruptedException e) {
            Utils.sneakyThrow(e);
            throw null;
        }
    }

    private record InstallProfile(Map<String, Data> data) {
        private record Data(String client) {
        }
    }

    private static void merge(Path out, Path mcAssets, String mcVersion, Path... classJars) throws IOException {
        var man = new Manifest();

        var ma = man.getMainAttributes();
        ma.put(Attributes.Name.MANIFEST_VERSION, "1.2");
        ma.putValue("FMLModType", "GAMELIBRARY");
        ma.put(Attributes.Name.IMPLEMENTATION_VERSION, mcVersion);

        try (var zout = new JarOutputStream(Files.newOutputStream(out), man);
             var assetsIn = new ZipInputStream(Files.newInputStream(mcAssets))) {

            ZipEntry entry;
            while ((entry = assetsIn.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    zout.putNextEntry(Utils.copyEntry(entry));
                    assetsIn.transferTo(zout);
                    zout.closeEntry();
                }
            }

            Set<String> knownClasses = new LinkedHashSet<>();

            for (Path classJar : classJars) {
                try (var classesIn = new ZipInputStream(Files.newInputStream(classJar))) {
                    while ((entry = classesIn.getNextEntry()) != null) {
                        if (entry.getName().endsWith(".class") && knownClasses.add(entry.getName())) {
                            zout.putNextEntry(Utils.copyEntry(entry));
                            classesIn.transferTo(zout);
                            zout.closeEntry();
                        }
                    }
                }
            }
        }
    }
}

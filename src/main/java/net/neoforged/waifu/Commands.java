/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.waifu;

import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.neoforged.waifu.util.ByteConversion;
import net.neoforged.waifu.util.Utils;
import org.jdbi.v3.core.Jdbi;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Commands {
    public static void onSlashCommandInteraction(final SlashCommandInteractionEvent event, final ExecutorService rescanner) throws Exception {
        switch (event.getFullCommandName()) {
            case "modpacks add" -> addModpack(event, rescanner);
            case "modpacks list" -> {
                final var packs = BotMain.PACKS.read();
                if (packs.isEmpty()) {
                    event.reply("No packs watched!").queue();
                } else {
                    event.deferReply().queue();
                    event.getHook().sendMessage("Watched modpacks:\n" + BotMain.CF.makeRequest(BotMain.getMods(packs))
                            .orElse(List.of())
                            .stream()
                            .map(pack -> "- " + pack.name() + " (" + pack.id() + ")")
                            .collect(Collectors.joining("\n")))
                            .queue();
                }
            }
            case "modpacks remove" -> removeModpack(event);


            case "gameversion add" -> addGameVersion(event, rescanner);
            case "gameversion list" -> // TODO - make better
                    BotMain.GAME_VERSIONS.useHandle(versions -> {
                        if (versions.isEmpty()) {
                            event.reply("No game versions watched!").queue();
                        } else {
                            event.reply("Watched versions:\n" + versions.stream().map(v -> "- " + v).collect(Collectors.joining("\n"))).queue();
                        }
                    });
            case "gameversion remove" -> removeGameVersion(event);

            case "delete-cache" -> onDeleteCache(event);
            case "data-size" -> onDataSize(event);

            case "help" -> event.replyEmbeds(new EmbedBuilder()
                        .setTitle("WhatAmIForgingUp", "https://github.com/NeoForged/WhatAmIForgingUp")
                        .setDescription("A bot used to index Minecraft mods on CurseForge.")
                        .addField("Version", BotMain.VERSION, false)
                        .setColor(Color.GREEN)
                        .build())
                    .queue();
        }
    }

    private static void addModpack(final SlashCommandInteractionEvent event, final ExecutorService rescanner) throws CurseForgeException {
        final Mod pack = BotMain.CF.makeRequest(Requests.getMod(event.getOption("modpack", 0, OptionMapping::getAsInt))).orElse(null);
        if (pack == null || pack.gameId() != Constants.GameIDs.MINECRAFT || pack.classId() != 4471) {
            event.reply("Unknown modpack!").setEphemeral(true).queue();
            return;
        }

        event.reply("Watching modpack. Started indexing, please wait...").queue();

        BotMain.CURRENTLY_COLLECTED.add(String.valueOf(pack.id()));
        BotMain.PACKS.useHandle(v -> v.add(pack.id()));
        rescanner.submit(() -> {
            BotMain.trigger(pack);

            event.getHook().editOriginal("Finished initial indexing.").queue();
        });
    }

    private static void removeModpack(final SlashCommandInteractionEvent event) throws SQLException {
        final var packs = BotMain.PACKS.read();
        final int packId = event.getOption("modpack", 0, OptionMapping::getAsInt);
        if (!packs.contains(packId)) {
            event.reply("Unknown pack!").setEphemeral(true).queue();
            return;
        }

        packs.remove(packId);
        BotMain.PACKS.write();

        if (event.getOption("removedb", false, OptionMapping::getAsBoolean)) {
            try (final var con = Database.initiateDBConnection()) {
                try (final var stmt = con.createStatement()) {
                    stmt.execute("drop schema if exists pack_" + packId + " cascade;");
                }
            }
        }
        event.reply("Pack removed!").queue();
    }

    private static void addGameVersion(final SlashCommandInteractionEvent event, final ExecutorService rescanner) throws CurseForgeException {
        final String gameVersion = event.getOption("version", "", OptionMapping::getAsString);
        if (BotMain.CF.getHelper().getGameVersions(Constants.GameIDs.MINECRAFT).orElse(List.of())
                .stream().flatMap(g -> g.versions().stream())
                .noneMatch(s -> s.equals(gameVersion))) {
            event.reply("Unknown game version!").setEphemeral(true).queue();
            return;
        }

        event.reply("Watching game version. Started indexing, please wait...").queue();
        BotMain.CURRENTLY_COLLECTED.add(gameVersion);
        BotMain.GAME_VERSIONS.useHandle(v -> v.add(gameVersion));
        rescanner.submit(() -> {
            try {
                BotMain.triggerGameVersion(gameVersion);
            } catch (Exception ex) {
                BotMain.LOGGER.error("Encountered exception indexing game version '{}': ", gameVersion, ex);
            }
        });
    }

    private static void removeGameVersion(final SlashCommandInteractionEvent event) throws SQLException {
        final var versions = BotMain.GAME_VERSIONS.read();
        final String versionID = event.getOption("version", "", OptionMapping::getAsString);
        if (!versions.contains(versionID)) {
            event.reply("Unknown game version!").setEphemeral(true).queue();
            return;
        }

        versions.remove(versionID);
        BotMain.GAME_VERSIONS.write();

        if (event.getOption("removedb", false, OptionMapping::getAsBoolean)) {
            try (final var con = Database.initiateDBConnection()) {
                try (final var stmt = con.createStatement()) {
                    stmt.execute("drop schema if exists " + BotMain.computeVersionSchema(versionID) + " cascade;");
                }
            }
        }
        event.reply("Game version removed!").queue();
    }

    private static void onDeleteCache(final SlashCommandInteractionEvent event) throws IOException {
        if (!BotMain.CURRENTLY_COLLECTED.isEmpty()) {
            event.reply("Cannot delete CurseForge cache while indexing is in progress!").setEphemeral(true).queue();
        } else {
            event.reply("Deleting caches...").queue();
            try (final Stream<Path> toDelete = Files.find(ModCollector.DOWNLOAD_CACHE, Integer.MAX_VALUE, (path, basicFileAttributes) -> Files.isRegularFile(path) && path.toString().endsWith(".jar") || path.toString().endsWith(".zip"))) {
                final var itr = toDelete.iterator();
                while (itr.hasNext()) {
                    Files.delete(itr.next());
                }
            }
            event.getHook().editOriginal("Deleted caches!").queue();
        }
    }

    private static void onDataSize(final SlashCommandInteractionEvent event) throws SQLException {
        event.deferReply().queue();
        final long sizeDb;
        try (final var con = Database.initiateDBConnection()) {
            sizeDb = Jdbi.create(con).withHandle(handle -> handle.select("select pg_database_size('" +
                            Utils.last(System.getProperty("db.url").split("/")) + "');")
                    .execute((statementSupplier, ctx) -> {
                        final ResultSet rs = statementSupplier.get().getResultSet();
                        rs.next();
                        return rs.getLong("pg_database_size");
                    }));
        }
        event.getHook().editOriginalEmbeds(new EmbedBuilder()
                        .setTitle("Data size")
                        .addField("File cache size", ByteConversion.formatBest(Utils.size(ModCollector.DOWNLOAD_CACHE)), true)
                        .addField("Database size", ByteConversion.formatBest(sizeDb), true)
                        .build())
                .queue();
    }
}

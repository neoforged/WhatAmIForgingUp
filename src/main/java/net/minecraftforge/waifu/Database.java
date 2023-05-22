/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.waifu;

import net.minecraftforge.metabase.MetabaseClient;
import net.minecraftforge.metabase.params.DatabaseInclusion;
import net.minecraftforge.metabase.params.FieldValues;
import net.minecraftforge.metabase.types.Field;
import net.minecraftforge.metabase.types.Table;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

public class Database {
    public static void updateMetabase(MetabaseClient client, String schemaName) {
        final String[] fullUrl = System.getProperty("db.url").substring("jdbc:postgresql://".length()).split("/", 2);

        CompletableFuture.allOf(client.getDatabases(UnaryOperator.identity())
                .thenApply(databases -> databases.stream().filter(db -> db.details().get("user").getAsString().equals(System.getenv("db.user"))
                        && db.details().get("host").getAsString().equals(fullUrl[0])
                        && db.details().get("dbname").getAsString().equals(fullUrl[1])))
                .thenApply(db -> db.findFirst().orElseThrow())
                .thenCompose(db -> db.syncSchema().thenCompose($ -> client.getDatabase(db.id(), p -> p.include(DatabaseInclusion.TABLES_AND_FIELDS))))
                .thenApply(db -> db.tables().stream().filter(tb -> tb.schema().equals(schemaName)).toList())
                .thenCompose(tbs -> CompletableFuture.allOf(tbs.stream().map(tb -> {
                    if (tb.name().equals("flyway_schema_history")) {
                        return tb.setHidden(true);
                    } else {
                        return tb.update(p -> switch (tb.name()) {
                            case "refs" -> p.withDisplayName("References")
                                    .withDescription("References of fields, methods, classes and annotations");
                            case "inheritance" -> p.withDescription("The class hierarchy of mods");
                            case "projects" ->
                                    p.withDescription("The IDs of the projects that are tracked by this schema");
                            case "modids" -> p.withDisplayName("Mod IDs")
                                    .withDescription("A mapping of text mod IDs to integers in order to save space");
                            default -> null;
                        });
                    }
                }).toArray(CompletableFuture[]::new)).thenApply($ -> tbs))
                .thenCompose(tbs -> {
                    final Table modids = tbs.stream().filter(tb -> tb.name().equals("modids")).findFirst().orElseThrow();
                    final Field target = modids.fields().stream().filter(f -> f.name().equals("modid")).findFirst().orElseThrow();
                    return CompletableFuture.allOf(tbs.stream().filter(tb -> tb.name().equals("inheritance") || tb.name().equals("refs"))
                            .map(tb -> tb.fields().stream().filter(f -> f.name().equals("modid")).findFirst().orElseThrow()
                                    .update(u -> u.setTarget(target).withFieldValues(FieldValues.SEARCH))
                                    .thenCompose(f -> f.setDimension(target.id(), "Mod ID")))
                            .toArray(CompletableFuture[]::new));
                }))
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        BotMain.LOGGER.error("Could not update metabase schema: ", throwable);
                    }
                });
    }

    public static Map.Entry<MigrateResult, Jdbi> createDatabaseConnection(String schemaName) {
        final var flyway = Flyway.configure()
                .dataSource(System.getProperty("db.url") + "?socketTimeout=0&tcpKeepAlive=true&options=-c%20statement_timeout=1h", System.getProperty("db.user"), System.getProperty("db.password"))
                .locations("classpath:db")
                .schemas(schemaName)
                .load();
        final var result = flyway.migrate();

        return Map.entry(result, Jdbi.create(() -> {
                    final Connection connection = initiateDBConnection();
                    connection.setSchema(schemaName);
                    return connection;
                })
                .registerArgument(new AbstractArgumentFactory<AtomicInteger>(Types.INTEGER) {
                    @Override
                    protected Argument build(AtomicInteger value, ConfigRegistry config) {
                        return (position, statement, ctx) -> statement.setInt(position, value.get());
                    }
                })
                .installPlugin(new SqlObjectPlugin()));
    }

    public static Connection initiateDBConnection() throws SQLException {
        final String user = System.getProperty("db.user");
        final String password = System.getProperty("db.password");
        final String url = System.getProperty("db.url");
        return DriverManager.getConnection(url + "?user=" + user + "&password=" + password);
    }
}

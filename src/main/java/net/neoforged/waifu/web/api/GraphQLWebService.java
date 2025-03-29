package net.neoforged.waifu.web.api;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public class GraphQLWebService {
    private record VersionKey(String ver, ModLoader loader) {}
    private final Map<VersionKey, Optional<DatabaseSearchHelper>> helpers = new ConcurrentHashMap<>();

    private final boolean anonymousAccess;
    @Nullable
    private final TokenManager.RateLimit anonymousRateLimit;

    private final AtomicInteger anonymousResetsIn = new AtomicInteger();
    private final Map<String, AtomicInteger> anonymousLimits = new ConcurrentHashMap<>();

    private final MainDatabase db;
    private final TokenManager tokenManager;
    private final GraphQL engine;

    private final Map<String, Optional<TokenRateLimit>> tokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService rateLimitService;

    private record TokenRateLimit(int requests, Duration interval, AtomicInteger resetsIn, AtomicInteger remaining) {}

    public GraphQLWebService(Javalin javalin, MainDatabase db, TokenManager tokenManager, boolean anonymousAccess, @Nullable TokenManager.RateLimit anonymousRateLimit) {
        this.db = db;
        this.tokenManager = tokenManager;
        this.anonymousAccess = anonymousAccess;
        this.anonymousRateLimit = anonymousAccess ? anonymousRateLimit : null;

        this.rateLimitService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("graphql-rate-limiter").factory());

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(getClass().getResourceAsStream("/web/api/schema.graphql"));

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("predicateType", new SchemaDirectiveWiring() {
                    @Override
                    public GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
                        var typeName = environment.getElement().getName();
                        return environment.getElement().transform(b -> {
                            b.field(f -> f.name("not")
                                    .type(GraphQLTypeReference.typeRef(typeName))
                                    .description("Invert the given filter"));
                            b.field(f -> f.name("anyOf")
                                    .type(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(typeName))))
                                    .description("Match if any of the given filters matches"));
                            b.field(f -> f.name("allOf")
                                    .type(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(typeName))))
                                    .description("Match if all of the given filters match"));
                        });
                    }
                })

                .type("Query", builder ->
                        builder.dataFetcher("gameVersion", this::getVersion)
                                .dataFetcher("gameVersions", this::getVersions)
                )
                .type("GameVersion", builder ->
                        builder.dataFetcher("getMods", invoke(Version::getMods))
                                .dataFetcher("getModsById", invoke(Version::getModsById))

                                .dataFetcher("loader", get(Version::loaderAsGraphQLEnum))
                                .dataFetcher("version", get(v -> v.version))
                )
                .build();


        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(SchemaGenerator.Options.defaultOptions().useCommentsAsDescriptions(false)
                .useAppliedDirectivesOnly(true), typeDefinitionRegistry, runtimeWiring);

        this.engine = GraphQL.newGraphQL(graphQLSchema).build();

        javalin.get("/graphql", ctx -> ctx.redirect("/graphql.html", HttpStatus.TEMPORARY_REDIRECT));

        setupEndpoint(javalin);
    }

    private void setupEndpoint(Javalin javalin) {
        if (anonymousRateLimit != null) {
            anonymousResetsIn.set((int) anonymousRateLimit.per().getSeconds());
        }

        for (TokenManager.Token token : tokenManager.getTokens()) {
            addToken(token);
        }

        rateLimitService.scheduleWithFixedDelay(() -> {
            for (var entry : tokens.entrySet()) {
                if (entry.getValue().isPresent()) {
                    var limit = entry.getValue().orElseThrow();
                    if (limit.resetsIn.decrementAndGet() <= 0) {
                        limit.resetsIn.set((int) limit.interval.getSeconds());
                        limit.remaining.set(limit.requests);
                    }
                }
            }

            if (anonymousRateLimit != null) {
                if (anonymousResetsIn.decrementAndGet() <= 0) {
                    anonymousResetsIn.set((int) anonymousRateLimit.per().getSeconds());
                    anonymousLimits.clear();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);

        tokenManager.addAddCallback(this::addToken);
        tokenManager.addRemoveCallback(tokens::remove);

        javalin.post("graphql", ctx -> {
            var token = ctx.header("Authorization");
            if (token == null) {
                if (!anonymousAccess) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Access token not provided"));
                    return;
                }

                if (anonymousRateLimit != null) {
                    ctx.header("x-ratelimit-reset", String.valueOf(anonymousResetsIn.get()));

                    var limit = this.anonymousLimits.get(ctx.ip());
                    if (limit == null) {
                        limit = new AtomicInteger(anonymousRateLimit.requests());
                        anonymousLimits.put(ctx.ip(), limit);
                    }

                    var current = limit.get();
                    if (current <= 0) {
                        ctx.status(HttpStatus.BAD_REQUEST)
                                .header("x-ratelimit-remaining", "0")
                                .json(Map.of("error", "Rate limit (" + anonymousRateLimit.requests() + ") exceeded, try again in " + anonymousResetsIn.get() + " seconds"));
                        return;
                    }

                    var remaining = limit.decrementAndGet();
                    ctx.header("x-ratelimit-remaining", String.valueOf(remaining));
                }
            } else {
                var tokenLimit = tokens.get(token);
                if (tokenLimit == null) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Access token is invalid"));
                    return;
                }

                if (tokenLimit.isPresent()) {
                    var limit = tokenLimit.orElseThrow();

                    ctx.header("x-ratelimit-reset", String.valueOf(limit.resetsIn.get()));

                    var current = limit.remaining.get();
                    if (current <= 0) {
                        ctx.status(HttpStatus.BAD_REQUEST)
                                .header("x-ratelimit-remaining", "0")
                                .json(Map.of("error", "Rate limit (" + limit.requests() + ") exceeded, try again in " + limit.resetsIn.get() + " seconds"));
                        return;
                    }

                    var remaining = limit.remaining().decrementAndGet();
                    ctx.header("x-ratelimit-remaining", String.valueOf(remaining));
                }
            }

            record Body(String query, String operationName, Map<String, Object> variables) {}
            var body = ctx.bodyAsClass(Body.class);
            Map<String, Object> variables = Objects.requireNonNullElse(body.variables(), Map.of());
            ExecutionResult executionResult = engine.execute(in -> in.query(body.query).operationName(body.operationName).variables(variables));
            if (!executionResult.getErrors().isEmpty()) {
                Main.LOGGER.error("Failure during GraphQL query: {}: {}", body, executionResult.getErrors());
                ctx.json(Map.of("error", executionResult.getErrors().get(0).getMessage())).status(HttpStatus.BAD_REQUEST);
                return;
            }
            ctx.json(Map.of("data", executionResult.getData()));
        });
    }

    private void addToken(TokenManager.Token token) {
        tokens.put(token.token(), Optional.ofNullable(token.limit())
                .map(l -> new TokenRateLimit(l.requests(), l.per(), new AtomicInteger((int) l.per().getSeconds()), new AtomicInteger(l.requests()))));
    }

    private Object getVersion(DataFetchingEnvironment env) {
        String ver = env.getArgument("version");
        ModLoader loader = switch ((String) Objects.requireNonNull(env.getArgument("loader"))) {
            case "NeoForge" -> ModLoader.NEOFORGE;
            case "Fabric" -> ModLoader.FABRIC;
            case "Forge" -> ModLoader.FORGE;
            default -> throw null;
        };
        var helper = getHelper(ver, loader);
        return helper == null ? null : new Version(ver, loader);
    }

    private Object getVersions(DataFetchingEnvironment env) {
        return db.getIndexedGameVersions().stream()
                .map(v -> new Version(v.gameVersion(), v.loader()))
                .toList();
    }

    private DataFetcher<?> invoke(BiFunction<Version, DataFetchingEnvironment, ?> getter) {
        return environment -> getter.apply(environment.getSource(), environment);
    }

    private DataFetcher<?> get(Function<Version, ?> getter) {
        return environment -> getter.apply(environment.getSource());
    }

    @Nullable
    private DatabaseSearchHelper getHelper(String version, ModLoader loader) {
        return helpers.computeIfAbsent(new VersionKey(version, loader), ke -> Main.DB_MANAGER.exists(version, loader) ? Optional.of(Main.DB_MANAGER.search(version, loader)) : Optional.empty())
                .orElse(null);
    }

    private class Version {
        private final String version;
        private final ModLoader loader;

        private Version(String version, ModLoader loader) {
            this.version = version;
            this.loader = loader;
        }

        public String loaderAsGraphQLEnum() {
            return switch (loader) {
                case FABRIC -> "Fabric";
                case NEOFORGE -> "NeoForge";
                case FORGE -> "Forge";
            };
        }

        public Object getMods(DataFetchingEnvironment env) {
            return getHelper(version, loader).getMods(env);
        }

        public Object getModsById(DataFetchingEnvironment env) {
            return getHelper(version, loader).getModsById(env);
        }
    }
}

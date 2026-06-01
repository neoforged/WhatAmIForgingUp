package net.neoforged.waifu.web.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonObject;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Description;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SDLDefinition;
import graphql.language.SourceLocation;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.validation.ValidationError;
import io.javalin.config.RoutesConfig;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.platform.PlatformProject;
import net.neoforged.waifu.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class GraphQLWebService {
    private static final String PERMISSIONS_CONTEXT_KEY = "permissions";

    private record VersionKey(String ver, ModLoader loader) {}
    private final Map<VersionKey, Optional<Version>> versions = new ConcurrentHashMap<>();

    private final boolean anonymousAccess;
    @Nullable
    private final TokenManager.RateLimit anonymousRateLimit;
    @Nullable
    private final TokenManager.RateLimit discordRateLimit;
    private final int defaultTimeout;

    private final MainDatabase db;
    private final TokenManager tokenManager;
    private final GraphQL engine;

    private final ThreadLocal<List<Runnable>> cancellationInvokers = ThreadLocal.withInitial(ArrayList::new);

    record TokenInfo(boolean active, int executionTimeout, @Nullable TokenManager.RateLimit limit) {}
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    private final Cache<String, Long> discordTokenToUser = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private final RateLimiter rateLimiter = new RateLimiter();

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("graphql-executor-", 0).factory());

    public GraphQLWebService(RoutesConfig routes, MainDatabase db, TokenManager tokenManager, boolean anonymousAccess, @Nullable TokenManager.RateLimit anonymousRateLimit, @Nullable TokenManager.RateLimit discordRateLimit, int defaultTimeout) {
        this.db = db;
        this.tokenManager = tokenManager;
        this.anonymousAccess = anonymousAccess;
        this.anonymousRateLimit = anonymousAccess ? anonymousRateLimit : null;
        this.discordRateLimit = discordRateLimit;
        this.defaultTimeout = defaultTimeout;

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(getClass().getResourceAsStream("/web/api/schema.graphql"));

        Map<String, SDLDefinition<?>> typesToAdd = new HashMap<>();

        typeDefinitionRegistry.types().forEach((name, bdef) -> {
            if (bdef instanceof ObjectTypeDefinition def) {
                var orderBy = new ArrayList<FieldDefinition>();

                for (FieldDefinition field : def.getFieldDefinitions()) {
                    if (field.hasDirective("paginated") && field.getType() instanceof TypeName type) {
                        var edgeName = type.getName() + "Edge";
                        typesToAdd.computeIfAbsent(edgeName, $ -> ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(edgeName)
                                .description(new Description("An edge of a `" + type.getName() + "`.", SourceLocation.EMPTY, false))
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name("node")
                                        .description(new Description("The item at the end of the edge.", SourceLocation.EMPTY, false))
                                        .type(new NonNullType(type))
                                        .build())
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name("cursor")
                                        .description(new Description("The cursor ID of the element.", SourceLocation.EMPTY, false))
                                        .type(new NonNullType(new TypeName("ID")))
                                        .build())
                                .build());

                        typesToAdd.computeIfAbsent(type.getName() + "Connection", $ -> ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(type.getName() + "Connection")
                                .description(new Description("A connection (list) composed of `" + type.getName() + "`.", SourceLocation.EMPTY, false))
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name("pageInfo")
                                        .description(new Description("Information to aid in pagination.", SourceLocation.EMPTY, false))
                                        .type(new NonNullType(new TypeName("PageInfo")))
                                        .build())
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name("edges")
                                        .description(new Description("A list of edges.", SourceLocation.EMPTY, false))
                                        .type(new NonNullType(new ListType(new NonNullType(new TypeName(edgeName)))))
                                        .build())
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name("count")
                                        .description(new Description("Identifies the amount of items in the returned edges.", SourceLocation.EMPTY, false))
                                        .type(new NonNullType(new TypeName("Int")))
                                        .build())
                                .build());
                    }
                    if (field.hasDirective("orderBy")) {
                        orderBy.add(field);
                    }
                }

                if (!orderBy.isEmpty()) {
                    typesToAdd.computeIfAbsent(def.getName() + "Ordering", $ -> InputObjectTypeDefinition.newInputObjectDefinition()
                            .name(def.getName() + "Ordering")
                            .directive(new Directive("oneOf"))
                            .description(new Description("Type used to order `" + def.getName() + "`s", SourceLocation.EMPTY, false))
                            .inputValueDefinitions(orderBy.stream().map(f -> InputValueDefinition.newInputValueDefinition()
                                    .name(f.getName())
                                    .description(new Description("Order by " + f.getName(), SourceLocation.EMPTY, false))
                                    .type(new TypeName("OrderMethod"))
                                    .build())
                                    .toList())
                            .build());
                }
            }
        });

        typesToAdd.values().forEach(typeDefinitionRegistry::add);

        RuntimeWiring.Builder runtimeWiring = RuntimeWiring.newRuntimeWiring()
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
                            b.field(f -> f.name("noneOf")
                                    .type(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLTypeReference.typeRef(typeName))))
                                    .description("Match if none of the given filters match.\nPrefer using this instead of `not(anyOf(...))` as it is generally faster."));
                            b.field(f -> f.name("isNull")
                                    .type(GraphQLTypeReference.typeRef("Boolean"))
                                    .description("If `true`, match if the value tested is null. Otherwise, match if non-null."));
                        });
                    }
                })
                .directive("implement", new SchemaDirectiveWiring() {
                    @Override
                    public GraphQLObjectType onObject(SchemaDirectiveWiringEnvironment<GraphQLObjectType> environment) {
                        List<String> ifaces = environment.getAppliedDirective().getArgument("interfaces").getValue();
                        return environment.getElement().transform(b -> {
                            var fields = new ArrayList<>(environment.getElement().getFields());
                            fields.removeIf(f -> f.getName().equals("_"));

                            ifaces.stream()
                                    .map(i -> environment.getRegistry().getType(i, InterfaceTypeDefinition.class))
                                    .flatMap(Optional::stream)
                                    .forEach(def -> {
                                        def.getFieldDefinitions().forEach(f -> fields.add(
                                                GraphQLWebService.onField(typesToAdd, field(f))
                                        ));
                                        b.withInterface(GraphQLTypeReference.typeRef(def.getName()));
                                    });

                            b.replaceFields(fields);
                        });
                    }

                    @Override
                    public GraphQLInterfaceType onInterface(SchemaDirectiveWiringEnvironment<GraphQLInterfaceType> environment) {
                        List<String> ifaces = environment.getAppliedDirective().getArgument("interfaces").getValue();
                        return environment.getElement().transform(b -> {
                            var fields = new ArrayList<>(environment.getElement().getFields());
                            fields.removeIf(f -> f.getName().equals("_"));

                            ifaces.stream()
                                    .map(i -> environment.getRegistry().getType(i, InterfaceTypeDefinition.class))
                                    .flatMap(Optional::stream)
                                    .forEach(def -> {
                                        def.getFieldDefinitions().forEach(f -> fields.add(
                                                GraphQLWebService.onField(typesToAdd, field(f))
                                        ));
                                        b.withInterface(GraphQLTypeReference.typeRef(def.getName()));
                                    });

                            b.replaceFields(fields);
                        });
                    }
                })
                .directive("paginated", new SchemaDirectiveWiring() {
                    @Override
                    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
                        return environment.getElement().transform(b -> {
                            var type = ((GraphQLNamedSchemaElement) environment.getElement().getType()).getName();
                            b.type(new GraphQLNonNull(new GraphQLTypeReference(type + "Connection")));
                            b.argument(GraphQLArgument.newArgument()
                                    .name("first")
                                    .description("Returns the first _n_ elements from the list.")
                                    .type(new GraphQLTypeReference("Int"))
                                    .build());
                            b.argument(GraphQLArgument.newArgument()
                                    .name("after")
                                    .description("Returns the elements in the list that come after the specified cursor.")
                                    .type(new GraphQLTypeReference("ID"))
                                    .build());
                            b.argument(GraphQLArgument.newArgument()
                                    .name("last")
                                    .description("Returns the last _n_ elements from the list.")
                                    .type(new GraphQLTypeReference("Int"))
                                    .build());
                            b.argument(GraphQLArgument.newArgument()
                                    .name("before")
                                    .description("Returns the elements in the list that come before the specified cursor.")
                                    .type(new GraphQLTypeReference("ID"))
                                    .build());
                        });
                    }
                })
                .directiveWiring(new SchemaDirectiveWiring() {
                    @Override
                    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
                        return GraphQLWebService.onField(typesToAdd, environment.getElement());
                    }
                })

                .codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                        .defaultDataFetcher(env -> new ConsiderAliasDataFetcher<>(env.getFieldDefinition().getName())))

                .scalar(ExtendedScalars.DateTime)
                .scalar(ExtendedScalars.Json)

                .type("Query", builder ->
                        builder.dataFetcher("gameVersion", this::getVersion)
                                .dataFetcher("gameVersions", this::getVersions)
                )
                .type("Mutation", builder ->
                        builder.dataFetcher("stopIndexingGameVersion", this::stopIndexing))
                .type("GameVersion", builder ->
                        builder.dataFetcher("mods", dbHelper(DatabaseSearchHelper::getMods))
                                .dataFetcher("modsById", dbHelper(DatabaseSearchHelper::getModsById))
                                .dataFetcher("classes", dbHelper(DatabaseSearchHelper::getClasses))
                                .dataFetcher("class", dbHelper(DatabaseSearchHelper::getClass))
                                .dataFetcher("classDefinitions", dbHelper(DatabaseSearchHelper::getClassDefinitions))

                                .dataFetcher("recipes", dbHelper(DatabaseSearchHelper::getRecipes))
                                .dataFetcher("dataMaps", dbHelper(DatabaseSearchHelper::getDataMaps))
                                .dataFetcher("tagEntries", dbHelper(DatabaseSearchHelper::getTagEntries))
                                .dataFetcher("dataFiles", dbHelper(DatabaseSearchHelper::getDataFiles))
                                .dataFetcher("enumExtensions", dbHelper(DatabaseSearchHelper::getEnumExtensions))

                                .dataFetcher("loader", get(Version::loaderAsGraphQLEnum))
                                .dataFetcher("version", get(v -> v.version))

                                // Internal
                                .dataFetcher("_modInformation", dbHelper(DatabaseSearchHelper::getInternalModInformation))
                )

                // Internal types
                .type("IntModInformation", builder ->
                        builder.dataFetcher("curseforge", this.<Map<String, Object>>fetcher(map -> Objects.equals(map.get("cpid"), null) ? null : Main.CURSE_FORGE_PLATFORM.getProjectById(map.get("cpid"))))
                                .dataFetcher("modrinth", this.<Map<String, Object>>fetcher(map -> Objects.equals(map.get("mpid"), null) ? null : Main.MODRINTH_PLATFORM.getProjectById(map.get("mpid"))))
                )
                .type("IntPlatformInformation", builder ->
                        builder.dataFetcher("projectUrl", fetcher(PlatformProject::getUrl))
                                .dataFetcher("iconUrl", fetcher(PlatformProject::getIconUrl))
                                .dataFetcher("sourceUrl", fetcher(PlatformProject::getSourceUrl))
                                .dataFetcher("issuesUrl", fetcher(PlatformProject::getIssuesUrl))
                                .dataFetcher("title", fetcher(PlatformProject::getTitle))
                                .dataFetcher("description", fetcher(PlatformProject::getDescription))
                                .dataFetcher("downloads", fetcher(PlatformProject::getDownloads))
                                .dataFetcher("fileDownloadUrl", environment -> {
                                    var project = environment.<PlatformProject>getSource();
                                    assert project != null;
                                    var latestFile = project.getLatestFile(environment.getArgument("version"), getLoader(environment.getArgumentOrDefault("loader", "")));
                                    return latestFile == null ? null : latestFile.getDownloadUrl();
                                }));

        typesToAdd.forEach((key, def) -> {
            if (key.endsWith("Edge")) {
                runtimeWiring.type(key, b -> b
                        .dataFetcher("cursor", environment -> {
                            Map<String, Object> obj = environment.getSource();
                            // noinspection DataFlowIssue
                            return Utils.cursorEncode(obj.get("id"));
                        })
                        .dataFetcher("node", DataFetchingEnvironment::getSource));
            }
        });

        for (InterfaceTypeDefinition type : typeDefinitionRegistry.getTypes(InterfaceTypeDefinition.class)) {
            runtimeWiring.type(type.getName(), b -> b.typeResolver(env -> (GraphQLObjectType) env.getFieldType()));
        }

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(SchemaGenerator.Options.defaultOptions().useCommentsAsDescriptions(false)
                .useAppliedDirectivesOnly(true), typeDefinitionRegistry, runtimeWiring.build());

        this.engine = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(new Instrumentation() {
                    @Override
                    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
                        return SimpleInstrumentationContext.whenCompleted((err, ex) -> {
                            if (!err.isEmpty() || ex != null) return; // No need to check when the query provided is invalid

                            QueryTraverser queryTraverser = QueryTraverser.newQueryTraverser().document(parameters.getDocument()).schema(parameters.getSchema()).variables(parameters.getVariables()).build();
                            queryTraverser.visitDepthFirst(new QueryVisitor() {
                                @Override
                                public void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
                                    var directive = queryVisitorFieldEnvironment.getFieldDefinition().getAppliedDirective("requires");
                                    if (directive != null) {
                                        var permission = directive.getArgument("permission");
                                        if (permission != null && permission.getValue() != null) {
                                            var permissions = parameters.getGraphQLContext().<Collection<String>>getOrDefault(PERMISSIONS_CONTEXT_KEY, List.of());
                                            if (!permissions.contains(permission.<String>getValue())) {
                                                throw new AbortExecutionException("Field '" + queryVisitorFieldEnvironment.getFieldDefinition().getName() + "' requires permissions the current user does not have: " + permission.getValue());
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

                                }

                                @Override
                                public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

                                }
                            });
                        });
                    }
                })
                .build();

        routes.get("/graphql", ctx -> ctx.redirect("/graphql.html", HttpStatus.TEMPORARY_REDIRECT));

        setupEndpoint(routes);
    }

    private void setupEndpoint(RoutesConfig javalin) {
        for (TokenManager.Token token : tokenManager.getTokens()) {
            addToken(token);
        }

        tokenManager.addAddCallback(this::addToken);
        tokenManager.addRemoveCallback(tokens::remove);

        javalin.post("/api/graphql", ctx -> {
            var token = ctx.header("Authorization");
            var executionTimeout = defaultTimeout;

            if (token == null) {
                if (!anonymousAccess) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Access token not provided"));
                    return;
                }

                if (anonymousRateLimit != null && rateLimiter.rateLimitExceeded(ctx, ctx.ip(), anonymousRateLimit)) {
                    return;
                }
            } else if (token.startsWith("Discord ")) {
                if (discordRateLimit == null) {
                    if (!anonymousAccess) {
                        ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Cannot use Discord token based authentication"));
                        return;
                    }

                    if (anonymousRateLimit != null && rateLimiter.rateLimitExceeded(ctx, ctx.ip(), anonymousRateLimit)) {
                        return;
                    }
                } else {
                    var discordToken = token.substring("Discord ".length());
                    var identifiedUser = identifyDiscordUser(discordToken);
                    if (identifiedUser == null) {
                        ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Cannot identify user from Discord token"));
                        return;
                    }

                    if (rateLimiter.rateLimitExceeded(ctx, "Discord " + identifiedUser, discordRateLimit)) {
                        return;
                    }
                }
            } else {
                var tokenInfo = tokens.get(token);
                if (tokenInfo == null) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Access token is invalid"));
                    return;
                }
                if (!tokenInfo.active()) {
                    ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Access token is inactive"));
                    return;
                }

                if (tokenInfo.limit() != null && rateLimiter.rateLimitExceeded(ctx, "Bearer " + token, tokenInfo.limit())) {
                    return;
                }

                executionTimeout = tokenInfo.executionTimeout();
            }

            record Body(String query, String operationName, Map<String, Object> variables) {}
            var body = ctx.bodyAsClass(Body.class);
            Map<String, Object> variables = Objects.requireNonNullElse(body.variables(), Map.of());

            var cancellation = new LinkedList<Runnable>();

            var future = executor.submit(() -> {
                cancellationInvokers.set(cancellation);
                return engine.execute(in -> in.query(body.query).operationName(body.operationName).variables(variables));
            });
            try {
                ExecutionResult executionResult;
                if (executionTimeout <= 0) {
                    executionResult = future.get();
                } else {
                    executionResult = future.get(executionTimeout, TimeUnit.SECONDS);
                }

                if (!executionResult.getErrors().isEmpty()) {
                    Main.LOGGER.error("Failure during GraphQL query: {}: {}", body, executionResult.getErrors());
                    ctx.json(Map.of("error", executionResult.getErrors().get(0).getMessage())).status(HttpStatus.BAD_REQUEST);
                    return;
                }
                ctx.json(Utils.GSON.toJson(Map.of("data", executionResult.getData())));
            } catch (TimeoutException timeout) {
                ctx.json(Map.of("error", "Execution timed out"));
                cancellation.forEach(Runnable::run);
                future.cancel(true);
            }
        });
    }

    private void addToken(TokenManager.Token token) {
        if (token.token() == null) return;

        tokens.put(token.token(), new TokenInfo(
                token.active(),
                token.executionTimeout() == null ? defaultTimeout : token.executionTimeout(),
                token.limit()
        ));
    }

    private static final URI DISCORD_ME = URI.create("https://discord.com/api/v10/users/@me");
    @Nullable
    private Long identifyDiscordUser(String token) {
        return discordTokenToUser.get(token, $ -> {
            final HttpResponse<String> response;
            try {
                response = Main.HTTP_CLIENT.send(HttpRequest.newBuilder(DISCORD_ME)
                        .header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                return null;
            }

            if (response.statusCode() == 200) {
                var body = Utils.GSON.fromJson(response.body(), JsonObject.class);
                if (body.has("id")) {
                    return body.get("id").getAsLong();
                }
            }
            return null;
        });
    }

    private Object getVersion(DataFetchingEnvironment env) {
        String ver = env.getArgument("version");
        ModLoader loader = getLoader(env.getArgument("loader"));
        return getVersion(ver, loader);
    }

    private Object getVersions(DataFetchingEnvironment env) {
        var stream = db.getIndexedGameVersions().stream();
        String loader = env.getArgument("loader");
        if (loader != null) {
            var load = getLoader(loader);
            stream = stream.filter(v -> v.loader().equals(load));
        }
        return stream
                .map(v -> getVersion(v.gameVersion(), v.loader()))
                .toList();
    }

    private boolean stopIndexing(DataFetchingEnvironment env) {
        final String version = env.getArgument("version");
        final ModLoader loader = getLoader(env.getArgumentOrDefault("loader", ""));

        final boolean success = db.stopIndexingVersion(version, loader);
        if (success) {
            var future = Main.getService(version, loader);
            if (future != null) {
                future.cancel(env.getArgumentOrDefault("force", false));
            }
        }

        return success;
    }

    static ModLoader getLoader(String argument) {
        return switch (argument) {
            case "NeoForge" -> ModLoader.NEOFORGE;
            case "Fabric" -> ModLoader.FABRIC;
            case "Forge" -> ModLoader.FORGE;
            default -> throw new IllegalArgumentException("Unknown loader '" + argument + "'");
        };
    }

    private DataFetcher<?> dbHelper(BiFunction<DatabaseSearchHelper, DataFetchingEnvironment, ?> getter) {
        return environment -> getter.apply(((Version)environment.getSource()).helper(), environment);
    }

    private DataFetcher<?> get(Function<Version, ?> getter) {
        return environment -> getter.apply(environment.getSource());
    }

    private <T> DataFetcher<?> fetcher(Function<T, ?> getter) {
        return environment -> getter.apply(environment.getSource());
    }

    @Nullable
    private Version getVersion(String version, ModLoader loader) {
        return versions.computeIfAbsent(new VersionKey(version, loader), ke -> Main.DB_MANAGER.exists(version, loader) ?
                        Optional.of(new Version(version, loader, Main.DB_MANAGER.search(version, loader, ivk -> cancellationInvokers.get().add(ivk)))) :
                        Optional.empty())
                .orElse(null);
    }

    private record Version(String version, ModLoader loader, DatabaseSearchHelper helper) {
        public String loaderAsGraphQLEnum() {
            return switch (loader) {
                case FABRIC -> "Fabric";
                case NEOFORGE -> "NeoForge";
                case FORGE -> "Forge";
            };
        }
    }

    private static GraphQLFieldDefinition onField(Map<String, SDLDefinition<?>> typesToAdd, GraphQLFieldDefinition element) {
        if (unwrap(element.getType()) instanceof GraphQLList list && unwrap(list.getWrappedType()) instanceof GraphQLNamedType named) {
            if (typesToAdd.containsKey(named.getName() + "Ordering")) {
                return element.transform(builder -> builder
                        .argument(GraphQLArgument.newArgument()
                                .name("order")
                                .description("Optional ordering to apply to the returned elements")
                                .type(new GraphQLTypeReference(named.getName() + "Ordering"))
                                .build()));
            }
        }
        return element;
    }

    private static GraphQLFieldDefinition field(FieldDefinition f) {
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(f.getName())
                .description(description(f.getDescription()))
                .type(GraphQLWebService.<GraphQLOutputType>type(f.getType()))
                .arguments(f.getInputValueDefinitions().stream()
                        .map(d -> GraphQLArgument.newArgument()
                                .description(description(d.getDescription()))
                                .name(d.getName())
                                .type(type(d.getType()))
                                .build())
                        .toList())
                .replaceDirectives(f.getDirectives().stream()
                        .map(d -> GraphQLDirective.newDirective()
                                .name(d.getName())
                                .replaceArguments(d.getArguments().stream()
                                        .map(a -> GraphQLArgument.newArgument()
                                                .name(a.getName())
                                                .defaultValueLiteral(a.getValue())
                                                .build())
                                        .toList())
                                .build())
                        .toList())
                .build();
    }

    private static String description(Description d) {
        return d == null ? null : d.getContent();
    }

    private static <T> T type(Type<?> tp) {
        return (T) switch (tp) {
            case NonNullType n -> GraphQLNonNull.nonNull(type(n.getType()));
            case ListType l -> GraphQLList.list(type(l.getType()));
            case TypeName nm -> GraphQLTypeReference.typeRef(nm.getName());
            default -> null;
        };
    }

    private static GraphQLType unwrap(GraphQLType output) {
        return output instanceof GraphQLNonNull nn ? nn.getWrappedType() : output;
    }

    private static final class ConsiderAliasDataFetcher<T> extends PropertyDataFetcher<T> {
        public ConsiderAliasDataFetcher(String propertyName) {
            super(propertyName);
        }

        @Override
        public T get(GraphQLFieldDefinition fieldDefinition, Object source, Supplier<DataFetchingEnvironment> environmentSupplier) throws Exception {
            if (source instanceof Map<?,?> mp) {
                var val = mp.get(getPropertyName());
                if (val != null) {
                    return (T) val;
                }

                var fld = environmentSupplier.get().getField();
                if (fld.getAlias() != null) {
                    val = mp.get("$" + fld.getAlias());
                    if (val != null) {
                        return (T) val;
                    }
                }

                return null;
            }
            return super.get(fieldDefinition, source, environmentSupplier);
        }
    }
}

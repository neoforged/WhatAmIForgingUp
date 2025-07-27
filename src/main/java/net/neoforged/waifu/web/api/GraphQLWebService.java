package net.neoforged.waifu.web.api;

import graphql.ExecutionResult;
import graphql.GraphQL;
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
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import net.neoforged.waifu.Main;
import net.neoforged.waifu.MainDatabase;
import net.neoforged.waifu.db.DatabaseSearchHelper;
import net.neoforged.waifu.platform.ModLoader;
import net.neoforged.waifu.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class GraphQLWebService {
    private record VersionKey(String ver, ModLoader loader) {}
    private final Map<VersionKey, Optional<DatabaseSearchHelper>> helpers = new ConcurrentHashMap<>();

    private final boolean anonymousAccess;
    @Nullable
    private final TokenManager.RateLimit anonymousRateLimit;
    private final int defaultTimeout;

    private final AtomicInteger anonymousResetsIn = new AtomicInteger();
    private final Map<String, AtomicInteger> anonymousLimits = new ConcurrentHashMap<>();

    private final MainDatabase db;
    private final TokenManager tokenManager;
    private final GraphQL engine;

    private final ThreadLocal<List<Runnable>> cancellationInvokers = ThreadLocal.withInitial(ArrayList::new);

    record TokenInfo(int executionTimeout, Optional<TokenRateLimit> rateLimit) {}
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService rateLimitService;

    private record TokenRateLimit(int requests, Duration interval, AtomicInteger resetsIn, AtomicInteger remaining) {}

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("graphql-executor-", 0).factory());

    public GraphQLWebService(Javalin javalin, MainDatabase db, TokenManager tokenManager, boolean anonymousAccess, @Nullable TokenManager.RateLimit anonymousRateLimit, int defaultTimeout) {
        this.db = db;
        this.tokenManager = tokenManager;
        this.anonymousAccess = anonymousAccess;
        this.anonymousRateLimit = anonymousAccess ? anonymousRateLimit : null;
        this.defaultTimeout = defaultTimeout;

        this.rateLimitService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("graphql-rate-limiter").factory());

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
                .type("GameVersion", builder ->
                        builder.dataFetcher("mods", invoke(Version::getMods))
                                .dataFetcher("modsById", invoke(Version::getModsById))
                                .dataFetcher("classes", invoke(Version::getClasses))
                                .dataFetcher("classDefinitions", invoke(Version::getClassDefinitions))

                                .dataFetcher("recipes", invoke(Version::getRecipes))

                                .dataFetcher("loader", get(Version::loaderAsGraphQLEnum))
                                .dataFetcher("version", get(v -> v.version))
                );

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
                var rateLimit = entry.getValue().rateLimit();
                if (rateLimit.isPresent()) {
                    var limit = rateLimit.orElseThrow();
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
            var executionTimeout = defaultTimeout;

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
                        ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                                .header("x-ratelimit-remaining", "0")
                                .json(Map.of("error", "Rate limit (" + anonymousRateLimit.requests() + ") exceeded, try again in " + anonymousResetsIn.get() + " seconds"));
                        return;
                    }

                    var remaining = limit.decrementAndGet();
                    ctx.header("x-ratelimit-remaining", String.valueOf(remaining));
                }
            } else {
                var tokenInfo = tokens.get(token);
                if (tokenInfo == null) {
                    ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", "Access token is invalid"));
                    return;
                }

                executionTimeout = tokenInfo.executionTimeout();

                if (tokenInfo.rateLimit().isPresent()) {
                    var limit = tokenInfo.rateLimit().orElseThrow();

                    ctx.header("x-ratelimit-reset", String.valueOf(limit.resetsIn.get()));

                    var current = limit.remaining.get();
                    if (current <= 0) {
                        ctx.status(HttpStatus.TOO_MANY_REQUESTS)
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

            var cancellation = new LinkedList<Runnable>();

            var future = executor.submit(() -> {
                cancellationInvokers.set(cancellation);
                return engine.execute(in -> in.query(body.query).operationName(body.operationName).variables(variables));
            });
            try {
                ExecutionResult executionResult;
                if (executionTimeout < 0) {
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
        tokens.put(token.token(), new TokenInfo(
                token.executionTimeout() == null ? defaultTimeout : token.executionTimeout(),
                Optional.ofNullable(token.limit())
                        .map(l -> new TokenRateLimit(l.requests(), l.per(), new AtomicInteger((int) l.per().getSeconds()), new AtomicInteger(l.requests())))
        ));
    }

    private Object getVersion(DataFetchingEnvironment env) {
        String ver = env.getArgument("version");
        ModLoader loader = getLoader(env.getArgument("loader"));
        var helper = getHelper(ver, loader);
        return helper == null ? null : new Version(ver, loader);
    }

    private Object getVersions(DataFetchingEnvironment env) {
        var stream = db.getIndexedGameVersions().stream();
        var loader = env.getArguments().get("loader");
        if (loader != null) {
            var load = getLoader(loader);
            stream = stream.filter(v -> v.loader().equals(load));
        }
        return stream
                .map(v -> new Version(v.gameVersion(), v.loader()))
                .toList();
    }

    private ModLoader getLoader(Object argument) {
        return switch ((String) argument) {
            case "NeoForge" -> ModLoader.NEOFORGE;
            case "Fabric" -> ModLoader.FABRIC;
            case "Forge" -> ModLoader.FORGE;
            default -> throw null;
        };
    }

    private DataFetcher<?> invoke(BiFunction<Version, DataFetchingEnvironment, ?> getter) {
        return environment -> getter.apply(environment.getSource(), environment);
    }

    private DataFetcher<?> get(Function<Version, ?> getter) {
        return environment -> getter.apply(environment.getSource());
    }

    @Nullable
    private DatabaseSearchHelper getHelper(String version, ModLoader loader) {
        return helpers.computeIfAbsent(new VersionKey(version, loader), ke -> Main.DB_MANAGER.exists(version, loader) ? Optional.of(Main.DB_MANAGER.search(version, loader, ivk -> cancellationInvokers.get().add(ivk))) : Optional.empty())
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

        public Object getClasses(DataFetchingEnvironment env) {
            return getHelper(version, loader).getClasses(env);
        }

        public Object getClassDefinitions(DataFetchingEnvironment env) {
            return getHelper(version, loader).getClassDefinitions(env);
        }

        public Object getRecipes(DataFetchingEnvironment env) {
            return getHelper(version, loader).getRecipes(env);
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

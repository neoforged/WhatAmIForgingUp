package net.neoforged.waifu.db.sql.search;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@FunctionalInterface
public interface SqlFilter {
    FilterType BOOL_FILTER = in -> make((field, ctx) -> field + " is " + (((Boolean) in).toString()), lhs -> lhs + " == " + (((Boolean) in).toString()));

    FilterType INT_FILTER = builder()
            .castFromJson("%s::int"::formatted)

            .filter("lessThan", SqlFilter::smallerThan)
            .filter("lessThanOrEqual", SqlFilter::smallerThanOrEqual)

            .filter("greaterThan", SqlFilter::greaterThan)
            .filter("greaterThanOrEqual", SqlFilter::greaterThanOrEqual)

            .filter("isEven", SqlFilter::isEven)

            .transform("abs", "abs(%s)"::formatted, "%s.abs()"::formatted)

            .build();

    FilterType STRING_FILTER = builder()
            .castFromJson("left(right(%s::text, -1), -1)"::formatted)

            .filter("matches", SqlFilter::matches)
            .filter("startsWith", SqlFilter::startsWith)

            .transform("length", "length(%s)"::formatted, null, INT_FILTER)

            .build();

    FilterType JSON_FILTER = builder()
            .filter("pathExists", SqlFilter::jsonpath_exists)
            .filter("extract", SqlFilter::jsonpath_extract)
            .build();

    FilterType DATE_TIME_FILTER = builder()
            .filter("after", SqlFilter::greaterThan)
            .filter("before", SqlFilter::smallerThan)
            .build();

    String buildSql(String field, SqlSearchBuilder ctx);

    @Nullable
    default String buildJson(String lhs) {
        return null;
    }

    static SqlFilter allOf(List<SqlFilter> ops) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlSearchBuilder ctx) {
                return ops.stream().map(o -> "(" + o.buildSql(field, ctx) + ")").collect(Collectors.joining(" and "));
            }

            @Override
            @SuppressWarnings("DuplicatedCode")
            public @Nullable String buildJson(String lhs) {
                var jsonValues = new ArrayList<String>();
                for (var op : ops) {
                    var built = op.buildJson(lhs);
                    if (built == null) {
                        return null;
                    }

                    jsonValues.add("(" + built + ")");
                }
                return String.join(" && ", jsonValues);
            }
        };
    }

    static SqlFilter anyOf(List<SqlFilter> ops) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlSearchBuilder ctx) {
                return ops.stream().map(o -> "(" + o.buildSql(field, ctx) + ")").collect(Collectors.joining(" or "));
            }

            @Override
            @SuppressWarnings("DuplicatedCode")
            public @Nullable String buildJson(String lhs) {
                var jsonValues = new ArrayList<String>();
                for (var op : ops) {
                    var built = op.buildJson(lhs);
                    if (built == null) {
                        return null;
                    }

                    jsonValues.add("(" + built + ")");
                }
                return String.join(" || ", jsonValues);
            }
        };
    }

    static SqlFilter not(SqlFilter op) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlSearchBuilder ctx) {
                return "not (" + op.buildSql(field, ctx) + ")";
            }

            @Override
            public @Nullable String buildJson(String lhs) {
                var built = op.buildJson(lhs);
                return built == null ? null : ("!(" + built + ")");
            }
        };
    }

    static SqlFilter eq(Object value) {
        return make((field, ctx) -> field + " = " + ctx.insert(value), lhs -> lhs + " == \"" + value + "\"");
    }

    static SqlFilter isIn(Object value) {
        return (field, ctx) -> field + " = any(" + ctx.insert(value) + ")";
    }

    static SqlFilter matches(String regex) {
        return make((field, ctx) -> field + " ~ " + ctx.insert(regex), lhs -> lhs + " like_regex \"" + regex + "\"");
    }

    static SqlFilter startsWith(String str) {
        return make((field, ctx) -> "starts_with(" + field + ", " + ctx.insert(str) + ")", lhs -> lhs + " starts with \"" + str + "\"");
    }

    static SqlFilter greaterThan(Object val) {
        return make((field, ctx) -> field + " > " + ctx.insert(val), lhs -> lhs + " > " + val);
    }

    static SqlFilter greaterThanOrEqual(Object val) {
        return make((field, ctx) -> field + " >= " + ctx.insert(val), lhs -> lhs + " >= " + val);
    }

    static SqlFilter smallerThan(Object val) {
        return make((field, ctx) -> field + " < " + ctx.insert(val), lhs -> lhs + " < " + val);
    }

    static SqlFilter smallerThanOrEqual(Object val) {
        return make((field, ctx) -> field + " <= " + ctx.insert(val), lhs -> lhs + " <= " + val);
    }

    static SqlFilter isEven(boolean even) {
        var checkValue = even ? 0 : 1;
        return make((field, ctx) -> field + " % 2 = " + checkValue, lhs -> lhs + " % 2 == " + checkValue);
    }

    static SqlFilter jsonpath_exists(String path) {
        return make(
                (field, ctx) -> "jsonb_path_exists(" + field + ", " + ctx.insert(path) + "::jsonpath)",
                lhs -> lhs + " ? (" + path + ")"
        );
    }

    @SuppressWarnings("unchecked")
    static SqlFilter jsonpath_extract(Map<String, Object> config) {
        var path = (String) config.get("path");
        var cast = ((Map<String, Object>) config.get("as")).entrySet().stream().findFirst().orElseThrow();
        var type = switch (cast.getKey()) {
            case "string" -> STRING_FILTER;
            case "int" -> INT_FILTER;
            default -> throw new IllegalArgumentException("Unknown type " + cast.getKey());
        };
        return jsonpathPredicate(path, type, type.parse(cast.getValue()));
    }

    static SqlFilter jsonpathPredicate(String path, FilterType filterType, SqlFilter filter) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlSearchBuilder ctx) {
                // Try to build a query that uses @@ first, if possible, to benefit from indexing
                var asJson = filter.buildJson(path);
                if (asJson != null) {
                    return field + " @@ (" + ctx.insert(asJson) + "::jsonpath)";
                }

                // if not we will use a subquery
                return "exists (select 0 from jsonb_path_query(" + field + ", " + ctx.insert(path) + "::jsonpath) as elem where " +
                        filter.buildSql(filterType.castFromJson("elem"), ctx) + ")";
            }

            @Override
            public @Nullable String buildJson(String lhs) {
                return filter.buildJson(path);
            }
        };
    }

    static SqlFilter make(BiFunction<String, SqlSearchBuilder, String> sql, Function<String, String> json) {
        return new SqlFilter() {
            @Override
            public String buildSql(String field, SqlSearchBuilder ctx) {
                return sql.apply(field, ctx);
            }

            @Override
            public String buildJson(String lhs) {
                return json.apply(lhs);
            }
        };
    }

    @FunctionalInterface
    interface FilterType {
        SqlFilter parse(Object in);

        default String castFromJson(String variable) {
            return variable;
        }
    }

    static TypeBuilder builder() {
        return new TypeBuilder();
    }

    class TypeBuilder {
        private UnaryOperator<String> jsonCast = UnaryOperator.identity();
        private final Map<String, Function<Object, SqlFilter>> filters = new HashMap<>();

        public TypeBuilder castFromJson(UnaryOperator<String> cast) {
            this.jsonCast = cast;
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> TypeBuilder filter(String type, Function<T, SqlFilter> filterFunction) {
            filters.put(type, (Function) filterFunction);
            return this;
        }

        public TypeBuilder transform(String type, UnaryOperator<String> transformer, @Nullable UnaryOperator<String> jsonTransformer) {
            return transform(type, transformer, jsonTransformer, this::parse);
        }

        public TypeBuilder transform(String type, UnaryOperator<String> transformer, @Nullable UnaryOperator<String> jsonTransformer, FilterType subFilter) {
            filters.put(type, o -> new SqlFilter() {
                @Override
                public String buildSql(String field, SqlSearchBuilder ctx) {
                    return subFilter.parse(o).buildSql(transformer.apply(field), ctx);
                }

                @Override
                public @Nullable String buildJson(String lhs) {
                    return jsonTransformer == null ? null : jsonTransformer.apply(lhs);
                }
            });
            return this;
        }

        @SuppressWarnings("unchecked")
        private SqlFilter parse(Object in) {
            if (in instanceof Map<?, ?> filter) {
                var filterEntry = filter.entrySet().stream().findFirst().orElse(null);
                assert filterEntry != null;

                return switch ((String) filterEntry.getKey()) {
                    case "equals" -> SqlFilter.eq(filterEntry.getValue());
                    case "isIn" -> SqlFilter.isIn(filterEntry.getValue());
                    case "not" -> SqlFilter.not(parse(filterEntry.getValue()));

                    case "allOf" -> SqlFilter.allOf(((List<Map<String, Object>>) filterEntry.getValue())
                            .stream().map(this::parse).toList());
                    case "anyOf" -> SqlFilter.anyOf(((List<Map<String, Object>>) filterEntry.getValue())
                            .stream().map(this::parse).toList());

                    case "noneOf" -> SqlFilter.allOf(((List<Map<String, Object>>) filterEntry.getValue())
                            .stream().map(v -> SqlFilter.not(this.parse(v))).toList());

                    case "isNull" -> ((Boolean) filterEntry.getValue()) ?
                            make((f, ctx) -> f + " is null", l -> l + " == null") :
                            make((f, ctx) -> f + " is not null", l -> l + " != null");

                    default -> filters.get(filterEntry.getKey()).apply(filterEntry.getValue());
                };
            }
            return SqlFilter.eq(in);
        }

        public FilterType build() {
            return new FilterType() {
                @Override
                public SqlFilter parse(Object in) {
                    return TypeBuilder.this.parse(in);
                }

                @Override
                public String castFromJson(String variable) {
                    return jsonCast.apply(variable);
                }
            };
        }
    }
}

package com.richie.component.search.util;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原生查询字符串解析工具类
 *
 * <p>将 Elasticsearch 原生查询字符串（JSON格式）转换为 Elasticsearch 原生 Query 对象。
 * <p>支持常见的查询类型：bool、term、terms、range、match、exists、nested、script 等。
 *
 * <p>使用示例：
 * <pre>{@code
 * String queryString = """
 *     {
 *       "bool": {
 *         "must": [
 *           {"term": {"status": "active"}},
 *           {"range": {"price": {"gte": 100, "lte": 1000}}}
 *         ]
 *       }
 *     }
 *     """;
 *
 * Query query = NativeQueryParser.parse(queryString);
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-12
 */
@Slf4j
public class NativeQueryParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析原生查询字符串为 Elasticsearch Query 对象
     *
     * @param queryString 原生查询字符串（JSON格式）
     * @return Elasticsearch 原生 Query 对象
     * @throws RuntimeException 当解析失败时抛出
     */
    public static Query parse(String queryString) {
        try {
            if (queryString == null || queryString.trim().isEmpty()) {
                log.warn("查询字符��为空，返回 match_all 查询");
                return Query.of(q -> q.matchAll(m -> m));
            }

            // 使用 Jackson 解析 JSON 字符串
            JsonNode jsonNode = OBJECT_MAPPER.readTree(queryString);

            // 如果 JSON 包含 "query" 字段，提取其中的查询部分
            if (jsonNode.has("query")) {
                jsonNode = jsonNode.get("query");
            }

            // 将 JsonNode 转换为 Elasticsearch Query 对象
            return parseQueryNode(jsonNode);

        } catch (Exception e) {
            log.error("解析原生查询字符串失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析原生查询字符串失败: " + queryString, e);
        }
    }

    /**
     * 递归解析查询节点
     *
     * @param jsonNode JSON 节点
     * @return Elasticsearch Query 对象
     */
    private static Query parseQueryNode(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            // 获取第一个字段名，这通常是查询类型
            String queryType = firstFieldName(jsonNode);

            return switch (queryType) {
                case "bool" -> parseBoolQuery(jsonNode.get(queryType));
                case "term" -> parseTermQuery(jsonNode.get(queryType));
                case "terms" -> parseTermsQuery(jsonNode.get(queryType));
                case "range" -> parseRangeQuery(jsonNode.get(queryType));
                case "match" -> parseMatchQuery(jsonNode.get(queryType));
                case "match_all" -> parseMatchAllQuery(jsonNode.get(queryType));
                case "exists" -> parseExistsQuery(jsonNode.get(queryType));
                case "nested" -> parseNestedQuery(jsonNode.get(queryType));
                case "script" -> parseScriptQuery(jsonNode.get(queryType));
                case "wildcard" -> parseWildcardQuery(jsonNode.get(queryType));
                case "prefix" -> parsePrefixQuery(jsonNode.get(queryType));
                case "fuzzy" -> parseFuzzyQuery(jsonNode.get(queryType));
                case "regexp" -> parseRegexpQuery(jsonNode.get(queryType));
                case "ids" -> parseIdsQuery(jsonNode.get(queryType));
                default -> {
                    log.warn("未支持的查询类型: {}, 返回 match_all 查询", queryType);
                    yield Query.of(q -> q.matchAll(m -> m));
                }
            };
        }

        // 如果不是对象，返回 match_all 查询
        log.warn("查询节点不是对象类型，返回 match_all 查询");
        return Query.of(q -> q.matchAll(m -> m));
    }

    /** 从对象形式的 JsonNode 上取第一个字段名（JsonNode 无 fieldNames()，用 propertyNames() 的迭代器）。 */
    private static String firstFieldName(JsonNode node) {
        return node.propertyNames().iterator().next();
    }

    /**
     * 解析布尔查询
     */
    private static Query parseBoolQuery(JsonNode boolNode) {
        return Query.of(q -> q.bool(b -> {
            // 处理 must 条件
            if (boolNode.has("must")) {
                JsonNode mustNode = boolNode.get("must");
                if (mustNode.isArray()) {
                    for (JsonNode mustItem : mustNode) {
                        b.must(parseQueryNode(mustItem));
                    }
                }
            }

            // 处理 should 条件
            if (boolNode.has("should")) {
                JsonNode shouldNode = boolNode.get("should");
                if (shouldNode.isArray()) {
                    for (JsonNode shouldItem : shouldNode) {
                        b.should(parseQueryNode(shouldItem));
                    }
                }
            }

            // 处理 must_not 条件
            if (boolNode.has("must_not")) {
                JsonNode mustNotNode = boolNode.get("must_not");
                if (mustNotNode.isArray()) {
                    for (JsonNode mustNotItem : mustNotNode) {
                        b.mustNot(parseQueryNode(mustNotItem));
                    }
                }
            }

            // 处理 filter 条件
            if (boolNode.has("filter")) {
                JsonNode filterNode = boolNode.get("filter");
                if (filterNode.isArray()) {
                    for (JsonNode filterItem : filterNode) {
                        b.filter(parseQueryNode(filterItem));
                    }
                }
            }

            // 设置最小匹配数
            if (boolNode.has("minimum_should_match")) {
                b.minimumShouldMatch(boolNode.get("minimum_should_match").asString());
            }

            // 设置提升值
            if (boolNode.has("boost")) {
                b.boost(boolNode.get("boost").floatValue());
            }

            return b;
        }));
    }

    /**
     * 解析词条查询
     */
    private static Query parseTermQuery(JsonNode termNode) {
        String field = firstFieldName(termNode);
        JsonNode valueNode = termNode.get(field);

        return Query.of(q -> q.term(t -> {
            t.field(field);

            if (valueNode.isObject() && valueNode.has("value")) {
                Object value = parseValue(valueNode.get("value"));
                t.value(FieldValue.of(value));

                // 设置提升值
                if (valueNode.has("boost")) {
                    t.boost(valueNode.get("boost").floatValue());
                }
            } else {
                Object value = parseValue(valueNode);
                t.value(FieldValue.of(value));
            }

            return t;
        }));
    }

    /**
     * 解析词条集合查询
     */
    private static Query parseTermsQuery(JsonNode termsNode) {
        String field = firstFieldName(termsNode);
        JsonNode valuesNode = termsNode.get(field);

        if (valuesNode.isArray()) {
            List<FieldValue> values = new ArrayList<>();
            for (JsonNode valueNode : valuesNode) {
                values.add(FieldValue.of(parseValue(valueNode)));
            }
            return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(values))));
        }

        log.warn("terms 查询的值不是数组类型，返回 match_all 查询");
        return Query.of(q -> q.matchAll(m -> m));
    }

    /**
     * 解析范围查询
     */
    private static Query parseRangeQuery(JsonNode rangeNode) {
        String field = firstFieldName(rangeNode);
        JsonNode rangeConfig = rangeNode.get(field);

        // 分析字段值类型，选择合适的范围查询类型
        RangeQueryType queryType = determineRangeQueryType(rangeConfig);

        return switch (queryType) {
            case DATE -> buildDateRangeQuery(field, rangeConfig);
            case NUMBER -> buildNumberRangeQuery(field, rangeConfig);
            case TERM -> buildTermRangeQuery(field, rangeConfig);
            case UNTYPED -> buildUntypedRangeQuery(field, rangeConfig);
        };
    }

    /**
     * 范围查询类型枚举
     */
    private enum RangeQueryType {
        DATE, NUMBER, TERM, UNTYPED
    }

    /**
     * 根据 JsonNode 的值类型确定范围查询类型
     */
    private static RangeQueryType determineRangeQueryType(JsonNode rangeConfig) {
        // 检查所有范围条件的值类型
        JsonNode[] valueNodes = {
                rangeConfig.get("gte"), rangeConfig.get("gt"),
                rangeConfig.get("lte"), rangeConfig.get("lt")
        };

        boolean hasDatePattern = false;
        boolean hasNumericValue = false;
        boolean hasStringValue = false;

        for (JsonNode valueNode : valueNodes) {
            if (valueNode != null) {
                if (valueNode.isNumber()) {
                    hasNumericValue = true;
                } else if (valueNode.isString()) {
                    String textValue = valueNode.asString();
                    // 检查是否为日期格式
                    if (isDateString(textValue)) {
                        hasDatePattern = true;
                    } else {
                        hasStringValue = true;
                    }
                }
            }
        }

        // 根据值类型确定查询类型
        if (hasDatePattern) {
            return RangeQueryType.DATE;
        } else if (hasNumericValue && !hasStringValue) {
            return RangeQueryType.NUMBER;
        } else if (hasStringValue && !hasNumericValue) {
            return RangeQueryType.TERM;
        } else {
            return RangeQueryType.UNTYPED;
        }
    }

    /**
     * 检查字符串是否为日期格式
     */
    private static boolean isDateString(String value) {
        // 常见的日期格式模式
        return value.matches("\\d{4}-\\d{2}-\\d{2}.*") ||           // yyyy-MM-dd开头
                value.matches("\\d{4}/\\d{2}/\\d{2}.*") ||           // yyyy/MM/dd开头
                value.matches("\\d{2}-\\d{2}-\\d{4}.*") ||           // MM-dd-yyyy开头
                value.matches("\\d{2}/\\d{2}/\\d{4}.*") ||           // MM/dd/yyyy开头
                value.contains("T") && value.contains(":") ||        // ISO 8601格式
                value.equalsIgnoreCase("now") ||                     // Elasticsearch的now
                value.matches("now[+-]\\d+[dwMy]");                  // now+1d, now-1w等
    }

    /**
     * 构建日期范围查询
     */
    private static Query buildDateRangeQuery(String field, JsonNode rangeConfig) {
        return Query.of(q -> q.range(r -> r
                .date(d -> {
                    d.field(field);

                    if (rangeConfig.has("gte")) {
                        d.gte(rangeConfig.get("gte").asString());
                    }
                    if (rangeConfig.has("gt")) {
                        d.gt(rangeConfig.get("gt").asString());
                    }
                    if (rangeConfig.has("lte")) {
                        d.lte(rangeConfig.get("lte").asString());
                    }
                    if (rangeConfig.has("lt")) {
                        d.lt(rangeConfig.get("lt").asString());
                    }

                    // 设置日期格式（如果指定）
                    if (rangeConfig.has("format")) {
                        d.format(rangeConfig.get("format").asString());
                    }

                    // 设置时区（如果指定）
                    if (rangeConfig.has("time_zone")) {
                        d.timeZone(rangeConfig.get("time_zone").asString());
                    }

                    // 设置提升值
                    if (rangeConfig.has("boost")) {
                        d.boost(rangeConfig.get("boost").floatValue());
                    }

                    return d;
                })
        ));
    }

    /**
     * 构建数字范围查询
     */
    private static Query buildNumberRangeQuery(String field, JsonNode rangeConfig) {
        return Query.of(q -> q.range(r -> r
                .number(n -> {
                    n.field(field);

                    if (rangeConfig.has("gte")) {
                        n.gte(rangeConfig.get("gte").asDouble());
                    }
                    if (rangeConfig.has("gt")) {
                        n.gt(rangeConfig.get("gt").asDouble());
                    }
                    if (rangeConfig.has("lte")) {
                        n.lte(rangeConfig.get("lte").asDouble());
                    }
                    if (rangeConfig.has("lt")) {
                        n.lt(rangeConfig.get("lt").asDouble());
                    }

                    // 设置提升值
                    if (rangeConfig.has("boost")) {
                        n.boost(rangeConfig.get("boost").floatValue());
                    }

                    return n;
                })
        ));
    }

    /**
     * 构建词项范围查询
     */
    private static Query buildTermRangeQuery(String field, JsonNode rangeConfig) {
        return Query.of(q -> q.range(r -> r
                .term(t -> {
                    t.field(field);

                    if (rangeConfig.has("gte")) {
                        t.gte(rangeConfig.get("gte").asString());
                    }
                    if (rangeConfig.has("gt")) {
                        t.gt(rangeConfig.get("gt").asString());
                    }
                    if (rangeConfig.has("lte")) {
                        t.lte(rangeConfig.get("lte").asString());
                    }
                    if (rangeConfig.has("lt")) {
                        t.lt(rangeConfig.get("lt").asString());
                    }

                    // 设置提升值
                    if (rangeConfig.has("boost")) {
                        t.boost(rangeConfig.get("boost").floatValue());
                    }

                    return t;
                })
        ));
    }

    /**
     * 构建无类型范围查询
     */
    private static Query buildUntypedRangeQuery(String field, JsonNode rangeConfig) {
        return Query.of(q -> q.range(r -> r
                .untyped(u -> {
                    u.field(field);

                    if (rangeConfig.has("gte")) {
                        u.gte(JsonData.of(parseValue(rangeConfig.get("gte"))));
                    }
                    if (rangeConfig.has("gt")) {
                        u.gt(JsonData.of(parseValue(rangeConfig.get("gt"))));
                    }
                    if (rangeConfig.has("lte")) {
                        u.lte(JsonData.of(parseValue(rangeConfig.get("lte"))));
                    }
                    if (rangeConfig.has("lt")) {
                        u.lt(JsonData.of(parseValue(rangeConfig.get("lt"))));
                    }

                    // 设置提升值
                    if (rangeConfig.has("boost")) {
                        u.boost(rangeConfig.get("boost").floatValue());
                    }

                    return u;
                })
        ));
    }

    /**
     * 解析匹配查询
     */
    private static Query parseMatchQuery(JsonNode matchNode) {
        String field = firstFieldName(matchNode);
        JsonNode valueNode = matchNode.get(field);

        return Query.of(q -> q.match(m -> {
            m.field(field);

            if (valueNode.isObject() && valueNode.has("query")) {
                String value = valueNode.get("query").asString();
                m.query(value);

                // 设置操作符
                if (valueNode.has("operator")) {
                    m.operator(Operator.valueOf(valueNode.get("operator").asString()));
                }

                // 设置模糊度
                if (valueNode.has("fuzziness")) {
                    m.fuzziness(valueNode.get("fuzziness").asString());
                }

                // 设置提升值
                if (valueNode.has("boost")) {
                    m.boost(valueNode.get("boost").floatValue());
                }
            } else {
                String value = valueNode.asString();
                m.query(value);
            }

            return m;
        }));
    }

    /**
     * 解析匹配所有查询
     */
    private static Query parseMatchAllQuery(JsonNode matchAllNode) {
        return Query.of(q -> q.matchAll(m -> {
            if (matchAllNode.has("boost")) {
                m.boost(matchAllNode.get("boost").floatValue());
            }
            return m;
        }));
    }

    /**
     * 解析存在查询
     */
    private static Query parseExistsQuery(JsonNode existsNode) {
        String field = existsNode.get("field").asString();
        return Query.of(q -> q.exists(e -> e.field(field)));
    }

    /**
     * 解析嵌套查询
     */
    private static Query parseNestedQuery(JsonNode nestedNode) {
        String path = nestedNode.get("path").asString();
        JsonNode queryNode = nestedNode.get("query");

        return Query.of(q -> q.nested(n -> {
            n.path(path);
            n.query(parseQueryNode(queryNode));

            if (nestedNode.has("score_mode")) {
                n.scoreMode(ChildScoreMode.valueOf(nestedNode.get("score_mode").asString()));
            }

            return n;
        }));
    }

    /**
     * 解析脚本查询
     */
    private static Query parseScriptQuery(JsonNode scriptNode) {
        return Query.of(q -> q.script(s -> {
            // 设置脚本内容
            if (scriptNode.has("source")) {
                s.script(sc -> sc.source(builder -> builder.scriptString(scriptNode.get("source").asString())));
            } else if (scriptNode.has("id")) {
                s.script(sc -> sc.id(scriptNode.get("id").asString()));
            }

            // 设置脚本语言
            if (scriptNode.has("lang")) {
                s.script(sc -> sc.lang(scriptNode.get("lang").asString()));
            }

            // 设置脚本参数
            if (scriptNode.has("params")) {
                JsonNode paramsNode = scriptNode.get("params");
                if (paramsNode.isObject()) {
                    Map<String, JsonData> params = new HashMap<>();
                    paramsNode.propertyNames().forEach(fieldName ->
                        params.put(fieldName, JsonData.of(parseValue(paramsNode.get(fieldName)))));
                    s.script(sc -> sc.params(params));
                }
            }

            return s;
        }));
    }

    /**
     * 解析通配符查询
     */
    private static Query parseWildcardQuery(JsonNode wildcardNode) {
        String field = firstFieldName(wildcardNode);
        JsonNode valueNode = wildcardNode.get(field);

        return Query.of(q -> q.wildcard(w -> {
            w.field(field);

            if (valueNode.isObject() && valueNode.has("value")) {
                w.value(valueNode.get("value").asString());
            } else {
                w.value(valueNode.asString());
            }

            return w;
        }));
    }

    /**
     * 解析前缀查询
     */
    private static Query parsePrefixQuery(JsonNode prefixNode) {
        String field = firstFieldName(prefixNode);
        JsonNode valueNode = prefixNode.get(field);

        return Query.of(q -> q.prefix(p -> {
            p.field(field);

            if (valueNode.isObject() && valueNode.has("value")) {
                p.value(valueNode.get("value").asString());
            } else {
                p.value(valueNode.asString());
            }

            return p;
        }));
    }

    /**
     * 解析模糊查询
     */
    private static Query parseFuzzyQuery(JsonNode fuzzyNode) {
        String field = firstFieldName(fuzzyNode);
        JsonNode valueNode = fuzzyNode.get(field);

        return Query.of(q -> q.fuzzy(f -> {
            f.field(field);

            if (valueNode.isObject() && valueNode.has("value")) {
                f.value(valueNode.get("value").asString());
            } else {
                f.value(valueNode.asString());
            }

            return f;
        }));
    }

    /**
     * 解析正则表达式查询
     */
    private static Query parseRegexpQuery(JsonNode regexpNode) {
        String field = firstFieldName(regexpNode);
        JsonNode valueNode = regexpNode.get(field);

        return Query.of(q -> q.regexp(r -> {
            r.field(field);

            if (valueNode.isObject() && valueNode.has("value")) {
                r.value(valueNode.get("value").asString());
            } else {
                r.value(valueNode.asString());
            }

            return r;
        }));
    }

    /**
     * 解析ID查询
     */
    private static Query parseIdsQuery(JsonNode idsNode) {
        if (idsNode.has("values")) {
            JsonNode valuesNode = idsNode.get("values");
            if (valuesNode.isArray()) {
                List<String> ids = new ArrayList<>();
                for (JsonNode idNode : valuesNode) {
                    ids.add(idNode.asString());
                }
                return Query.of(q -> q.ids(i -> i.values(ids)));
            }
        }

        log.warn("ids 查询格式不正确，返回 match_all 查询");
        return Query.of(q -> q.matchAll(m -> m));
    }

    /**
     * 解析值
     */
    private static Object parseValue(JsonNode valueNode) {
        if (valueNode.isString()) {
            return valueNode.asString();
        } else if (valueNode.isNumber()) {
            if (valueNode.isInt()) {
                return valueNode.asInt();
            } else if (valueNode.isLong()) {
                return valueNode.asLong();
            } else if (valueNode.isDouble()) {
                return valueNode.asDouble();
            }
        } else if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }

        return valueNode.asString();
    }
}

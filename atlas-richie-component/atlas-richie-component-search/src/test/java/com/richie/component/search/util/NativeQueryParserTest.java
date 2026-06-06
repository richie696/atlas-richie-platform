package com.richie.component.search.util;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeQueryParserTest {

    @Test
    void parse_nullString_returnsMatchAll() {
        Query query = NativeQueryParser.parse(null);
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_emptyString_returnsMatchAll() {
        Query query = NativeQueryParser.parse("");
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_whitespaceString_returnsMatchAll() {
        Query query = NativeQueryParser.parse("   ");
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_invalidJson_throwsRuntimeException() {
        assertThatThrownBy(() -> NativeQueryParser.parse("{invalid json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("解析原生查询字符串失败");
    }

    @Test
    void parse_nonObjectNode_returnsMatchAll() {
        Query query = NativeQueryParser.parse("\"just a string\"");
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_unsupportedQueryType_returnsMatchAll() {
        String json = """
                {"unsupported": {"field": "value"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_topLevelQueryWrapper() {
        String json = """
                {"query": {"term": {"status": "active"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    // ========== Term Query Tests ==========

    @Test
    void parse_termQuery_simple() {
        String json = """
                {"term": {"status": "active"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    @Test
    void parse_termQuery_withValueObject() {
        String json = """
                {"term": {"status": {"value": "active", "boost": 2.0}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    @Test
    void parse_termQuery_withBooleanValue() {
        String json = """
                {"term": {"enabled": true}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    @Test
    void parse_termQuery_withIntValue() {
        String json = """
                {"term": {"count": 42}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    @Test
    void parse_termQuery_withLongValue() {
        String json = """
                {"term": {"timestamp": 12345678901234}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    @Test
    void parse_termQuery_withDoubleValue() {
        String json = """
                {"term": {"price": 99.99}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }

    // ========== Terms Query Tests ==========

    @Test
    void parse_termsQuery_arrayValues() {
        String json = """
                {"terms": {"status": ["active", "pending", "draft"]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerms()).isTrue();
    }

    @Test
    void parse_termsQuery_nonArrayValue_returnsMatchAll() {
        String json = """
                {"terms": {"status": "notAnArray"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    // ========== Range Query Tests - DATE ==========

    @Test
    void parse_rangeQuery_date_yyyyMMdd() {
        String json = """
                {"range": {"createDate": {"gte": "2024-01-01", "lte": "2024-12-31"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_date_now() {
        String json = """
                {"range": {"createDate": {"gte": "now", "lte": "now+30d"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_date_nowWithUnit() {
        String json = """
                {"range": {"createDate": {"gte": "now-1d", "lte": "now+1d"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_date_isoFormat() {
        String json = """
                {"range": {"createDate": {"gte": "2024-01-01T00:00:00Z", "lte": "2024-12-31T23:59:59Z"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_date_withFormat() {
        String json = """
                {"range": {"createDate": {"gte": "2024-01-01", "format": "yyyy-MM-dd"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_date_withTimeZone() {
        String json = """
                {"range": {"createDate": {"gte": "2024-01-01", "time_zone": "+08:00"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_date_withBoost() {
        String json = """
                {"range": {"createDate": {"gte": "now", "boost": 1.5}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    // ========== Range Query Tests - NUMBER ==========

    @Test
    void parse_rangeQuery_number_integer() {
        String json = """
                {"range": {"count": {"gte": 10, "lte": 100}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_number_double() {
        String json = """
                {"range": {"price": {"gte": 10.5, "lte": 99.9}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_number_withBoost() {
        String json = """
                {"range": {"count": {"gte": 0, "lte": 100, "boost": 2.0}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    // ========== Range Query Tests - TERM (String) ==========

    @Test
    void parse_rangeQuery_term_string() {
        String json = """
                {"range": {"category": {"gte": "a", "lte": "z"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    // ========== Range Query Tests - UNTYPED (Mixed) ==========

    @Test
    void parse_rangeQuery_untyped_mixed() {
        String json = """
                {"range": {"field": {"gte": 10, "lte": "value"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    @Test
    void parse_rangeQuery_untyped_withBoost() {
        String json = """
                {"range": {"field": {"gte": "a", "lte": "z", "boost": 1.5}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRange()).isTrue();
    }

    // ========== Match Query Tests ==========

    @Test
    void parse_matchQuery_simple() {
        String json = """
                {"match": {"title": "hello world"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatch()).isTrue();
    }

    @Test
    void parse_matchQuery_withOperator() {
        String json = """
                {"match": {"title": {"query": "hello world", "operator": "And"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatch()).isTrue();
    }

    @Test
    void parse_matchQuery_withFuzziness() {
        String json = """
                {"match": {"title": {"query": "hello", "fuzziness": "AUTO"}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatch()).isTrue();
    }

    @Test
    void parse_matchQuery_withBoost() {
        String json = """
                {"match": {"title": {"query": "hello", "boost": 2.0}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatch()).isTrue();
    }

    // ========== Match All Query Tests ==========

    @Test
    void parse_matchAllQuery_simple() {
        String json = """
                {"match_all": {}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_matchAllQuery_withBoost() {
        String json = """
                {"match_all": {"boost": 1.5}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    // ========== Exists Query Tests ==========

    @Test
    void parse_existsQuery() {
        String json = """
                {"exists": {"field": "userName"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isExists()).isTrue();
    }

    // ========== Nested Query Tests ==========

    @Test
    void parse_nestedQuery_simple() {
        String json = """
                {"nested": {"path": "user", "query": {"term": {"user.name": "john"}}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isNested()).isTrue();
    }

    @Test
    void parse_nestedQuery_withScoreMode() {
        String json = """
                {"nested": {"path": "user", "score_mode": "Avg", "query": {"term": {"user.name": "john"}}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isNested()).isTrue();
    }

    // ========== Script Query Tests ==========

    @Test
    void parse_scriptQuery_withSource() {
        String json = """
                {"script": {"source": "doc['price'].value > params.minPrice", "params": {"minPrice": 100}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isScript()).isTrue();
    }

    @Test
    void parse_scriptQuery_withId() {
        String json = """
                {"script": {"id": "my-script-id"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isScript()).isTrue();
    }

    @Test
    void parse_scriptQuery_withLang() {
        String json = """
                {"script": {"source": "doc['price'].value > 100", "lang": "painless"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isScript()).isTrue();
    }

    @Test
    void parse_scriptQuery_withParams() {
        String json = """
                {"script": {"source": "doc['field'].value > params.threshold", "params": {"threshold": 50}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isScript()).isTrue();
    }

    // ========== Wildcard Query Tests ==========

    @Test
    void parse_wildcardQuery_simple() {
        String json = """
                {"wildcard": {"userName": "john*"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isWildcard()).isTrue();
    }

    @Test
    void parse_wildcardQuery_withValueObject() {
        String json = """
                {"wildcard": {"userName": {"value": "john*", "boost": 1.5}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isWildcard()).isTrue();
    }

    // ========== Prefix Query Tests ==========

    @Test
    void parse_prefixQuery_simple() {
        String json = """
                {"prefix": {"userName": "john"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isPrefix()).isTrue();
    }

    @Test
    void parse_prefixQuery_withValueObject() {
        String json = """
                {"prefix": {"userName": {"value": "john", "boost": 2.0}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isPrefix()).isTrue();
    }

    // ========== Fuzzy Query Tests ==========

    @Test
    void parse_fuzzyQuery_simple() {
        String json = """
                {"fuzzy": {"userName": "john"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isFuzzy()).isTrue();
    }

    @Test
    void parse_fuzzyQuery_withValueObject() {
        String json = """
                {"fuzzy": {"userName": {"value": "john", "boost": 1.5}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isFuzzy()).isTrue();
    }

    // ========== Regexp Query Tests ==========

    @Test
    void parse_regexpQuery_simple() {
        String json = """
                {"regexp": {"userName": "john.*"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRegexp()).isTrue();
    }

    @Test
    void parse_regexpQuery_withValueObject() {
        String json = """
                {"regexp": {"userName": {"value": "john.*", "boost": 1.5}}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isRegexp()).isTrue();
    }

    // ========== Ids Query Tests ==========

    @Test
    void parse_idsQuery_withValuesArray() {
        String json = """
                {"ids": {"values": ["1", "2", "3"]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isIds()).isTrue();
    }

    @Test
    void parse_idsQuery_noValues_returnsMatchAll() {
        String json = """
                {"ids": {}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    // ========== Bool Query Tests ==========

    @Test
    void parse_boolQuery_must() {
        String json = """
                {"bool": {"must": [{"term": {"status": "active"}}, {"term": {"count": {"value": 10, "boost": 2.0}}}]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isBool()).isTrue();
    }

    @Test
    void parse_boolQuery_should() {
        String json = """
                {"bool": {"should": [{"term": {"status": "active"}}, {"term": {"status": "pending"}}]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isBool()).isTrue();
    }

    @Test
    void parse_boolQuery_mustNot() {
        String json = """
                {"bool": {"must_not": [{"term": {"status": "deleted"}}]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isBool()).isTrue();
    }

    @Test
    void parse_boolQuery_filter() {
        String json = """
                {"bool": {"filter": [{"term": {"status": "active"}}]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isBool()).isTrue();
    }

    @Test
    void parse_boolQuery_minimumShouldMatchAndBoost() {
        String json = """
                {"bool": {"should": [{"term": {"a": "x"}}, {"term": {"b": "y"}}], "minimum_should_match": 1, "boost": 1.5}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isBool()).isTrue();
    }

    @Test
    void parse_boolQuery_combined() {
        String json = """
                {"bool": {"must": [{"term": {"status": "active"}}], "should": [{"term": {"featured": true}}], "must_not": [{"term": {"deleted": true}}], "filter": [{"range": {"price": {"gte": 100}}}]}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isBool()).isTrue();
    }
}

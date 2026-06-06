package com.richie.component.search.util;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NativeQueryParserTest {

    @Test
    void parse_emptyString_returnsMatchAll() {
        Query query = NativeQueryParser.parse("   ");
        assertThat(query).isNotNull();
        assertThat(query.isMatchAll()).isTrue();
    }

    @Test
    void parse_termQuery() {
        String json = """
                {"term": {"status": "active"}}
                """;
        Query query = NativeQueryParser.parse(json);
        assertThat(query).isNotNull();
        assertThat(query.isTerm()).isTrue();
    }
}

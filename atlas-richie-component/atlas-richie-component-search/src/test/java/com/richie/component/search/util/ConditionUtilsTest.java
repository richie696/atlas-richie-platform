package com.richie.component.search.util;

import com.richie.component.search.enums.QueryOperator;
import com.richie.component.search.model.QueryCondition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionUtilsTest {

    @Test
    void extractConditions_handlesEqAndBetween() {
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.EQ, "status", "active"),
                condition(QueryOperator.BETWEEN, "age", new Object[] {18, 60}),
                condition(QueryOperator.LIKE, "name", "张%"),
                condition(QueryOperator.IN, "tags", List.of("a", "b")));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        assertThat(extracted).containsEntry("status", "active");
        assertThat(extracted).containsEntry("age_from", 18);
        assertThat(extracted).containsEntry("age_to", 60);
        assertThat(extracted).containsEntry("name_like", "张%");
        assertThat(extracted).containsKey("tags_in");
    }

    @Test
    void extractConditions_returnsEmptyForNullInput() {
        assertThat(ConditionUtils.extractConditions(null)).isEmpty();
        assertThat(ConditionUtils.extractConditions(List.of())).isEmpty();
    }

    @Test
    void extractConditions_handlesScalarInAndBetweenFallback() {
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.IN, "code", "A"),
                condition(QueryOperator.BETWEEN, "score", 10),
                condition(QueryOperator.GT, "level", 3),
                condition(QueryOperator.NE, "status", "deleted"));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        assertThat(extracted).containsEntry("code", "A");
        assertThat(extracted).containsEntry("score_from", 10);
        assertThat(extracted).containsEntry("score_to", 10);
        assertThat(extracted).containsEntry("level", 3);
        assertThat(extracted).containsEntry("status", "deleted");
    }

    @Test
    void extractConditions_skipsNullFieldOrValue() {
        assertThat(ConditionUtils.extractConditions(List.of(
                condition(QueryOperator.EQ, null, "x"),
                condition(QueryOperator.EQ, "field", null)))).isEmpty();
    }

    private static QueryCondition condition(QueryOperator operator, String field, Object value) {
        return new QueryCondition().setOperator(operator).setField(field).setValue(value);
    }
}

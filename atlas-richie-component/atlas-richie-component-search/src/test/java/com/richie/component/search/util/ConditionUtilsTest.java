package com.richie.component.search.util;

import com.richie.component.search.enums.QueryOperator;
import com.richie.component.search.model.QueryCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    @Test
    void extractConditions_handlesExistsAndNotExists() {
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.EXISTS, "field1", "anyValue"),
                condition(QueryOperator.NOT_EXISTS, "field2", "anyValue"));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        assertThat(extracted).containsEntry("field1", "anyValue");
        assertThat(extracted).containsEntry("field2", "anyValue");
    }

    @Test
    void extractConditions_handlesNotInWithIterable() {
        List<String> values = List.of("a", "b", "c");
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.NOT_IN, "category", values));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        assertThat(extracted).containsKey("category_in");
        assertThat(extracted.get("category_in")).isEqualTo(values);
    }

    @Test
    void extractConditions_handlesNotInWithNonIterable() {
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.NOT_IN, "code", "singleValue"));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        assertThat(extracted).containsEntry("code", "singleValue");
        assertThat(extracted).doesNotContainKey("code_in");
    }

    @Test
    void extractConditions_handlesBetweenWithNonArrayValue() {
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.BETWEEN, "value", "notAnArray"));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        // Fallback path: the value is returned as-is for both from and to
        assertThat(extracted).containsEntry("value_from", "notAnArray");
        assertThat(extracted).containsEntry("value_to", "notAnArray");
    }

    @Test
    void extractConditions_handlesAllComparisonOperators() {
        List<QueryCondition> conditions = List.of(
                condition(QueryOperator.EQ, "eqField", "value"),
                condition(QueryOperator.NE, "neField", "value"),
                condition(QueryOperator.GT, "gtField", 10),
                condition(QueryOperator.GE, "geField", 10),
                condition(QueryOperator.LT, "ltField", 10),
                condition(QueryOperator.LE, "leField", 10));

        Map<String, Object> extracted = ConditionUtils.extractConditions(conditions);

        assertThat(extracted).containsEntry("eqField", "value");
        assertThat(extracted).containsEntry("neField", "value");
        assertThat(extracted).containsEntry("gtField", 10);
        assertThat(extracted).containsEntry("geField", 10);
        assertThat(extracted).containsEntry("ltField", 10);
        assertThat(extracted).containsEntry("leField", 10);
    }

    @Test
    void extractConditions_handlesEmptyConditionsList() {
        Map<String, Object> extracted = ConditionUtils.extractConditions(new ArrayList<>());
        assertThat(extracted).isEmpty();
    }

    private static QueryCondition condition(QueryOperator operator, String field, Object value) {
        return new QueryCondition().setOperator(operator).setField(field).setValue(value);
    }
}

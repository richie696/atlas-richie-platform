package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeaviateVectorFilterCompilerTest {

    private final WeaviateVectorFilterCompiler compiler = new WeaviateVectorFilterCompiler();

    @Test
    void compilesAclTreeToServerSideWhereExpression() {
        VectorFilter filter = VectorFilter.and(
                VectorFilter.eq("tenantId", "tenant'1"),
                VectorFilter.or(
                        VectorFilter.eq("visibility", "COMPANY"),
                        VectorFilter.containsAny("allowedPrincipalIds", List.of("user-1", "user-2"))));

        assertThat(compiler.compile(filter))
                .contains("operator:And")
                .contains("meta_tenantId", "meta_visibility", "meta_allowedPrincipalIds")
                .contains("valueText:\"tenant'1\"")
                .contains("operator:ContainsAny")
                .contains("valueTextArray:[\"user-1\",\"user-2\"]");
    }

    @Test
    void escapesGraphqlStringAndRejectsUnsafeField() {
        assertThat(compiler.compile(VectorFilter.eq("status", "a\\b\"c\n")))
                .contains("valueText:\"a\\\\b\\\"c\\n\"");
        assertThatThrownBy(() -> compiler.compile(VectorFilter.eq("status} malicious", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

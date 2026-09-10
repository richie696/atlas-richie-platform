package cn.richie696.component.vector.filter;

import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbFilterExpressionConverter;
import cn.richie696.component.vector.model.VectorFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TencentVectorDbAclFilterAdapterTest {

    private final TencentVectorDbFilterExpressionConverter converter = new TencentVectorDbFilterExpressionConverter();

    @Test
    void compilesTheSameStructuredAclPredicateWithoutStringConcatenation() {
        String expression = converter.convertExpression(TencentVectorDbAclFilterAdapter.toSpring(
                VectorFilter.and(VectorFilter.eq("tenantId", "tenant-a"),
                        VectorFilter.in("principalId", List.of("user-1", "group-1")))));

        assertThat(expression).isEqualTo("(tenantId = \"tenant-a\" AND principalId IN (\"user-1\", \"group-1\"))");
    }
}

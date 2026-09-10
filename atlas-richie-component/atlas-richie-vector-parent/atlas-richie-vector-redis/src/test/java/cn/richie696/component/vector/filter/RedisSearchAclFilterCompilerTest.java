package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSearchAclFilterCompilerTest {
    private final RedisSearchAclFilterCompiler compiler = new RedisSearchAclFilterCompiler();
    @Test void producesProviderSideTagAndNumericPredicates() {
        assertThat(compiler.compile(VectorFilter.and(VectorFilter.eq("tenantId", "tenant-a"),
                VectorFilter.in("principalId", List.of("u1", "g1")), VectorFilter.range("visibility", 1, 3))))
                .contains("@tenantId:{tenant\\-a}").contains("@principalId:{u1|g1}").contains("@visibility:[1.0 3.0]");
    }
    @Test void rejectsUnsupportedExistsPredicate() {
        assertThatThrownBy(() -> compiler.compile(VectorFilter.exists("tenantId"))).isInstanceOf(UnsupportedOperationException.class);
    }
}

package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresqlAclFilterCompilerTest {
    @Test
    void bindsAclValuesInsteadOfInterpolatingThemIntoSql() {
        var compiled = new PostgresqlAclFilterCompiler().compile(VectorFilter.and(
                VectorFilter.eq("tenantId", "tenant-a' OR true --"),
                VectorFilter.containsAny("principals", List.of("u1", "g1")),
                VectorFilter.range("visibility", 1, 3)));

        assertThat(compiled.sql()).doesNotContain("tenant-a").contains("metadata ->> 'tenantId' = ?");
        assertThat(compiled.parameters()).contains("tenant-a' OR true --", 1D, 3D);
    }
}

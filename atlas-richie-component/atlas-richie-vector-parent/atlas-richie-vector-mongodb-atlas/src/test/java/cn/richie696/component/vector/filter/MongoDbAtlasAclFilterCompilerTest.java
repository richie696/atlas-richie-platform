package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MongoDbAtlasAclFilterCompilerTest {
    @Test
    void mapsTheSameAclExpressionForVectorAndAtlasSearch() {
        VectorFilter filter = VectorFilter.and(VectorFilter.eq("tenantId", "tenant-a"), VectorFilter.containsAny("principals", List.of("u1", "g1")));
        MongoDbAtlasAclFilterCompiler compiler = new MongoDbAtlasAclFilterCompiler();
        assertThat(compiler.vectorFilter(filter).toJson()).contains("metadata.tenantId").contains("metadata.principals");
        assertThat(compiler.searchFilter(filter).toJson()).contains("equals").contains("in").contains("metadata.tenantId");
    }
}

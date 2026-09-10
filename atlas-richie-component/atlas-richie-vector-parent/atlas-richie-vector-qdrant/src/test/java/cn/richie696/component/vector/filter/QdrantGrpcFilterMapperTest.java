package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import io.qdrant.client.grpc.Common;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantGrpcFilterMapperTest {
    private final QdrantGrpcFilterMapper mapper = new QdrantGrpcFilterMapper();

    @Test
    void mapsStructuredAclAsProviderFilter() {
        Common.Filter filter = mapper.map(VectorFilter.and(
                VectorFilter.eq("tenantId", "tenant-a"),
                VectorFilter.in("knowledgeBaseId", List.of(11L, 12L)),
                VectorFilter.range("visibility", 1, 3)));

        assertThat(filter.getMustCount()).isEqualTo(3);
        assertThat(filter.getMust(0).getField().getKey()).isEqualTo("tenantId");
        assertThat(filter.getMust(1).getField().getMatch().getIntegers().getIntegersList()).containsExactly(11L, 12L);
        assertThat(filter.getMust(2).getField().getRange().getGte()).isEqualTo(1D);
        assertThat(filter.getMust(2).getField().getRange().getLte()).isEqualTo(3D);
    }

    @Test
    void preservesNestedOrAndNotWithoutFallingBackToTextDsl() {
        Common.Filter filter = mapper.map(VectorFilter.not(VectorFilter.or(
                VectorFilter.eq("tenantId", "denied"), VectorFilter.containsAny("roles", List.of("blocked")))));

        assertThat(filter.getMustNotCount()).isEqualTo(1);
        assertThat(filter.getMustNot(0).getFilter().getShouldCount()).isEqualTo(2);
    }

    @Test
    void rejectsUnsupportedValueTypes() {
        assertThatThrownBy(() -> mapper.map(VectorFilter.eq("tenantId", true)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.map(VectorFilter.exists("tenantId")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

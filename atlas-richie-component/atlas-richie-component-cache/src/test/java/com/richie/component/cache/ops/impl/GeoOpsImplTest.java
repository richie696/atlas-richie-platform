package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.GeoFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoOpsImplTest {

    @Mock
    private GeoFunction fn;

    @InjectMocks
    private GeoOpsImpl ops;

    @Test
    void add_delegates() {
        ops.add("geo", 116.0, 39.0, "bj");
        verify(fn).addGeo("geo", 116.0, 39.0, "bj");
    }

    @Test
    void distance_delegates() {
        Distance d = new Distance(100, Metrics.KILOMETERS);
        when(fn.geoDist("geo", "a", "b")).thenReturn(d);
        assertThat(ops.distance("geo", "a", "b")).isSameAs(d);
    }
}

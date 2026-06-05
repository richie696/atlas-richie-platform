package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.HyperLogFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HyperLogOpsImplTest {

    @Mock
    private HyperLogFunction fn;

    @InjectMocks
    private HyperLogOpsImpl ops;

    @Test
    void add_delegates() {
        ops.add("uv", "u1", "u2");
        verify(fn).pfAdd("uv", "u1", "u2");
    }

    @Test
    void count_delegates() {
        when(fn.pfCount("uv")).thenReturn(42L);
        assertThat(ops.count("uv")).isEqualTo(42L);
    }
}

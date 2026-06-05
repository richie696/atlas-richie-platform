package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.BitmapFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BitmapOpsImplTest {

    @Mock
    private BitmapFunction fn;

    @InjectMocks
    private BitmapOpsImpl ops;

    @Test
    void set_delegates() {
        ops.set("sign", 1, true);
        verify(fn).setBit("sign", 1, true);
    }

    @Test
    void get_delegates() {
        when(fn.getBit("sign", 1)).thenReturn(true);
        assertThat(ops.get("sign", 1)).isTrue();
    }
}

/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

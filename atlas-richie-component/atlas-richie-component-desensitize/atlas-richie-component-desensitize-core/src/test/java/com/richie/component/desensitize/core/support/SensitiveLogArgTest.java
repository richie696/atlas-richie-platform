/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.desensitize.core.support;

import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLogArgTest {

    @Test
    void factoryMethodsWrapValueAndType() {
        assertThat(SensitiveLogArg.of("v", MaskType.CUSTOM).value()).isEqualTo("v");
        assertThat(SensitiveLogArg.of("v", MaskType.CUSTOM).type()).isEqualTo(MaskType.CUSTOM);
        assertThat(SensitiveLogArg.phone("138").type()).isEqualTo(MaskType.PHONE);
        assertThat(SensitiveLogArg.idCard("110").type()).isEqualTo(MaskType.ID_CARD);
        assertThat(SensitiveLogArg.email("a@b.c").type()).isEqualTo(MaskType.EMAIL);
        assertThat(SensitiveLogArg.bankCard("6222").type()).isEqualTo(MaskType.BANK_CARD);
        assertThat(SensitiveLogArg.name("张三").type()).isEqualTo(MaskType.NAME);
        assertThat(SensitiveLogArg.address("addr").type()).isEqualTo(MaskType.ADDRESS);
        assertThat(SensitiveLogArg.password("pwd").type()).isEqualTo(MaskType.PASSWORD);
        assertThat(SensitiveLogArg.custom("x").type()).isEqualTo(MaskType.CUSTOM);
    }

    @Test
    void toStringReturnsRawValue() {
        SensitiveLogArg arg = SensitiveLogArg.phone("13812348000");
        assertThat(arg.toString()).isEqualTo("13812348000");
    }
}

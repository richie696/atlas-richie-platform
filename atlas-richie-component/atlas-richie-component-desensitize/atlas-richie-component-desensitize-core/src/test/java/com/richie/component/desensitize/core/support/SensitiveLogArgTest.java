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

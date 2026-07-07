package com.richie.component.web.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumErrorMassageTest {

    @Test
    void requestParamsInvalid_hasExpectedMetadata() {
        EnumErrorMassage error = EnumErrorMassage.REQUEST_PARAMS_INVALID;

        assertThat(error.getStatusCode()).isEqualTo("00101001");
        assertThat(error.getDefaultMessage()).contains("invalid");
        assertThat(error.getI18nCode()).contains("requestParams");
    }
}

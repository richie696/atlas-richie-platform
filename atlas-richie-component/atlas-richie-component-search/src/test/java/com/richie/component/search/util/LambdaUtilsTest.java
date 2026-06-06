package com.richie.component.search.util;

import org.junit.jupiter.api.Test;

import java.lang.invoke.SerializedLambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LambdaUtilsTest {

    @Test
    void getFieldName_fromSerializedLambda() {
        SerializedLambda lambda = mock(SerializedLambda.class);
        when(lambda.getImplMethodName()).thenReturn("getUserName");

        assertThat(LambdaUtils.getFieldName(lambda)).isEqualTo("userName");
    }
}

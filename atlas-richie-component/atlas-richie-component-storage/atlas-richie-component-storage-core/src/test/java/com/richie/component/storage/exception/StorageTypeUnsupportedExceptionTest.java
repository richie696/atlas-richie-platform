package com.richie.component.storage.exception;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageTypeUnsupportedExceptionTest {

    @Test
    void message_shouldIncludeEngineAndType() {
        RuntimeException cause = new RuntimeException("root");
        var ex = new StorageTypeUnsupportedException(
                StorageEngineEnum.AWS_S3,
                StorageTypeEnum.MULTI_AZ_STANDARD,
                "unsupported");
        assertThat(ex.getMessage()).contains("AWS S3");
        assertThat(ex.getMessage()).contains("unsupported");

        var withCause = new StorageTypeUnsupportedException(
                StorageEngineEnum.AWS_S3, StorageTypeEnum.STANDARD, "msg", cause);
        assertThat(withCause.getCause()).isSameAs(cause);

        var causeOnly = new StorageTypeUnsupportedException(
                StorageEngineEnum.AWS_S3, StorageTypeEnum.STANDARD, cause);
        assertThat(causeOnly.getCause()).isSameAs(cause);
    }
}

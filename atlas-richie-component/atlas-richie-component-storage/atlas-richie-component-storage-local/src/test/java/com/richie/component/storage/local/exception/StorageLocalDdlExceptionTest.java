package com.richie.component.storage.local.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageLocalDdlExceptionTest {

    @Test
    void constructors_shouldPreserveMessage() {
        RuntimeException cause = new RuntimeException("root");
        assertThat(new StorageLocalDdlException("ddl").getMessage()).isEqualTo("ddl");
        assertThat(new StorageLocalDdlException("ddl", cause).getCause()).isSameAs(cause);
    }
}

package com.richie.component.storage.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageExceptionTest {

    @Test
    void constructors_shouldPreserveMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        assertThat(new StorageException()).isInstanceOf(Exception.class);
        assertThat(new StorageException("msg").getMessage()).isEqualTo("msg");
        assertThat(new StorageException("msg", cause).getCause()).isSameAs(cause);
        assertThat(new StorageException(cause).getCause()).isSameAs(cause);
        assertThat(new StorageException("msg", cause, true, true).getMessage()).isEqualTo("msg");
    }
}

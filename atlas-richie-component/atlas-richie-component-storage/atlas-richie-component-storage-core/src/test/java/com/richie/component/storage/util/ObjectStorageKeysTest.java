package com.richie.component.storage.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageKeysTest {

    @Test
    void realPath_withBasePath_joinsWithSlash() {
        assertThat(ObjectStorageKeys.realPath("uploads", "a.txt")).isEqualTo("uploads/a.txt");
    }

    @Test
    void realPath_blankBasePath_returnsKey() {
        assertThat(ObjectStorageKeys.realPath("", "only-key")).isEqualTo("only-key");
        assertThat(ObjectStorageKeys.realPath(null, "only-key")).isEqualTo("only-key");
    }
}

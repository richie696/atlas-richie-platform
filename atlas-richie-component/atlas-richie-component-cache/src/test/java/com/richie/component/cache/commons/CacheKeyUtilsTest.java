package com.richie.component.cache.commons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "plain-key, plain-key",
            "shard@@real-key, real-key",
            "a@@b@@c, b"
    })
    void getRealKey_stripsShardPrefix(String input, String expected) {
        assertThat(CacheKeyUtils.getRealKey(input)).isEqualTo(expected);
    }

    @Test
    void getRealKeys_mapsAllKeys() {
        List<String> keys = List.of("k1", "shard@@k2", "x@@y@@z");
        assertThat(CacheKeyUtils.getRealKeys(keys)).containsExactly("k1", "k2", "y");
    }
}

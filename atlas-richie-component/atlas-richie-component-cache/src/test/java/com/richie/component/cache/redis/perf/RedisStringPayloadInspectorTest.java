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
package com.richie.component.cache.redis.perf;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import com.richie.component.cache.support.OpsTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStringPayloadInspectorTest {

    private final AtlasRedisProperties.RedisPerf perf = OpsTestSupport.enabledPerf();

    @Test
    void inspect_nullIsOk() {
        assertThat(RedisStringPayloadInspector.inspect(null, perf).severity())
                .isEqualTo(RedisStringPayloadInspector.Severity.OK);
    }

    @Test
    void inspect_collectionIsError() {
        var r = RedisStringPayloadInspector.inspect(List.of("a"), perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.ERROR);
    }

    @Test
    void inspect_mapIsError() {
        var r = RedisStringPayloadInspector.inspect(Map.of("k", "v"), perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.ERROR);
    }

    @Test
    void inspect_objectArrayIsError() {
        var r = RedisStringPayloadInspector.inspect(new String[]{"a"}, perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.ERROR);
    }

    @Test
    void inspect_javaBeanIsWarn() {
        record Payload(String x) {
        }
        var r = RedisStringPayloadInspector.inspect(new Payload("x"), perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.WARN);
    }

    @Test
    void inspect_oversizedStringIsError() {
        perf.setStringPayloadMaxCharsError(10);
        var r = RedisStringPayloadInspector.inspect("x".repeat(10), perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.ERROR);
    }

    @Test
    void inspect_jsonLikeStringIsWarn() {
        perf.setJsonLikeMinCharsForWarn(4);
        var r = RedisStringPayloadInspector.inspect("{\"a\":1}", perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.WARN);
    }

    @Test
    void inspect_optionalEmptyIsOk() {
        assertThat(RedisStringPayloadInspector.inspect(java.util.Optional.empty(), perf).severity())
                .isEqualTo(RedisStringPayloadInspector.Severity.OK);
    }

    @Test
    void inspect_optionalPresentUnwraps() {
        var r = RedisStringPayloadInspector.inspect(java.util.Optional.of(List.of("a")), perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.ERROR);
    }

    @Test
    void inspect_byteArrayErrorWhenTooLarge() {
        perf.setStringPayloadMaxBytesError(4);
        var r = RedisStringPayloadInspector.inspect(new byte[]{1, 2, 3, 4, 5}, perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.ERROR);
    }

    @Test
    void inspect_byteArrayWarnWhenLarge() {
        perf.setStringPayloadMaxBytesWarn(2);
        perf.setStringPayloadMaxBytesError(10);
        var r = RedisStringPayloadInspector.inspect(new byte[]{1, 2, 3}, perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.WARN);
    }

    @Test
    void inspect_stringWarnWhenLarge() {
        perf.setStringPayloadMaxCharsWarn(3);
        perf.setStringPayloadMaxCharsError(10);
        var r = RedisStringPayloadInspector.inspect("abcd", perf);
        assertThat(r.severity()).isEqualTo(RedisStringPayloadInspector.Severity.WARN);
    }

    @Test
    void inspect_numberIsOk() {
        assertThat(RedisStringPayloadInspector.inspect(42, perf).severity())
                .isEqualTo(RedisStringPayloadInspector.Severity.OK);
    }
}

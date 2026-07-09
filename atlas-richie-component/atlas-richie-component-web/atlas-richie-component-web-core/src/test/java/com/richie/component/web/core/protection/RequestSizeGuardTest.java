/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeGuardTest {

    private static final long MAX_BODY = 1024L;
    private static final long MAX_HEADER = 256L;

    private final RequestSizeGuard guard = new RequestSizeGuard(MAX_BODY, MAX_HEADER, 413, 431);

    @Test
    void allow_whenUnderLimit() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/users")
                .header("Content-Length", "100")
                .header("Content-Type", "application/json")
                .build();
        assertThat(guard.check(ctx)).isEmpty();
    }

    @Test
    void deny_bodyTooLarge() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/upload")
                .header("Content-Length", String.valueOf(MAX_BODY + 1))
                .build();
        assertThat(guard.check(ctx))
                .isPresent()
                .get()
                .satisfies(d -> {
                    assertThat(d.status()).isEqualTo(413);
                    assertThat(d.reason()).isEqualTo("REQUEST_BODY_TOO_LARGE");
                    assertThat(d.limit()).isEqualTo(MAX_BODY);
                    assertThat(d.actual()).isEqualTo(MAX_BODY + 1);
                });
    }

    @Test
    void deny_headerTooLarge() {
        // 单个 header value 长度突破 MAX_HEADER
        String hugeValue = "x".repeat((int) (MAX_HEADER + 1));
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/")
                .header("X-Big", hugeValue)
                .build();
        assertThat(guard.check(ctx))
                .isPresent()
                .get()
                .satisfies(d -> {
                    assertThat(d.status()).isEqualTo(431);
                    assertThat(d.reason()).isEqualTo("REQUEST_HEADER_TOO_LARGE");
                });
    }

    @Test
    void allow_noContentLengthHeader() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("Accept", "application/json")
                .build();
        assertThat(guard.check(ctx)).isEmpty();
    }

    @Test
    void allow_invalidContentLength() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/")
                .header("Content-Length", "not-a-number")
                .build();
        assertThat(guard.check(ctx)).isEmpty();
    }

    @Test
    void deny_bodyCheckedBeforeHeader() {
        // body 和 header 都超限，应返回 body 决策（先 body 后 header 顺序）
        String hugeValue = "x".repeat(1024);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/")
                .header("Content-Length", String.valueOf(MAX_BODY + 1))
                .header("X-Big", hugeValue)
                .build();
        assertThat(guard.check(ctx))
                .isPresent()
                .get()
                .extracting(RequestSizeGuard.RequestSizeDecision::status)
                .isEqualTo(413);
    }

    @Test
    void allow_atExactBodyLimit() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/")
                .header("Content-Length", String.valueOf(MAX_BODY))
                .build();
        assertThat(guard.check(ctx)).isEmpty();
    }

    @Test
    void deny_negativeContentLengthPassesThrough() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/")
                .header("Content-Length", "-1")
                .build();
        assertThat(guard.check(ctx)).isEmpty();
    }

    @Test
    void constructor_rejectsNonPositiveBodyLimit() {
        assertThatThrownBy(() -> new RequestSizeGuard(0, MAX_HEADER, 413, 431))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBodyBytes");
    }

    @Test
    void constructor_rejectsNonPositiveHeaderLimit() {
        assertThatThrownBy(() -> new RequestSizeGuard(MAX_BODY, 0, 413, 431))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxHeaderBytes");
    }
}
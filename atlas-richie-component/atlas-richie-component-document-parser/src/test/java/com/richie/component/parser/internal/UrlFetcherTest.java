/*
 * Copyright (c) 2026 Richie (https://www.richie696.cn)
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

package com.richie.component.parser.internal;

import com.richie.component.parser.ParserSource;
import com.richie.component.parser.UrlFetchPolicy;
import com.richie.component.parser.exception.DocumentParseException;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UrlFetcher} 单元测试 — 三道防线 SSRF + HEAD + 内容层。
 * <p>
 * 用 JDK 内置 {@link HttpServer} 启动本地 server (随机端口,避免冲突),
 * 配合真实 JDK HttpClient 测试 URL 拉取器全链路。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class UrlFetcherTest {

    private HttpServer server;
    private int port;
    private UrlFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
        fetcher = new UrlFetcher();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("Default policy should reject private IPs (127.0.0.1)")
    void rejectsPrivateIpByDefault() throws Exception {
        registerContext("/test", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        // Use https:// scheme so the SSRF check (IP validation) fires before any
        // HTTP scheme rejection would short-circuit the test. The URL still
        // never connects because InetAddress.getByName resolves to 127.0.0.1,
        // which isPrivateIp() flags as private.
        URL url = URI.create("https://127.0.0.1:" + port + "/test").toURL();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> fetcher.fetch(new ParserSource.UrlSource(url, UrlFetchPolicy.defaults())));
        assertTrue(ex.getMessage().contains("private IP")
                        || ex.getMessage().contains("SSRF"),
                "expected SSRF rejection, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Default policy should reject HTTP scheme (HTTPS required)")
    void rejectsHttpSchemeByDefault() throws Exception {
        URL url = URI.create("http://example.com/test.pdf").toURL();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> fetcher.fetch(new ParserSource.UrlSource(url, UrlFetchPolicy.defaults())));
        assertTrue(ex.getMessage().contains("HTTPS")
                        || ex.getMessage().contains("scheme"),
                "expected scheme rejection, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Successful fetch with whitelisted Content-Type returns body bytes")
    void successfulFetch() throws Exception {
        byte[] body = "%PDF-1.4 mock content".getBytes();
        registerContext("/test.pdf", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });

        URL url = URI.create("http://127.0.0.1:" + port + "/test.pdf").toURL();
        try (InputStream in = fetcher.fetch(new ParserSource.UrlSource(url, allowTestPolicy()))) {
            assertNotNull(in);
            byte[] data = in.readAllBytes();
            assertEquals(new String(body), new String(data));
        }
    }

    @Test
    @DisplayName("HEAD layer should reject Content-Type outside whitelist")
    void rejectsInvalidContentType() throws Exception {
        byte[] body = new byte[]{0x00, 0x01, 0x02};
        registerContext("/malware.bin", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/x-msdownload");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });

        URL url = URI.create("http://127.0.0.1:" + port + "/malware.bin").toURL();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> fetcher.fetch(new ParserSource.UrlSource(url, allowTestPolicy())));
        assertTrue(ex.getMessage().contains("Content-Type")
                        || ex.getMessage().toLowerCase().contains("not allowed"),
                "expected Content-Type rejection, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("HEAD layer should reject when Content-Length exceeds maxBytes")
    void rejectsOversizedContent() throws Exception {
        registerContext("/huge.pdf", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.getResponseHeaders().set("Content-Length", "1073741824"); // 1 GB
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, 0);
            }
            exchange.close();
        });

        URL url = URI.create("http://127.0.0.1:" + port + "/huge.pdf").toURL();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> fetcher.fetch(new ParserSource.UrlSource(url, allowTestPolicy())));
        assertTrue(ex.getMessage().contains("Content-Length")
                        || ex.getMessage().contains("maxBytes"),
                "expected size rejection, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("HEAD layer should reject cross-host redirects (SSRF defense)")
    void rejectsCrossHostRedirect() throws Exception {
        registerContext("/redirect", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Location", "http://localhost:1/elsewhere");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        URL url = URI.create("http://127.0.0.1:" + port + "/redirect").toURL();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> fetcher.fetch(new ParserSource.UrlSource(url, allowTestPolicy())));
        assertTrue(ex.getMessage().contains("Cross-host")
                        || ex.getMessage().contains("redirect"),
                "expected cross-host redirect rejection, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("GET layer should reject HTTP errors (non-2xx)")
    void rejectsHttpError() throws Exception {
        registerContext("/server-error", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(500, -1);
            }
            exchange.close();
        });
        URL url = URI.create("http://127.0.0.1:" + port + "/server-error").toURL();
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> fetcher.fetch(new ParserSource.UrlSource(url, allowTestPolicy())));
        assertTrue(ex.getMessage().contains("500")
                        || ex.getMessage().contains("HTTP"),
                "expected HTTP 500 rejection, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("isPrivateIp should classify common private IP ranges")
    void isPrivateIpClassifiesRanges() throws Exception {
        // Loopback
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("127.0.0.1")));
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("127.255.255.254")));
        // Private class A
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("10.0.0.1")));
        // Private class B
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("172.16.0.1")));
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("172.31.255.254")));
        // Private class C
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("192.168.1.1")));
        // Link-local
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("169.254.1.1")));
        // IPv6 loopback
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("::1")));
        // IPv6 unique-local fc00::/7
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("fc00::1")));
        // IPv6 link-local fe80::/10
        assertTrue(UrlFetcher.isPrivateIp(java.net.InetAddress.getByName("fe80::1")));
    }

    // ============ Internal ============

    /**
     * Test policy that permits HTTP and private IPs, so we can exercise the
     * HttpServer-based scenarios. Only for unit tests; production configs
     * default to HTTPS-only and private-IP-blocked.
     */
    private UrlFetchPolicy allowTestPolicy() {
        return new UrlFetchPolicy(
                true,   // allowHttp (test only)
                true,   // allowPrivateIp (test only)
                true,
                1024L * 1024,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                List.of()
        );
    }

    @Test
    @DisplayName("checkIpMatches should throw when current IP differs from expected (rebinding)")
    void checkIpMatchesRejectsMismatch() throws Exception {
        InetAddress expected = InetAddress.getByName("93.184.216.34");
        InetAddress rebound = InetAddress.getByName("93.184.216.35");
        DocumentParseException ex = assertThrows(
                DocumentParseException.class,
                () -> UrlFetcher.checkIpMatches("example.com", rebound, expected));
        assertTrue(ex.getMessage().contains("DNS rebinding"));
        assertTrue(ex.getMessage().contains("example.com"));
        assertTrue(ex.getMessage().contains("93.184.216.34"));
        assertTrue(ex.getMessage().contains("93.184.216.35"));
    }

    @Test
    @DisplayName("checkIpMatches should pass when current IP equals expected")
    void checkIpMatchesAllowsEqual() throws Exception {
        InetAddress ip = InetAddress.getByName("1.1.1.1");
        assertDoesNotThrow(() -> UrlFetcher.checkIpMatches("any.host", ip, ip));
    }

    @Test
    @DisplayName("verifyDnsBinding should pass through when null host or expectedIp (no-op)")
    void verifyDnsBindingAllowsNulls() throws Exception {
        UrlFetcher fetcher = new UrlFetcher();
        assertDoesNotThrow(() -> fetcher.verifyDnsBinding(null, null));
        assertDoesNotThrow(() -> fetcher.verifyDnsBinding("example.com", null));
        assertDoesNotThrow(() -> fetcher.verifyDnsBinding(null, InetAddress.getByName("8.8.8.8")));
    }

    private void registerContext(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }
}

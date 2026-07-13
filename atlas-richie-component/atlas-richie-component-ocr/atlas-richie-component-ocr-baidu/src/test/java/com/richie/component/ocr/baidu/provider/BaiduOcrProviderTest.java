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
package com.richie.component.ocr.baidu.provider;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.jdk.JdkHttpAdapter;
import com.richie.component.ocr.baidu.config.BaiduOcrProperties;
import com.richie.component.ocr.model.MimeType;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.exception.OcrException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BaiduOcrProvider 单测 —— 用 JDK 内置 HttpServer 起本地 mock 服务,
 * 验证 HTTP 调用路径 + access_token 缓存 + 响应解析.
 *
 * <p>不验证真实百度 API, 真实集成测试在 vendor 真实环境接入阶段做.
 */
class BaiduOcrProviderTest {

    private HttpServer server;
    private int port;
    private HttpClient httpClient;
    private AtomicInteger tokenCalls;
    private AtomicInteger ocrCalls;
    private AtomicReference<String> lastOcrAccessToken;
    private AtomicReference<String> lastOcrLanguageType;

    @BeforeEach
    void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        httpClient = new JdkHttpAdapter(java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build());
        tokenCalls = new AtomicInteger();
        ocrCalls = new AtomicInteger();
        lastOcrAccessToken = new AtomicReference<>();
        lastOcrLanguageType = new AtomicReference<>();

        server.createContext("/oauth/2.0/token", exchange -> {
            tokenCalls.incrementAndGet();
            String body = "{\"access_token\":\"mock-token-123\",\"expires_in\":2592000,\"scope\":\"public\"}";
            respondJson(exchange, 200, body);
        });

        server.createContext("/rest/2.0/ocr/v1/general_basic", exchange -> {
            ocrCalls.incrementAndGet();
            String body;
            try (var in = exchange.getRequestBody()) {
                String form = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                lastOcrAccessToken.set(extractFormValue(form, "access_token"));
                lastOcrLanguageType.set(extractFormValue(form, "language_type"));
            }
            body = "{\"words_result\":[{\"words\":\"你好世界\"},{\"words\":\"hello world\"}],"
                    + "\"words_result_num\":2,\"log_id\":\"mock-log-id\"}";
            respondJson(exchange, 200, body);
        });

        server.start();
    }

    @AfterEach
    void stopMockServer() {
        if (server != null) server.stop(0);
    }

    private BaiduOcrProperties baseProps(String apiKey, String secretKey) {
        BaiduOcrProperties props = new BaiduOcrProperties();
        props.setEndpoint("http://127.0.0.1:" + port);
        if (apiKey != null) props.setApiKey(apiKey);
        if (secretKey != null) props.setSecretKey(secretKey);
        return props;
    }

    @Test
    void recognize_sendsFormRequest_andParsesWordsResult() throws Exception {
        BaiduOcrProperties props = baseProps("test-key", "test-secret");
        props.setTimeoutMs(5000L);
        BaiduOcrProvider provider = new BaiduOcrProvider(props, httpClient);

        OcrImage image = new OcrImage.Bytes(new byte[]{1, 2, 3}, MimeType.PNG);
        OcrResult result = provider.recognize(image, OcrOptions.builder().build());

        assertEquals(1, tokenCalls.get(), "should call token endpoint exactly once (cached)");
        assertEquals(1, ocrCalls.get(), "should call ocr endpoint once");
        assertEquals("mock-token-123", lastOcrAccessToken.get());
        assertNotNull(lastOcrLanguageType.get(), "language_type should be set");
        assertTrue(result.text().contains("你好世界"));
        assertTrue(result.text().contains("hello world"));
        assertEquals(2, result.blocks().size());
    }

    @Test
    void recognize_reusesAccessToken_acrossMultipleCalls() throws Exception {
        BaiduOcrProvider provider = new BaiduOcrProvider(baseProps("test-key", "test-secret"), httpClient);

        OcrImage image = new OcrImage.Bytes(new byte[]{1}, MimeType.PNG);
        for (int i = 0; i < 3; i++) {
            provider.recognize(image, OcrOptions.builder().build());
        }

        assertEquals(1, tokenCalls.get(), "access_token must be cached across calls");
        assertEquals(3, ocrCalls.get(), "each OCR call hits the OCR endpoint");
    }

    @Test
    void recognize_errorCodeNonZero_throwsUnrecognized() throws Exception {
        // 重写 OCR endpoint 返回 error_code != 0
        server.removeContext("/rest/2.0/ocr/v1/general_basic");
        server.createContext("/rest/2.0/ocr/v1/general_basic", exchange -> {
            ocrCalls.incrementAndGet();
            respondJson(exchange, 200,
                    "{\"error_code\":216100,\"error_msg\":\"invalid param\"}");
        });

        BaiduOcrProvider provider = new BaiduOcrProvider(baseProps("test-key", "test-secret"), httpClient);

        OcrImage image = new OcrImage.Bytes(new byte[]{1}, MimeType.PNG);
        OcrException.Unrecognized ex = assertThrows(OcrException.Unrecognized.class,
                () -> provider.recognize(image, OcrOptions.builder().build()));
        assertTrue(ex.getMessage().contains("216100"));
    }

    @Test
    void recognize_httpServerReturns500_throwsProviderUnavailable() throws Exception {
        server.removeContext("/rest/2.0/ocr/v1/general_basic");
        server.createContext("/rest/2.0/ocr/v1/general_basic", exchange -> {
            ocrCalls.incrementAndGet();
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        });

        BaiduOcrProvider provider = new BaiduOcrProvider(baseProps("test-key", "test-secret"), httpClient);

        OcrImage image = new OcrImage.Bytes(new byte[]{1}, MimeType.PNG);
        assertThrows(OcrException.ProviderUnavailable.class,
                () -> provider.recognize(image, OcrOptions.builder().build()));
    }

    @Test
    void construct_missingApiKey_throwsConfigMissing() {
        // 故意不设 api-key / secret-key
        assertThrows(OcrException.ConfigMissing.class,
                () -> new BaiduOcrProvider(baseProps(null, "test-secret"), httpClient));
        assertThrows(OcrException.ConfigMissing.class,
                () -> new BaiduOcrProvider(baseProps("test-key", null), httpClient));
    }

    // --- helpers ---

    private static void respondJson(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractFormValue(String form, String key) {
        for (String pair : form.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = pair.substring(0, eq);
                String v = pair.substring(eq + 1);
                if (k.equals(key)) {
                    return java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}

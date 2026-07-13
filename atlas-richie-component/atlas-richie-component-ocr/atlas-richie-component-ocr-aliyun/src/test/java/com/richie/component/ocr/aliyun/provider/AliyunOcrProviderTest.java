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
package com.richie.component.ocr.aliyun.provider;

import tools.jackson.databind.JsonNode;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.jdk.JdkHttpAdapter;
import com.richie.component.ocr.aliyun.config.AliyunOcrProperties;
import com.richie.component.ocr.model.MimeType;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.exception.OcrException;
import com.richie.context.utils.data.JsonUtils;
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
 * AliyunOcrProvider 单测 —— Mock 本地 HttpServer 验证协议细节.
 *
 * <p>不依赖真实阿里云 API, 真实集成测试在 vendor 接入阶段做.
 */
class AliyunOcrProviderTest {

    private HttpServer server;
    private int port;
    private HttpClient httpClient;
    private AtomicInteger callCount;
    private AtomicReference<String> lastAuthHeader;
    private AtomicReference<String> lastBodyFieldBody;
    private AtomicReference<String> lastBodyFieldUrl;

    @BeforeEach
    void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        httpClient = new JdkHttpAdapter(java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build());
        callCount = new AtomicInteger();
        lastAuthHeader = new AtomicReference<>();
        lastBodyFieldUrl = new AtomicReference<>();
        lastBodyFieldBody = new AtomicReference<>();

        server.createContext("/v1/ocr/recognize", exchange -> {
            callCount.incrementAndGet();
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try (var in = exchange.getRequestBody()) {
                JsonNode body = JsonUtils.getInstance().convertJsonNode(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (body.hasNonNull("url")) {
                    lastBodyFieldUrl.set(body.get("url").asText());
                    lastBodyFieldBody.set(null);
                } else if (body.hasNonNull("body")) {
                    lastBodyFieldUrl.set(null);
                    lastBodyFieldBody.set(body.get("body").asText());
                }
            }
            String resp = "{\"content\":\"你好\\nhello\",\"prism_wnum\":180,\"prism_wordsInfo\":["
                    + "{\"word\":\"你好\",\"prob\":99,\"pos\":[[0,0],[100,0],[100,20],[0,20]]},"
                    + "{\"word\":\"hello\",\"prob\":95,\"pos\":[[0,30],[80,30],[80,50],[0,50]]}],"
                    + "\"requestId\":\"mock-req-1\",\"ret\":\"0\"}";
            respondJson(exchange, 200, resp);
        });

        server.start();
    }

    @AfterEach
    void stopMockServer() {
        if (server != null) server.stop(0);
    }

    private AliyunOcrProperties baseProps(String appCode) {
        AliyunOcrProperties props = new AliyunOcrProperties();
        props.setEndpoint("http://127.0.0.1:" + port);
        if (appCode != null) {
            AliyunOcrProperties.Credentials creds = new AliyunOcrProperties.Credentials();
            creds.setAppCode(appCode);
            props.setCredentials(creds);
        }
        return props;
    }

    @Test
    void recognize_bytesImage_sendsBase64Body_andParsesContent() throws Exception {
        AliyunOcrProperties props = baseProps("test-app-code");
        props.setTimeoutMs(5000L);
        AliyunOcrProvider provider = new AliyunOcrProvider(props, httpClient);

        byte[] imageBytes = new byte[]{1, 2, 3, 4, 5};
        OcrImage image = new OcrImage.Bytes(imageBytes, MimeType.PNG);
        OcrResult result = provider.recognize(image, OcrOptions.builder().build());

        assertEquals(1, callCount.get());
        assertEquals("APPCODE test-app-code", lastAuthHeader.get());
        assertNotNull(lastBodyFieldBody.get(), "Bytes image should be sent as body field");
        assertTrue(lastBodyFieldUrl.get() == null, "Url field should not be set when body is set");
        assertTrue(result.text().contains("你好"));
        assertEquals(2, result.blocks().size());
        assertTrue(result.overallConfidence() > 0f);
    }

    @Test
    void recognize_urlImage_sendsUrlField() throws Exception {
        AliyunOcrProperties props = baseProps("test-app-code");
        AliyunOcrProvider provider = new AliyunOcrProvider(props, httpClient);

        OcrImage image = new OcrImage.Url("https://example.com/img.jpg", null);
        provider.recognize(image, OcrOptions.builder().build());

        assertEquals("https://example.com/img.jpg", lastBodyFieldUrl.get());
        assertTrue(lastBodyFieldBody.get() == null, "body field should not be set when url is set");
    }

    @Test
    void recognize_retNonZero_throwsUnrecognized() throws Exception {
        server.removeContext("/v1/ocr/recognize");
        server.createContext("/v1/ocr/recognize", exchange -> {
            callCount.incrementAndGet();
            respondJson(exchange, 200,
                    "{\"ret\":\"500\",\"content\":\"\",\"prism_wordsInfo\":[],\"prism_wnum\":0}");
        });

        AliyunOcrProvider provider = new AliyunOcrProvider(baseProps("x"), httpClient);

        assertThrows(OcrException.Unrecognized.class,
                () -> provider.recognize(
                        new OcrImage.Bytes(new byte[]{1}, MimeType.PNG),
                        OcrOptions.builder().build()));
    }

    @Test
    void recognize_http500_throwsProviderUnavailable() throws Exception {
        server.removeContext("/v1/ocr/recognize");
        server.createContext("/v1/ocr/recognize", exchange -> {
            callCount.incrementAndGet();
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        });

        AliyunOcrProvider provider = new AliyunOcrProvider(baseProps("x"), httpClient);

        assertThrows(OcrException.ProviderUnavailable.class,
                () -> provider.recognize(
                        new OcrImage.Bytes(new byte[]{1}, MimeType.PNG),
                        OcrOptions.builder().build()));
    }

    @Test
    void construct_missingAppCode_throwsConfigMissing() {
        // 故意不设 credentials.app-code
        assertThrows(OcrException.ConfigMissing.class,
                () -> new AliyunOcrProvider(baseProps(null), httpClient));
    }

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
}

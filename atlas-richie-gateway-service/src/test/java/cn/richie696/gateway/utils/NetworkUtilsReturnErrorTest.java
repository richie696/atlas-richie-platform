package cn.richie696.gateway.utils;

import cn.richie696.contract.model.ApiResult;
import cn.richie696.gateway.error.GatewayErrorRegistry;
import cn.richie696.gateway.error.codes.GatewayErrorCode;
import cn.richie696.gateway.filter.common.infrastructure.RequestIdGlobalFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NetworkUtils#returnError} Wave 2 契约测试。
 * <p>
 * 覆盖以下契约：
 * </p>
 * <ul>
 *   <li>HTTP 响应行写 {@link HttpStatus#OK}（历史兼容，本期不修复）。</li>
 *   <li>写入 {@code X-Request-Id} 响应头：传入则沿用，否则生成 32 位 hex。</li>
 *   <li>{@link ApiResult} 包含 {@code requestId} 与 {@code helpUrl}。</li>
 *   <li>code 字段使用 {@code GW-*} 命名空间。</li>
 *   <li>找不到匹配的 HTTP 状态码时，使用 {@code GW-SYSTEM-0001} 兜底。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
class NetworkUtilsReturnErrorTest {

    @Test
    @DisplayName("returnError 写入 X-Request-Id 响应头（沿用入参）")
    void testReturnError_PropagatesRequestIdHeader() {
        MockServerHttpResponse response = new MockServerHttpResponse();
        String existingRequestId = "test-request-id-from-filter-0001";
        response.getHeaders().set(RequestIdGlobalFilter.HEADER_NAME, existingRequestId);

        Mono<Void> result = NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, "token invalid");

        StepVerifier.create(result).verifyComplete();
        assertEquals(existingRequestId,
                response.getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME),
                "X-Request-Id 响应头应沿用入参中的请求 ID");
    }

    @Test
    @DisplayName("returnError 在无 X-Request-Id 时生成 32 位 hex")
    void testReturnError_GeneratesRequestIdWhenMissing() {
        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Void> result = NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, "token invalid");

        StepVerifier.create(result).verifyComplete();
        String requestId = response.getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        assertNotNull(requestId, "X-Request-Id 响应头必须存在");
        assertEquals(32, requestId.length(), "X-Request-Id 应为 32 位 hex");
        assertTrue(requestId.matches("[0-9a-f]{32}"),
                "X-Request-Id 应为 32 位小写十六进制字符串，实际：" + requestId);
    }

    @Test
    @DisplayName("returnError 返回 HTTP 200 状态码（历史兼容，不修改）")
    void testReturnError_ReturnsHttp200ForBackwardCompatibility() {
        MockServerHttpResponse response = new MockServerHttpResponse();
        Mono<Void> result = NetworkUtils.returnError(response, HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "returnError 必须保持 HTTP 200 状态码（历史兼容）");
    }

    @Test
    @DisplayName("returnError 写入 GW-* 错误码与 helpUrl")
    void testReturnError_PopulatesGatewayErrorCodeAndHelpUrl() {
        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Void> result = NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, "token invalid");

        StepVerifier.create(result).verifyComplete();

        String body = readBodyAsString(response);
        assertTrue(body.contains("\"code\":\"GW-AUTH-"),
                "returnError 响应体 code 字段应使用 GW-AUTH-* 命名空间，实际：" + body);
        assertTrue(body.contains("\"helpUrl\":\"/gateway/errors/GW-AUTH-"),
                "returnError 响应体 helpUrl 字段应指向 /gateway/errors/GW-AUTH-*，实际：" + body);
    }

    @Test
    @DisplayName("returnError 找不到匹配状态码时使用 GW-SYSTEM-0001 兜底")
    void testReturnError_FallbackToSystemError() {
        MockServerHttpResponse response = new MockServerHttpResponse();
        HttpStatus unregistered = HttpStatus.I_AM_A_TEAPOT;

        Mono<Void> result = NetworkUtils.returnError(response, unregistered, "tea pot");

        StepVerifier.create(result).verifyComplete();

        String body = readBodyAsString(response);
        assertTrue(body.contains(GatewayErrorCode.GW_SYSTEM_0001.getErrorCode()),
                "未注册状态码应使用 GW-SYSTEM-0001 兜底，实际：" + body);
    }

    @Test
    @DisplayName("returnError 响应 Content-Type 为 application/json;charset=UTF-8")
    void testReturnError_SetsJsonContentType() {
        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Void> result = NetworkUtils.returnError(response, HttpStatus.FORBIDDEN, "forbidden");

        StepVerifier.create(result).verifyComplete();
        String contentType = response.getHeaders().getFirst("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.toLowerCase().contains("application/json"),
                "Content-Type 应包含 application/json，实际：" + contentType);
    }

    @Test
    @DisplayName("returnError 通过 Registry 查找的 code 与 helpUrl 一致")
    void testReturnError_CodeAndHelpUrlConsistentWithRegistry() {
        MockServerHttpResponse response = new MockServerHttpResponse();

        Mono<Void> result = NetworkUtils.returnError(response, HttpStatus.NOT_FOUND, "not found");

        StepVerifier.create(result).verifyComplete();

        String body = readBodyAsString(response);

        String expectedCode = GatewayErrorRegistry.getByHttpStatus(404).getErrorCode();
        String expectedHelpUrl = GatewayErrorRegistry.getByHttpStatus(404).getHelpUrl();
        assertTrue(body.contains("\"code\":\"" + expectedCode + "\""),
                "body 中 code 应为 " + expectedCode + "，实际：" + body);
        assertTrue(body.contains("\"helpUrl\":\"" + expectedHelpUrl + "\""),
                "body 中 helpUrl 应为 " + expectedHelpUrl + "，实际：" + body);
    }

    private static String readBodyAsString(MockServerHttpResponse response) {
        Flux<DataBuffer> body = response.getBody();
        if (body == null) {
            return "";
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (DataBuffer buffer : body.toIterable()) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            baos.writeBytes(bytes);
        }
        try {
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


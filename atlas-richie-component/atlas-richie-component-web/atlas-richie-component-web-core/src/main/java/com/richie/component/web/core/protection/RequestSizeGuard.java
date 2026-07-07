package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.WebRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

/**
 * 请求体 / 请求头字节阈值检查（README.md §4.8.2 A 组）。
 * <p>
 * 检查 {@code Content-Length} header（body 字节数）与所有 request headers 的总字节数。
 * 任意一个超限即 deny。本类只做"事前判断"，不做真实流截断——流截断交由容器自身。
 *
 * <h2>状态码</h2>
 * <ul>
 *   <li>body 超限 → 默认 413 Payload Too Large</li>
 *   <li>header 超限 → 默认 431 Request Header Fields Too Large</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class RequestSizeGuard {

    /** HTTP/1.1 标准：name + ": " + value + "\r\n" 每条 header 占用的字节开销近似值。 */
    private static final int HEADER_LINE_OVERHEAD_BYTES = 4;

    private final long maxBodyBytes;
    private final long maxHeaderBytes;
    private final int bodyDenyStatus;
    private final int headerDenyStatus;

    public RequestSizeGuard(long maxBodyBytes,
                            long maxHeaderBytes,
                            int bodyDenyStatus,
                            int headerDenyStatus) {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("maxBodyBytes must be > 0, got: " + maxBodyBytes);
        }
        if (maxHeaderBytes <= 0) {
            throw new IllegalArgumentException("maxHeaderBytes must be > 0, got: " + maxHeaderBytes);
        }
        this.maxBodyBytes = maxBodyBytes;
        this.maxHeaderBytes = maxHeaderBytes;
        this.bodyDenyStatus = bodyDenyStatus;
        this.headerDenyStatus = headerDenyStatus;
    }

    /**
     * 检查请求大小；任一维度超限返回 deny 决策（先 body 后 header）。
     *
     * @param ctx 请求上下文
     * @return deny 时 {@code Optional.of(decision)}；放行时 {@code Optional.empty()}
     */
    public Optional<RequestSizeDecision> check(WebRequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        Optional<RequestSizeDecision> bodyDecision = checkBody(ctx);
        if (bodyDecision.isPresent()) {
            return bodyDecision;
        }
        return checkHeaders(ctx);
    }

    private Optional<RequestSizeDecision> checkBody(WebRequestContext ctx) {
        String clHeader = ctx.header("Content-Length");
        if (clHeader == null || clHeader.isBlank()) {
            return Optional.empty();
        }
        long bodyBytes;
        try {
            bodyBytes = Long.parseLong(clHeader.trim());
        } catch (NumberFormatException e) {
            log.debug("Content-Length is not a valid number: {}; pass through", clHeader);
            return Optional.empty();
        }
        if (bodyBytes < 0) {
            log.debug("Content-Length is negative: {}; pass through", bodyBytes);
            return Optional.empty();
        }
        if (bodyBytes > maxBodyBytes) {
            return Optional.of(new RequestSizeDecision(
                    bodyDenyStatus, "REQUEST_BODY_TOO_LARGE", maxBodyBytes, bodyBytes));
        }
        return Optional.empty();
    }

    private Optional<RequestSizeDecision> checkHeaders(WebRequestContext ctx) {
        long total = 0;
        for (String name : ctx.headerNames()) {
            int nameLen = name.length();
            for (String value : ctx.headers(name)) {
                total += (long) nameLen + value.length() + HEADER_LINE_OVERHEAD_BYTES;
            }
        }
        if (total > maxHeaderBytes) {
            return Optional.of(new RequestSizeDecision(
                    headerDenyStatus, "REQUEST_HEADER_TOO_LARGE", maxHeaderBytes, total));
        }
        return Optional.empty();
    }

    /**
     * deny 决策载荷。
     */
    public record RequestSizeDecision(int status, String reason, long limit, long actual) {
    }
}
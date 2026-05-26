package com.richie.component.http.bean;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import okhttp3.Protocol;

import java.util.Arrays;

/**
 * HTTP 协议版本枚举
 */
@Getter
@RequiredArgsConstructor
public enum HttpProtocol {

    /**
     * HTTP/1.0 默认不使用持久化套接字的明文协议框架（已过时）
     */
    HTTP_1_0("http/1.0"),

    /**
     * 包含持久连接的纯文本框架。 HTTP/1.1的语义在HTTP/2上分层。
     */
    HTTP_1_1("http/1.1"),

    /**
     * IETF 的二进制帧协议，包括标头压缩、在同一套接字上多路复用多个请求以及服务器推送。HTTP1.1语义在HTTP2上分层。
     * HTTP/2 需要部署使用 TLS 1.2 支持的 HTTP/2
     */
    HTTP_2("h2"),

    /**
     * 明文HTTP2，没有“upgrade”往返。此选项要求客户端事先知道服务器支持明文 HTTP2。
     */
    H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),

    /**
     * QUIC（快速UDP互联网连接）是UDP之上的一种新的多路复用和安全传输，从头开始设计并针对HTTP2语义进行了优化。HTTP1.1语义在HTTP2上分层。
     */
    QUIC("quic");

    private final String protocol;

    /**
     * 转换协议
     *
     * @param protocol 协议
     * @return HttpProtocol
     */
    public static HttpProtocol convert(Protocol protocol) {
        return Arrays.stream(values()).filter(obj -> obj.getProtocol().equalsIgnoreCase(protocol.toString())).findFirst().orElseThrow();
    }
}

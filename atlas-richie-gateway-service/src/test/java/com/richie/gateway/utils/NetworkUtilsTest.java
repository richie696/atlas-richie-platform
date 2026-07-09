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
package com.richie.gateway.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkUtilsTest {

    @Test
    @DisplayName("getClientHost 返回 ServerHttpRequest 的远端主机字符串")
    void getClientHost_returnsRemoteHostString() {
        ServerHttpRequest request = MockServerHttpRequest.get("/")
                .remoteAddress(InetSocketAddress.createUnresolved("203.0.113.5", 8080))
                .build();

        assertThat(NetworkUtils.getClientHost(request)).isEqualTo("203.0.113.5");
    }

    @Nested
    @DisplayName("isLoopBackAddress 判定")
    class IsLoopBackAddressTests {

        @Test
        @DisplayName("IPv4 回环 127.0.0.1 视为本地")
        void ipv4Loopback() {
            assertThat(NetworkUtils.isLoopBackAddress("127.0.0.1")).isTrue();
        }

        @Test
        @DisplayName("localhost 视为本地")
        void localhostName() {
            assertThat(NetworkUtils.isLoopBackAddress("localhost")).isTrue();
        }

        @Test
        @DisplayName("IPv6 回环 ::1 视为本地")
        void ipv6Loopback() {
            assertThat(NetworkUtils.isLoopBackAddress("::1")).isTrue();
        }

        @Test
        @DisplayName("公网 IP 视为非本地")
        void publicAddress() {
            assertThat(NetworkUtils.isLoopBackAddress("8.8.8.8")).isFalse();
        }
    }

    @Nested
    @DisplayName("isIntranetAddress 判定(基于预生成 SUBNETS 列表)")
    class IsIntranetAddressTests {

        @Test
        @DisplayName("回环地址也走 isLoopBackAddress 分支,直接返回 true")
        void loopbackIsIntranet() {
            assertThat(NetworkUtils.isIntranetAddress("127.0.0.1")).isTrue();
        }

        @Test
        @DisplayName("10.0.0.0/8 段地址为内网")
        void classAIntranet() {
            assertThat(NetworkUtils.isIntranetAddress("10.1.2.3")).isTrue();
        }

        @Test
        @DisplayName("172.16/12 段地址为内网(覆盖 172.16 ~ 172.31 全部 16 个 B 段)")
        void classBIntranet() {
            assertThat(NetworkUtils.isIntranetAddress("172.20.5.6")).isTrue();
            assertThat(NetworkUtils.isIntranetAddress("172.31.255.254")).isTrue();
        }

        @Test
        @DisplayName("192.168.0.0/16 段地址为内网")
        void classCIntranet() {
            assertThat(NetworkUtils.isIntranetAddress("192.168.1.1")).isTrue();
        }

        @Test
        @DisplayName("公网 IP 视为非内网")
        void publicIsNotIntranet() {
            assertThat(NetworkUtils.isIntranetAddress("8.8.8.8")).isFalse();
        }
    }

    @Nested
    @DisplayName("getIP 多代理头解析")
    class GetIpTests {

        @Test
        @DisplayName("x-forwarded-for 头存在时直接采用")
        void preferXForwardedFor() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("x-forwarded-for", "203.0.113.7")
                    .remoteAddress(InetSocketAddress.createUnresolved("10.0.0.1", 80))
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("x-forwarded-for 为 unknown 时降级到 Proxy-Client-IP")
        void fallbackToProxyClientIp() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("x-forwarded-for", "unknown")
                    .header("Proxy-Client-IP", "203.0.113.8")
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("203.0.113.8");
        }

        @Test
        @DisplayName("未知链一路降级到 WL-Proxy-Client-IP")
        void fallbackToWlProxyClientIp() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("x-forwarded-for", "unknown")
                    .header("Proxy-Client-IP", "")
                    .header("WL-Proxy-Client-IP", "203.0.113.9")
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("未知链一路降级到 HTTP_CLIENT_IP")
        void fallbackToHttpClientIp() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("HTTP_CLIENT_IP", "203.0.113.10")
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("203.0.113.10");
        }

        @Test
        @DisplayName("未知链一路降级到 HTTP_X_FORWARDED_FOR")
        void fallbackToHttpXForwardedFor() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("HTTP_X_FORWARDED_FOR", "203.0.113.11")
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("203.0.113.11");
        }

        @Test
        @DisplayName("所有代理头均为 unknown/空 时降级到 remoteAddress")
        void fallbackToRemoteAddress() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("x-forwarded-for", "unknown")
                    .header("Proxy-Client-IP", "unknown")
                    .header("WL-Proxy-Client-IP", "unknown")
                    .header("HTTP_CLIENT_IP", "unknown")
                    .header("HTTP_X_FORWARDED_FOR", "unknown")
                    .remoteAddress(InetSocketAddress.createUnresolved("198.51.100.42", 80))
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("198.51.100.42");
        }

        @Test
        @DisplayName("多 IP 逗号分隔时取第一个")
        void takeFirstOfCommaList() {
            ServerHttpRequest request = MockServerHttpRequest.get("/")
                    .header("x-forwarded-for", "203.0.113.7, 10.0.0.2, 192.168.1.1")
                    .build();

            assertThat(NetworkUtils.getIP(request)).isEqualTo("203.0.113.7");
        }
    }

    @Test
    @DisplayName("getUserAgent 返回 User-Agent 头首值,无则 null")
    void getUserAgentReturnsHeader() {
        ServerHttpRequest withHeader = MockServerHttpRequest.get("/")
                .header("User-Agent", "curl/8.0")
                .build();
        ServerHttpRequest withoutHeader = MockServerHttpRequest.get("/").build();

        assertThat(NetworkUtils.getUserAgent(withHeader)).isEqualTo("curl/8.0");
        assertThat(NetworkUtils.getUserAgent(withoutHeader)).isNull();
    }

    @Test
    @DisplayName("returnError 写入 200 + JSON 错误码,Content-Type 为 application/json;charset=UTF-8")
    void returnErrorWritesJsonBody() {
        MockServerHttpResponse response = new MockServerHttpResponse();

        StepVerifier.create(NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, "token expired"))
                .verifyComplete();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst("Content-Type")).isEqualTo("application/json;charset=UTF-8");
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"401\"").contains("token expired");
    }
}

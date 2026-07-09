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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * URL 拉取器 — 三道防线之协议层 + 内容嗅探前置。
 * <p>
 * <b>防线 1 (SSRF)</b>: 解析 URL 拿到 host,DNS 解析得到 IP,校验 IP 不在黑名单
 * (127.x / 10.x / 172.16-31 / 192.168 / 169.254 / ::1 / fc00::/7)。
 * 重定向不允许跨主机,每次重定向都重新校验新 host。
 * <p>
 * <b>防线 2 (协议层 HEAD)</b>: HEAD 请求检查 Content-Type 白名单 + Content-Length 上限。
 * <p>
 * <b>防线 3 (内容层)</b>: 在 GET 拿到字节流后,由 FormatDetector 做 magic bytes 嗅探
 * (由 Facade/Router 在拿到 InputStream 后调用,本类只负责下载)。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public final class UrlFetcher {

    /**
     * 允许下载的 Content-Type 白名单(精确匹配,不含字符编码)。
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "text/html",
            "text/xml",
            "application/xml",
            "application/octet-stream"  // 兜底:某些服务器不返回精确 MIME
    );

    private final HttpClient httpClient;

    public UrlFetcher() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)  // 手动控制重定向 + 校验
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public UrlFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 下载 URL 字节流,返回 InputStream(由调用方负责关闭)。
     * <p>
     * 应用三道防线的第 1 道 (SSRF) 和第 2 道 (HEAD 协议层),内容层交给 FormatDetector。
     */
    public InputStream fetch(ParserSource.UrlSource source) {
        URI uri = URI.create(source.url().toString());
        UrlFetchPolicy policy = source.policy();

        validateScheme(uri, policy);
        InetAddress resolved = resolveAndValidate(uri, policy);

        // 第一道 HEAD 协议层
        performHead(uri, policy, resolved);

        // GET 下载
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(policy.readTimeout())
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new DocumentParseException(
                        "URL fetch failed (HTTP " + resp.statusCode() + "): " + uri);
            }
            int len = resp.body() == null ? 0 : resp.body().length;
            if (len > policy.maxBytes()) {
                throw new DocumentParseException(
                        "URL content exceeds maxBytes limit (" + policy.maxBytes() + "): " + uri);
            }
            return new ByteArrayInputStream(resp.body() == null ? new byte[0] : resp.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DocumentParseException(
                    "Failed to download URL: " + uri, e);
        }
    }

    // ============ SSRF 防线 1 ============

    private void validateScheme(URI uri, UrlFetchPolicy policy) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new DocumentParseException("URL missing scheme: " + uri);
        }
        boolean isHttps = "https".equalsIgnoreCase(scheme);
        boolean isHttp = "http".equalsIgnoreCase(scheme);
        if (!isHttps && !(isHttp && policy.allowHttp())) {
            throw new DocumentParseException(
                    "URL scheme not allowed (HTTPS required, allowHttp=" + policy.allowHttp() + "): " + uri);
        }
    }

    private InetAddress resolveAndValidate(URI uri, UrlFetchPolicy policy) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new DocumentParseException("URL missing host: " + uri);
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (IOException e) {
            throw new DocumentParseException("Failed to resolve host: " + host, e);
        }
        if (!policy.allowPrivateIp() && isPrivateIp(addr)) {
            throw new DocumentParseException(
                    "SSRF blocked: host resolves to private IP " + addr.getHostAddress() + ": " + host);
        }
        return addr;
    }

    /**
     * 简易内网 IP 判断 — 覆盖 127 / 10 / 172.16-31 / 192.168 / 169.254 / ::1 / fc00::/7。
     * <p>
     * 未实现完整 CIDR 校验,后续 phase 可基于 Guava InetAddresses 增强。
     */
    static boolean isPrivateIp(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            // IPv4
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 10) return true;                    // 10.0.0.0/8
            if (b0 == 127) return true;                   // 127.0.0.0/8
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;  // 172.16.0.0/12
            if (b0 == 192 && b1 == 168) return true;      // 192.168.0.0/16
            return b0 == 169 && b1 == 254;      // 169.254.0.0/16
        }
        if (bytes.length == 16) {
            // IPv6: ::1 / fc00::/7 (unique local) / fe80::/10 (link-local)
            return (bytes[0] & 0xFE) == 0xFC;   // fc00::/7
            // ::1 已由 isLoopbackAddress 覆盖,fe80::/10 已由 isLinkLocalAddress 覆盖
        }
        return false;
    }

    // ============ HEAD 协议层防线 2 ============

    private void performHead(URI uri, UrlFetchPolicy policy, InetAddress expectedIp) {
        if (!policy.followRedirects()) {
            return;
        }

        // SSRF 防线 1 → 2 之间 — 防 DNS rebinding / TOCTOU 攻击。
        // attacker 在 T0 解析得白名单 IP 通过校验, T1 改 DNS 指向内网,
        // T2 实际 HEAD/GET 撞入 10.x.x.x。通过 re-resolve 把攻击窗口
        // 缩到 ~ms 级 (无法 100% 防; 真正的硬防护需绑定 IP 直连 +
        // HttpClient 强制不走 DNS, 见 README "Known Limitations")。
        verifyDnsBinding(uri.getHost(), expectedIp);

        try {
            URI current = uri;
            int redirects = 0;
            int maxRedirects = 5;
            while (redirects++ < maxRedirects) {
                HttpRequest head = HttpRequest.newBuilder()
                        .uri(current)
                        .timeout(policy.connectTimeout())
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> resp = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
                int code = resp.statusCode();
                if (code >= 300 && code < 400) {
                    // 重定向:校验 Location host 不跨主机(SSRF 防护)
                    String location = resp.headers().firstValue("Location").orElse(null);
                    if (location == null) {
                        throw new DocumentParseException(
                                "Redirect without Location header: " + current);
                    }
                    URI next = URI.create(location);
                    if (next.getHost() != null && !next.getHost().equals(uri.getHost())) {
                        throw new DocumentParseException(
                                "Cross-host redirect blocked (SSRF): " + current + " → " + next);
                    }
                    current = next;
                    continue;
                }
                if (code / 100 != 2) {
                    throw new DocumentParseException(
                            "HEAD failed (HTTP " + code + "): " + current);
                }
                // 校验 Content-Type
                String contentType = resp.headers()
                        .firstValue("Content-Type")
                        .map(s -> s.split(";")[0].trim().toLowerCase())
                        .orElse("application/octet-stream");
                if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                    throw new DocumentParseException(
                            "URL Content-Type not allowed: " + contentType + " (URL: " + current + ")");
                }
                // 校验 Content-Length
                resp.headers().firstValue("Content-Length").ifPresent(lenStr -> {
                    try {
                        long len = Long.parseLong(lenStr.trim());
                        if (len > policy.maxBytes()) {
                            throw new DocumentParseException(
                                    "URL Content-Length " + len + " exceeds maxBytes " + policy.maxBytes());
                        }
                    } catch (NumberFormatException ignored) {
                        // 非数字 Content-Length 跳过
                    }
                });
                return;  // HEAD 校验通过
            }
            throw new DocumentParseException(
                    "Too many redirects (> " + maxRedirects + ") from: " + uri);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DocumentParseException(
                    "HEAD request failed for: " + uri, e);
        }
    }

    /**
     * DNS binding 校验 — 防线 1 解析出的 IP 与防线 2 启动 HEAD 前
     * 重新解析的 IP 不一致 → 视为 DNS rebinding 攻击, 立即拒绝。
     * 局限: attacker 的窗口仅 ~ms 级缩短, 真正 100% 防护需在 HttpClient
     * 层级绑定 expectedIp 跳过 JDK 的 InetAddress 解析。
     */
    void verifyDnsBinding(String host, InetAddress expectedIp) {
        if (host == null || expectedIp == null) {
            return;
        }
        try {
            InetAddress current = InetAddress.getByName(host);
            checkIpMatches(host, current, expectedIp);
        } catch (java.net.UnknownHostException e) {
            throw new DocumentParseException(
                    "Re-resolution failed for host: " + host, e);
        }
    }

    static void checkIpMatches(String host, InetAddress current, InetAddress expected) {
        if (!expected.equals(current)) {
            throw new DocumentParseException(
                    "DNS rebinding detected for " + host
                            + ": current=" + current.getHostAddress()
                            + " expected=" + expected.getHostAddress());
        }
    }
}

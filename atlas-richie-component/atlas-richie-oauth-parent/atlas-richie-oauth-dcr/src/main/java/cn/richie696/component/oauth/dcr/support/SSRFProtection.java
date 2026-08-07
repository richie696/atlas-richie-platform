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
package cn.richie696.component.oauth.dcr.support;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 客户端元数据外链 URL 的 SSRF(Server-Side Request Forgery)防护器。
 * <p>
 * 对任意待加载 URL 依次校验:协议必须 HTTPS、Host 不能是裸 IP、解析结果不能落在 IPv4/IPv6 内网与
 * 保留地址段、可选白名单域名匹配;DNS 解析结果写入 {@link OAuthCache} 防止反复解析带来的放大探测
 * 与延迟。
 * </p>
 * <p>
 * 处于 oauth-dcr 模块的安全边界位置:由 {@link cn.richie696.component.oauth.dcr.DynamicClientRegistrationEndpoint} 在校验
 * redirect_uri 与 jwks_uri 时调用,亦被 {@link DefaultClientIdMetadataDocumentResolver} 用于
 * 解析外部 metadata 文档前的合法性检查。
 * </p>
 * <p>
 * 解决的问题:阻止恶意客户端在 DCR 阶段通过 redirect_uri / jwks_uri 把请求重定向到内网、链路本地
 * 或云元数据端点,把 SSRF 风险拦截在客户端注册入口,不污染下游授权码、Token 与 JWKS 加载流程。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class SSRFProtection {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6_FULL_PATTERN = Pattern.compile("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");

    private final OAuthCache cache;
    private final List<String> allowedDomains;
    private final Duration cacheTtl;

    public SSRFProtection(
            GlobalCache globalCache,
            List<String> allowedDomains,
            long cacheTtlSeconds
    ) {
        this(new LegacyGlobalCacheOAuthCache(), allowedDomains, cacheTtlSeconds);
    }

    public SSRFProtection(OAuthCache cache, List<String> allowedDomains, long cacheTtlSeconds) {
        this.cache = cache;
        this.allowedDomains = allowedDomains != null ? allowedDomains : List.of();
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    /**
     * 验证 URL 是否安全（防止 SSRF）
     *
     * @param url 待验证的 URL
     * @return 是否安全
     */
    public boolean isUrlSafe(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }

        try {
            URL parsedUrl = new URL(url);

            if (!"https".equalsIgnoreCase(parsedUrl.getProtocol())) {
                log.warn("SSRF 防护：非 HTTPS URL: {}", url);
                return false;
            }

            String host = parsedUrl.getHost().toLowerCase();

            if (isIpAddress(host)) {
                log.warn("SSRF 防护：不允许 IP 地址: {}", url);
                return false;
            }

            if (isReservedAddress(host)) {
                log.warn("SSRF 防护：内网/保留地址: {}", url);
                return false;
            }

            if (!allowedDomains.isEmpty() && !isInAllowList(host)) {
                log.warn("SSRF 防护：域名不在白名单中: {}", url);
                return false;
            }

            String resolvedIp = resolveAndCacheDns(host);
            if (resolvedIp == null) {
                return false;
            }

            if (isReservedAddress(resolvedIp)) {
                log.warn("SSRF 防护：DNS 解析后为内网地址: {}", resolvedIp);
                return false;
            }

            return true;
        } catch (MalformedURLException e) {
            log.warn("SSRF 防护：无效的 URL: {}", url);
            return false;
        }
    }

    private String resolveAndCacheDns(String host) {
        String cacheKey = OAuth2RedisKey.OAUTH2_SSRF_DNS_CACHE.getKey(host);
        String cachedIp = cache.get(cacheKey, String.class);

        if (cachedIp != null) {
            return cachedIp;
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            String ip = address.getHostAddress();
            cache.put(cacheKey, ip, cacheTtl.toMillis());
            return ip;
        } catch (UnknownHostException e) {
            log.warn("SSRF 防护：DNS 解析失败: {}", host);
            return null;
        }
    }

    private boolean isIpAddress(String host) {
        if (IPV4_PATTERN.matcher(host).matches()) {
            return true;
        }
        if (IPV6_FULL_PATTERN.matcher(host).matches()) {
            return true;
        }
        if (host.contains(":")) {
            return true;
        }
        return false;
    }

    private boolean isReservedAddress(String address) {
        if (StringUtils.isBlank(address)) {
            return false;
        }

        try {
            if (address.contains(":") && !address.startsWith("0x")) {
                return isReservedIpv6Address(address);
            }
            return isReservedIpv4Address(address);
        } catch (Exception e) {
            log.warn("SSRF 防护：地址解析异常: {}", address);
            return true;
        }
    }

    private boolean isReservedIpv4Address(String address) {
        String[] parts = address.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            if (first == 10) {
                return true;
            }

            if (first == 172) {
                if (second >= 16 && second <= 31) {
                    return true;
                }
            }

            if (first == 192) {
                if (second == 168) {
                    return true;
                }
            }

            if (first == 127) {
                return true;
            }

            if (first == 169 && second == 254) {
                return true;
            }

            if (first == 0) {
                return true;
            }

            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }

            if (first == 192 && second == 0 && (Integer.parseInt(parts[2]) == 0 || Integer.parseInt(parts[2]) == 2)) {
                return true;
            }

            if (first >= 224) {
                return true;
            }

            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isReservedIpv6Address(String address) {
        String lower = address.toLowerCase();

        if (lower.equals("::1")) {
            return true;
        }

        if (lower.equals("::")) {
            return true;
        }

        if (lower.startsWith("fe80:")) {
            return true;
        }

        if (lower.startsWith("fc00:") || lower.startsWith("fd00:")) {
            return true;
        }

        if (lower.startsWith("2001:db8:")) {
            return true;
        }

        if (lower.startsWith("100::")) {
            return true;
        }

        if (lower.startsWith("2001:10:")) {
            return true;
        }

        if (lower.equals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")) {
            return true;
        }

        return false;
    }

    private boolean isInAllowList(String host) {
        return allowedDomains.stream().anyMatch(domain ->
                host.equals(domain) || host.endsWith("." + domain)
        );
    }
}

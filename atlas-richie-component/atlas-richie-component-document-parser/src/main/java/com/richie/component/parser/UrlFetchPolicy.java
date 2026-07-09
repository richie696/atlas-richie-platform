/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.parser;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

/**
 * URL 拉取策略 — 内建 SSRF / 大小 / 超时防御(三道防线之协议层)。
 * <p>
 * 默认拒绝明文 HTTP 与内网 IP(127.x / 10.x / 172.16-31 / 192.168 / 169.254 / ::1 / fc00::/7),
 * 默认上限 200MB,连接超时 5s,读取超时 60s。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public record UrlFetchPolicy(
        boolean allowHttp,
        boolean allowPrivateIp,
        boolean followRedirects,
        long maxBytes,
        Duration connectTimeout,
        Duration readTimeout,
        List<CidrBlock> allowlist
) {
    public UrlFetchPolicy {
        if (maxBytes <= 0) {
            maxBytes = 200L * 1024 * 1024;
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(60);
        }
        if (allowlist == null) {
            allowlist = List.of();
        }
    }

    /**
     * 默认策略:拒绝 HTTP / 内网 IP,200MB 上限,默认超时。
     */
    public static UrlFetchPolicy defaults() {
        return new UrlFetchPolicy(
                false, false, true,
                200L * 1024 * 1024,
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                List.of()
        );
    }

    /**
     * CIDR 段(显式 opt-in 白名单)。
     * <p>
     * Phase 5 集成 UrlFetcher 时实现完整 CIDR 校验,Phase 1 占位。
     */
    public record CidrBlock(String cidr) {
        public CidrBlock {
            if (cidr == null || cidr.isBlank()) {
                throw new IllegalArgumentException("cidr must not be blank");
            }
        }

        /**
         * 判断给定 IP 是否在该 CIDR 段内。
         * <p>
         * Phase 1 占位实现,Phase 5 接入 UrlFetcher 时实现完整 IPv4/IPv6 CIDR 校验。
         */
        public boolean contains(InetAddress addr) {
            return false;
        }
    }
}

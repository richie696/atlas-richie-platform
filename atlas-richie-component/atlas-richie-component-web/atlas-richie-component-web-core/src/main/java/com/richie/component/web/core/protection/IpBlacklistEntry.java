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
package com.richie.component.web.core.protection;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * AnomalyDetection 子规则：IP 黑名单匹配器（README.md §4.8.2 / §4.8.3）。
 * <p>
 * 支持两种格式：
 * <ul>
 *   <li><strong>单 IP</strong>：{@code 192.0.2.10}</li>
 *   <li><strong>CIDR</strong>：{@code 192.0.2.0/24}（IPv4）或 {@code 2001:db8::/32}（IPv6）</li>
 * </ul>
 * 解析失败抛 {@link IllegalArgumentException}（启动时 fail-fast）。
 *
 * @author richie696
 * @since 2026-07
 */
public final class IpBlacklistEntry {

    private final String source;
    private final boolean ipv4;
    private final int prefixBits;
    private final BigInteger network;

    public IpBlacklistEntry(String source) {
        this.source = Objects.requireNonNull(source, "source must not be null");

        String ipPart;
        int pb;
        int slash = source.indexOf('/');
        if (slash < 0) {
            ipPart = source;
            pb = -1;
        } else {
            ipPart = source.substring(0, slash);
            try {
                pb = Integer.parseInt(source.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix: " + source, e);
            }
            if (pb < 0) {
                throw new IllegalArgumentException("CIDR prefix must be non-negative: " + source);
            }
        }

        byte[] bytes;
        try {
            bytes = InetAddress.getByName(ipPart).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address: " + source, e);
        }
        this.ipv4 = bytes.length == 4;

        if (pb >= 0) {
            int max = ipv4 ? 32 : 128;
            if (pb > max) {
                throw new IllegalArgumentException(
                        "CIDR prefix /" + pb + " out of range for " + (ipv4 ? "IPv4" : "IPv6"));
            }
            this.prefixBits = pb;
            this.network = bytesToBigInteger(applyMask(bytes, pb));
        } else {
            this.prefixBits = -1;
            this.network = bytesToBigInteger(bytes);
        }
    }

    public String source() {
        return source;
    }

    public boolean matches(InetAddress candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        byte[] bytes = candidate.getAddress();
        if (bytes.length != (ipv4 ? 4 : 16)) {
            return false;
        }
        if (prefixBits < 0) {
            return network.equals(bytesToBigInteger(bytes));
        }
        return network.equals(bytesToBigInteger(applyMask(bytes, prefixBits)));
    }

    public boolean matches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        int comma = candidate.indexOf(',');
        String first = comma >= 0 ? candidate.substring(0, comma).trim() : candidate.trim();
        try {
            return matches(InetAddress.getByName(first));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static byte[] applyMask(byte[] bytes, int prefix) {
        byte[] masked = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            int cur;
            if (prefix >= (i + 1) * 8) {
                cur = 0xFF;
            } else if (prefix <= i * 8) {
                cur = 0x00;
            } else {
                cur = (0xFF << ((i + 1) * 8 - prefix)) & 0xFF;
            }
            masked[i] = (byte) (bytes[i] & cur);
        }
        return masked;
    }

    private static BigInteger bytesToBigInteger(byte[] bytes) {
        return new BigInteger(1, bytes);
    }
}
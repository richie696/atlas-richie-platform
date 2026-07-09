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
package com.richie.component.web.core.protection;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpBlacklistEntryTest {

    @Test
    void singleIp_matchesExact() throws Exception {
        IpBlacklistEntry entry = new IpBlacklistEntry("192.0.2.1");
        assertThat(entry.matches(InetAddress.getByName("192.0.2.1"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("192.0.2.2"))).isFalse();
    }

    @Test
    void ipv4Cidr_matchesRange() throws Exception {
        IpBlacklistEntry entry = new IpBlacklistEntry("192.0.2.0/24");
        assertThat(entry.matches(InetAddress.getByName("192.0.2.0"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("192.0.2.1"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("192.0.2.255"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("192.0.3.0"))).isFalse();
    }

    @Test
    void ipv6Single_matches() throws Exception {
        IpBlacklistEntry entry = new IpBlacklistEntry("2001:db8::1");
        assertThat(entry.matches(InetAddress.getByName("2001:db8::1"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("2001:db8::2"))).isFalse();
    }

    @Test
    void ipv6Cidr_matchesRange() throws Exception {
        IpBlacklistEntry entry = new IpBlacklistEntry("2001:db8::/32");
        assertThat(entry.matches(InetAddress.getByName("2001:db8::1"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("2001:db8:ffff::1"))).isTrue();
        assertThat(entry.matches(InetAddress.getByName("2001:db9::1"))).isFalse();
    }

    @Test
    void stringOverload_resolvesAndMatches() {
        IpBlacklistEntry entry = new IpBlacklistEntry("192.0.2.0/24");
        assertThat(entry.matches("192.0.2.1")).isTrue();
        assertThat(entry.matches("192.0.3.1")).isFalse();
        assertThat(entry.matches("not-an-ip")).isFalse();
    }

    @Test
    void ipv4Family_vs_ipv6Family_neverMatch() throws Exception {
        IpBlacklistEntry v4 = new IpBlacklistEntry("192.0.2.0/24");
        assertThat(v4.matches(InetAddress.getByName("2001:db8::1"))).isFalse();
        IpBlacklistEntry v6 = new IpBlacklistEntry("2001:db8::/32");
        assertThat(v6.matches(InetAddress.getByName("192.0.2.1"))).isFalse();
    }

    @Test
    void source_returnsOriginal() {
        IpBlacklistEntry entry = new IpBlacklistEntry("10.0.0.0/8");
        assertThat(entry.source()).isEqualTo("10.0.0.0/8");
    }

    @Test
    void invalidFormat_throws() {
        assertThatThrownBy(() -> new IpBlacklistEntry("not-an-ip"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IpBlacklistEntry("192.0.2.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IpBlacklistEntry("192.0.2.0/-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hostBitsIgnored_returnsTrue() throws Exception {
        IpBlacklistEntry entry = new IpBlacklistEntry("192.0.2.0/24");
        assertThat(entry.matches(InetAddress.getByName("192.0.2.99"))).isTrue();
    }
}
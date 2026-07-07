package com.richie.component.web.core.protection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NonceCacheTest {

    @Test
    void putIfAbsent_firstTime_returnsTrue() {
        NonceCache c = new NonceCache(60);
        assertThat(c.putIfAbsent("n1")).isTrue();
    }

    @Test
    void putIfAbsent_secondTime_returnsFalse() {
        NonceCache c = new NonceCache(60);
        c.putIfAbsent("n1");
        assertThat(c.putIfAbsent("n1")).isFalse();
    }

    @Test
    void contains_returnsTrueForKnownNonce() {
        NonceCache c = new NonceCache(60);
        c.putIfAbsent("n1");
        assertThat(c.contains("n1")).isTrue();
        assertThat(c.contains("n2")).isFalse();
    }

    @Test
    void contains_returnsFalseForExpiredNonce() throws Exception {
        NonceCache c = new NonceCache(1);
        c.putIfAbsent("n1");
        Thread.sleep(1100);
        assertThat(c.contains("n1")).isFalse();
        assertThat(c.putIfAbsent("n1")).isTrue();
    }

    @Test
    void putIfAbsent_afterExpiry_returnsTrue() throws Exception {
        NonceCache c = new NonceCache(1);
        c.putIfAbsent("n1");
        Thread.sleep(1100);
        assertThat(c.putIfAbsent("n1")).isTrue();
    }

    @Test
    void size_tracksActiveEntries() {
        NonceCache c = new NonceCache(60);
        assertThat(c.size()).isZero();
        c.putIfAbsent("n1");
        c.putIfAbsent("n2");
        c.putIfAbsent("n3");
        assertThat(c.size()).isEqualTo(3);
    }

    @Test
    void size_doesNotIncludeExpiredEntries() throws Exception {
        NonceCache c = new NonceCache(1);
        c.putIfAbsent("n1");
        assertThat(c.size()).isEqualTo(1);
        Thread.sleep(1100);
        assertThat(c.size()).isZero();
    }

    @Test
    void constructor_rejectsNonPositiveTtl() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new NonceCache(0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new NonceCache(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullNonce_throwsOrReturnsFalse() {
        NonceCache c = new NonceCache(60);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.putIfAbsent(null))
                .isInstanceOf(NullPointerException.class);
    }
}
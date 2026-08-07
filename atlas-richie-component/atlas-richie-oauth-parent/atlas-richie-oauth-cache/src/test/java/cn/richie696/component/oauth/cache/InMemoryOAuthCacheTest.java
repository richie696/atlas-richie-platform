package cn.richie696.component.oauth.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOAuthCacheTest {

    @Test
    void supportsTtlAndAtomicCounters() throws InterruptedException {
        InMemoryOAuthCache cache = new InMemoryOAuthCache();
        cache.put("token", "value", 50);
        assertThat(cache.get("token", String.class)).isEqualTo("value");
        Thread.sleep(70);
        assertThat(cache.get("token", String.class)).isNull();

        assertThat(cache.increment("count", 1, 1_000)).isEqualTo(1);
        assertThat(cache.increment("count", 2, 1_000)).isEqualTo(3);
    }

    @Test
    void lockIsExclusive() {
        InMemoryOAuthCache cache = new InMemoryOAuthCache();
        OAuthLock first = cache.tryLock("lock", 5);
        OAuthLock second = cache.tryLock("lock", 5);
        assertThat(first.acquired()).isTrue();
        assertThat(second.acquired()).isFalse();
        second.close();
        first.close();
        assertThat(cache.tryLock("lock", 5).acquired()).isTrue();
    }
}

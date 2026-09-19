package cn.richie696.component.oauth.cache;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.ops.FieldOps;
import cn.richie696.component.cache.ops.KeyOps;
import cn.richie696.component.cache.ops.LockOps;
import cn.richie696.component.cache.ops.StructOps;
import cn.richie696.component.cache.ops.ValueOps;
import cn.richie696.component.cache.redis.manage.CacheLock;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalCacheOAuthCacheTest {

    @Test
    void mapsStandardValuesToValueOpsAndObjectsToStructOps() {
        ValueOps values = mock(ValueOps.class);
        StructOps structs = mock(StructOps.class);
        KeyOps keys = mock(KeyOps.class);
        when(values.get("text", String.class)).thenReturn("cached");
        when(structs.get("object", Sample.class)).thenReturn(new Sample("cached"));

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(values);
            cache.when(GlobalCache::struct).thenReturn(structs);
            cache.when(GlobalCache::key).thenReturn(keys);

            GlobalCacheOAuthCache adapter = new GlobalCacheOAuthCache();
            assertThat(adapter.get("text", String.class)).isEqualTo("cached");
            assertThat(adapter.get("object", Sample.class).value()).isEqualTo("cached");

            adapter.put("text", "one", 10);
            adapter.put("int", 1, 10);
            adapter.put("long", 2L, 10);
            adapter.put("bool", true, 10);
            adapter.put("object", new Sample("value"), 10);
            adapter.put("null", null, 10);
            adapter.remove("text");
            when(keys.hasKey("text")).thenReturn(true);
            assertThat(adapter.exists("text")).isTrue();
        }

        verify(values).set("text", "one", 10);
        verify(values).set("int", 1, 10);
        verify(values).set("long", 2L, 10);
        verify(values).set("bool", true, 10);
        verify(structs).set("object", new Sample("value"), 10);
        verify(structs).set("null", null, 10);
        verify(keys).removeCache("text");
    }

    @Test
    void delegatesCountersAbsentWritesAndLocks() {
        ValueOps values = mock(ValueOps.class);
        LockOps locks = mock(LockOps.class);
        CacheLock cacheLock = mock(CacheLock.class);
        when(values.setIfAbsent("token", "v", 100)).thenReturn(true);
        when(values.increment("count", 2, 100)).thenReturn(3L);
        when(cacheLock.isSuccess()).thenReturn(true);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(values);
            cache.when(GlobalCache::lock).thenReturn(locks);
            when(locks.optimisticWithRenewal("lock", 5)).thenReturn(cacheLock);

            GlobalCacheOAuthCache adapter = new GlobalCacheOAuthCache();
            assertThat(adapter.putIfAbsent("token", "v", 100)).isTrue();
            assertThat(adapter.increment("count", 2, 100)).isEqualTo(3L);
            OAuthLock lock = adapter.tryLock("lock", 5);
            assertThat(lock.acquired()).isTrue();
            lock.close();
        }

        verify(cacheLock).close();
    }

    @Test
    void legacyAdapterPreservesFieldAndValueSemantics() {
        ValueOps values = mock(ValueOps.class);
        StructOps structs = mock(StructOps.class);
        FieldOps fields = mock(FieldOps.class);
        KeyOps keys = mock(KeyOps.class);
        LockOps locks = mock(LockOps.class);
        CacheLock cacheLock = mock(CacheLock.class);
        when(fields.getAll("hash", String.class)).thenReturn(Map.of("field", "value"));
        when(fields.get("hash", "field", String.class)).thenReturn("value");
        when(values.get("text", String.class)).thenReturn("cached");
        when(values.setIfAbsent("token", "v", 100)).thenReturn(true);
        when(values.increment("count", 2)).thenReturn(4L);
        when(keys.hasKey("text")).thenReturn(true);
        when(cacheLock.isSuccess()).thenReturn(false);

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(values);
            cache.when(GlobalCache::struct).thenReturn(structs);
            cache.when(GlobalCache::field).thenReturn(fields);
            cache.when(GlobalCache::key).thenReturn(keys);
            cache.when(GlobalCache::lock).thenReturn(locks);
            when(locks.optimisticWithRenewal("lock", 5)).thenReturn(cacheLock);

            LegacyGlobalCacheOAuthCache adapter = new LegacyGlobalCacheOAuthCache();
            assertThat(adapter.get("hash", Map.class)).containsEntry("field", "value");
            assertThat(adapter.get("text", String.class)).isEqualTo("cached");
            assertThat(adapter.getField("hash", "field", String.class)).isEqualTo("value");
            adapter.put("text", "value", 10);
            adapter.put("object", new Sample("value"), 10);
            assertThat(adapter.putIfAbsent("token", "v", 100)).isTrue();
            assertThat(adapter.increment("count", 2, 100)).isEqualTo(4L);
            adapter.remove("text");
            assertThat(adapter.exists("text")).isTrue();
            assertThat(adapter.tryLock("lock", 5).acquired()).isFalse();
        }

        verify(values).set("text", "value", 10);
        verify(structs).set("object", new Sample("value"), 10);
        verify(keys).removeCache("text");
    }

    private record Sample(String value) {
    }
}

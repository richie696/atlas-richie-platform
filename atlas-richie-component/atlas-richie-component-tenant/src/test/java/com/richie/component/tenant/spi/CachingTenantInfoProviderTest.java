package com.richie.component.tenant.spi;

import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CachingTenantInfoProvider 装饰器单测")
class CachingTenantInfoProviderTest {

    private TenantInfoProvider delegate;
    private CachingTenantInfoProvider cached;

    @BeforeEach
    void setUp() {
        delegate = mock(TenantInfoProvider.class);
    }

    @AfterEach
    void tearDown() {
        if (cached != null) {
            cached.shutdown();
        }
    }

    private static TenantInfo tenant(long id) {
        return new TenantInfo()
            .setTenantId(id)
            .setMode(IsolationMode.COLUMN)
            .setStatus(TenantStatus.ACTIVE);
    }

    @Nested
    @DisplayName("命中与穿透")
    class HitAndMiss {

        @Test
        @DisplayName("首次查询穿透 delegate 并写入缓存")
        void firstCallDelegatesAndCaches() {
            TenantInfo info = tenant(100L);
            when(delegate.getTenantInfo(100L)).thenReturn(info);

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);

            assertThat(cached.getTenantInfo(100L)).isSameAs(info);
            assertThat(cached.size()).isEqualTo(1);
            verify(delegate, times(1)).getTenantInfo(100L);
        }

        @Test
        @DisplayName("第二次查询命中缓存不再穿透 delegate")
        void secondCallHitsCache() {
            TenantInfo info = tenant(100L);
            when(delegate.getTenantInfo(100L)).thenReturn(info);

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.getTenantInfo(100L);
            cached.getTenantInfo(100L);
            cached.getTenantInfo(100L);

            verify(delegate, times(1)).getTenantInfo(100L);
        }

        @Test
        @DisplayName("不同 tenantId 互不影响,各自独立缓存")
        void differentTenantsCacheIndependently() {
            when(delegate.getTenantInfo(100L)).thenReturn(tenant(100L));
            when(delegate.getTenantInfo(200L)).thenReturn(tenant(200L));

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.getTenantInfo(100L);
            cached.getTenantInfo(200L);
            cached.getTenantInfo(100L);

            verify(delegate, times(1)).getTenantInfo(100L);
            verify(delegate, times(1)).getTenantInfo(200L);
        }

        @Test
        @DisplayName("null tenantId 始终穿透 delegate 不缓存")
        void nullTenantIdAlwaysDelegates() {
            cached = new CachingTenantInfoProvider(delegate, 60, 1000);

            cached.getTenantInfo(null);
            cached.getTenantInfo(null);

            verify(delegate, times(2)).getTenantInfo(null);
            assertThat(cached.size()).isZero();
        }

        @Test
        @DisplayName("TTL=0 时禁用缓存,每次都穿透")
        void zeroTtlDisablesCache() {
            TenantInfo info = tenant(100L);
            when(delegate.getTenantInfo(100L)).thenReturn(info);

            cached = new CachingTenantInfoProvider(delegate, 0, 1000);
            cached.getTenantInfo(100L);
            cached.getTenantInfo(100L);

            verify(delegate, times(2)).getTenantInfo(100L);
            assertThat(cached.size()).isZero();
        }
    }

    @Nested
    @DisplayName("null 行为与 exists 穿透")
    class NegativeAndExists {

        @Test
        @DisplayName("delegate 返回 null 时不缓存(避免恶意刷不存在租户)")
        void nullResultNotCached() {
            when(delegate.getTenantInfo(404L)).thenReturn(null);

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            assertThat(cached.getTenantInfo(404L)).isNull();
            assertThat(cached.getTenantInfo(404L)).isNull();

            verify(delegate, times(2)).getTenantInfo(404L);
            assertThat(cached.size()).isZero();
        }

        @Test
        @DisplayName("exists() 永远穿透,不缓存")
        void existsAlwaysDelegates() {
            AtomicInteger counter = new AtomicInteger();
            Mockito.when(delegate.exists(100L)).thenAnswer(inv -> {
                counter.incrementAndGet();
                return true;
            });

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.exists(100L);
            cached.exists(100L);
            cached.exists(100L);

            assertThat(counter.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("失效 API")
    class Invalidate {

        @Test
        @DisplayName("invalidate(Long) 失效单个租户")
        void invalidateSingleTenant() {
            TenantInfo info = tenant(100L);
            when(delegate.getTenantInfo(100L)).thenReturn(info);

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.getTenantInfo(100L);
            assertThat(cached.size()).isEqualTo(1);

            cached.invalidate(100L);
            assertThat(cached.size()).isZero();

            cached.getTenantInfo(100L);
            verify(delegate, times(2)).getTenantInfo(100L);
        }

        @Test
        @DisplayName("invalidate(null) 不抛异常")
        void invalidateNullIsNoop() {
            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.invalidate(null);
            assertThat(cached.size()).isZero();
        }

        @Test
        @DisplayName("invalidateAll 清空全部")
        void invalidateAllClearsEverything() {
            when(delegate.getTenantInfo(Mockito.anyLong())).thenAnswer(inv -> tenant(inv.getArgument(0)));

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.getTenantInfo(1L);
            cached.getTenantInfo(2L);
            cached.getTenantInfo(3L);
            assertThat(cached.size()).isEqualTo(3);

            cached.invalidateAll();
            assertThat(cached.size()).isZero();
        }
    }

    @Nested
    @DisplayName("构造与生命周期")
    class Lifecycle {

        @Test
        @DisplayName("delegate 为 null 抛 NullPointerException")
        void nullDelegateRejected() {
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new CachingTenantInfoProvider(null, 60, 1000));
        }

        @Test
        @DisplayName("shutdown() 幂等可重复调用")
        void shutdownIdempotent() {
            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.shutdown();
            cached.shutdown();
            cached.shutdown();
        }

        @Test
        @DisplayName("负 maxSize 视为不限容量")
        void negativeMaxSizeAllowsUnlimited() {
            when(delegate.getTenantInfo(Mockito.anyLong())).thenAnswer(inv -> tenant(inv.getArgument(0)));

            cached = new CachingTenantInfoProvider(delegate, 60, -1);
            for (long i = 0; i < 50; i++) {
                cached.getTenantInfo(i);
            }
            assertThat(cached.size()).isEqualTo(50);
        }

        @Test
        @DisplayName("超过 maxSize 触发容量回收,delegate 不被多次调用")
        void exceedingMaxSizeTrimsCache() {
            when(delegate.getTenantInfo(Mockito.anyLong())).thenAnswer(inv -> tenant(inv.getArgument(0)));

            cached = new CachingTenantInfoProvider(delegate, 60, 10);
            for (long i = 0; i < 30; i++) {
                cached.getTenantInfo(i);
            }
            assertThat(cached.size()).isLessThanOrEqualTo(10);
            verify(delegate, times(30)).getTenantInfo(Mockito.anyLong());
        }

        @Test
        @DisplayName("hit 后 delegate 不会被再次调用")
        void hitDoesNotCallDelegate() {
            when(delegate.getTenantInfo(100L)).thenReturn(tenant(100L));

            cached = new CachingTenantInfoProvider(delegate, 60, 1000);
            cached.getTenantInfo(100L);
            cached.getTenantInfo(100L);

            verify(delegate, times(1)).getTenantInfo(100L);
            verify(delegate, never()).exists(Mockito.anyLong());
        }
    }
}

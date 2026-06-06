package com.richie.component.i18n.aspect;

import com.richie.component.i18n.annotation.I18nDict;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.i18n.handle.I18nHandle;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.contract.model.ApiResult;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class I18nDictAspectTest {

    @Test
    void doAround_injectsDictForApiResult() throws Throwable {
        I18nProperties properties = new I18nProperties();
        I18nDictAspect aspect = new I18nDictAspect(properties);
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(ApiResult.success(new DictVo("dict.key")));

        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of("en", "Label"));

            ApiResult<?> result = (ApiResult<?>) aspect.doAround(joinPoint);

            assertThat(result.getI18nDict()).containsKey("dict.key");
        }
    }

    static class DictVo {
        @I18nDict
        private final String label;

        DictVo(String label) {
            this.label = label;
        }
    }
}

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
package com.richie.component.i18n.aspect;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.richie.component.i18n.annotation.I18nControl;
import com.richie.component.i18n.annotation.I18nDict;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.contract.model.ApiResult;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class I18nDictAspectExtendedTest {

    @Test
    void doAround_returnsNonApiResultAsIs() throws Throwable {
        I18nDictAspect aspect = new I18nDictAspect(new I18nProperties());
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("plain");

        assertThat(aspect.doAround(joinPoint)).isEqualTo("plain");
    }

    @Test
    void doAround_skipsWhenApiResultNotSuccessful() throws Throwable {
        I18nDictAspect aspect = new I18nDictAspect(new I18nProperties());
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(ApiResult.error("error"));

        ApiResult<?> result = (ApiResult<?>) aspect.doAround(joinPoint);

        assertThat(result.getI18nDict()).isNull();
    }

    @Test
    void doAround_skipsWhenI18nControlRequiredButMissing() throws Throwable {
        I18nProperties properties = new I18nProperties();
        properties.setEnableI18nControl(true);
        I18nDictAspect aspect = new I18nDictAspect(properties);
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.proceed()).thenReturn(ApiResult.success("ok"));

        Object result = aspect.doAround(joinPoint);

        assertThat(result).isInstanceOf(ApiResult.class);
        assertThat(((ApiResult<?>) result).getData()).isEqualTo("ok");
        assertThat(((ApiResult<?>) result).getI18nDict()).isNull();
    }

    @Test
    void doAround_proceedsWhenControllerHasI18nControl() throws Throwable {
        I18nProperties properties = new I18nProperties();
        properties.setEnableI18nControl(true);
        I18nDictAspect aspect = new I18nDictAspect(properties);
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.getTarget()).thenReturn(new ControlledController());
        when(joinPoint.proceed()).thenReturn(ApiResult.success(new DictVo("key.one")));

        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of("zh", "值"));

            ApiResult<?> result = (ApiResult<?>) aspect.doAround(joinPoint);

            assertThat(result.getI18nDict()).containsKey("key.one");
        }
    }

    @Test
    void doAround_skipsWhenDataIsNull() throws Throwable {
        I18nDictAspect aspect = new I18nDictAspect(new I18nProperties());
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(ApiResult.success(null));

        ApiResult<?> result = (ApiResult<?>) aspect.doAround(joinPoint);

        assertThat(result.getI18nDict()).isNull();
    }

    @Test
    void doAround_skipsBlankDictKey() throws Throwable {
        I18nDictAspect aspect = new I18nDictAspect(new I18nProperties());
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(ApiResult.success(new DictVo(" ")));

        ApiResult<?> result = (ApiResult<?>) aspect.doAround(joinPoint);

        assertThat(result.getI18nDict()).isEmpty();
    }

    @Test
    void doAround_injectsDictForPagedApiResult() throws Throwable {
        I18nDictAspect aspect = new I18nDictAspect(new I18nProperties());
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        Page<DictVo> page = new Page<>();
        page.setRecords(java.util.List.of(new DictVo("page.key")));
        when(joinPoint.proceed()).thenReturn(ApiResult.success(page));

        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of("zh", "分页"));

            ApiResult<?> result = (ApiResult<?>) aspect.doAround(joinPoint);

            assertThat(result.getI18nDict()).containsKey("page.key");
        }
    }

    @I18nControl
    static class ControlledController {
    }

    static class DictVo {
        @I18nDict
        private final String label;

        DictVo(String label) {
            this.label = label;
        }
    }
}

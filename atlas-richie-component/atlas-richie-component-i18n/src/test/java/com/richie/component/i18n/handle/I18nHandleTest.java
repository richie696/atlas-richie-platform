package com.richie.component.i18n.handle;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.component.cache.ops.KeyOps;
import com.richie.contract.constant.GlobalConstants;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class I18nHandleTest {

    @Test
    void addAndGetI18nDictionary() {
        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("en"), eq(String.class))).thenReturn("Hello");
            when(fieldOps.getAll(anyString(), eq(String.class)))
                    .thenReturn(Map.of("en", "Hello"));

            I18nHandle.addI18nDictionary("greeting", Map.of("en", "Hello"));

            assertThat(I18nHandle.getI18nDictionary("greeting", "en")).isEqualTo("Hello");
            assertThat(I18nHandle.getI18nDictionaries("greeting")).containsEntry("en", "Hello");
            verify(fieldOps).set(GlobalConstants.I18N_CACHE_KEY + "greeting", "en", "Hello");
        }
    }

    @Test
    void getI18nDictionary_returnsKeyWhenMissing() {
        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), anyString(), eq(String.class))).thenReturn(null);

            assertThat(I18nHandle.getI18nDictionary("missing", "en")).isEqualTo("missing");
        }
    }

    @Test
    void addI18nDictionaries_writesAllEntries() {
        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);

            I18nHandle.addI18nDictionaries(Map.of(
                    "greeting", Map.of("en", "Hello"),
                    "farewell", Map.of("en", "Bye")));

            verify(fieldOps).setAll(
                    GlobalConstants.I18N_CACHE_KEY + "greeting", Map.of("en", "Hello"), 604800000L);
            verify(fieldOps).setAll(
                    GlobalConstants.I18N_CACHE_KEY + "farewell", Map.of("en", "Bye"), 604800000L);
        }
    }

    @Test
    void getI18nDictionaries_returnsEmptyMapWhenCacheNull() {
        FieldOps fieldOps = mock(FieldOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(null);

            assertThat(I18nHandle.getI18nDictionaries("any")).isEmpty();
        }
    }

    @Test
    void deleteI18nDictionary_removesCacheKey() {
        KeyOps keyOps = mock(KeyOps.class);
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::key).thenReturn(keyOps);

            I18nHandle.deleteI18nDictionary("dict");

            verify(keyOps).removeCache(GlobalConstants.I18N_CACHE_KEY + "dict");
        }
    }
}

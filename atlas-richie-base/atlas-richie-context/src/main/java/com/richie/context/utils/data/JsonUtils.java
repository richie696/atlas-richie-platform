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
package com.richie.context.utils.data;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.spring.CommonUtils;
import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.util.StdDateFormat;
import tools.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具类：JSON转换/序列化工具类
 *
 * @author richie696
 * @version 1.4
 * @since 2023-10-18
 */
@Slf4j
@SuppressWarnings({"unused"})
public class JsonUtils {

    private ObjectMapper MAPPER_LOWER_CAMEL_CASE;
    private ObjectMapper MAPPER_SNAKE_CASE;
    private static final ConcurrentMap<String, JsonUtils> CUSTOM_MAPPER = Maps.newConcurrentMap();
    private static final CopyOnWriteArrayList<JacksonModule> GLOBAL_MODULES = new CopyOnWriteArrayList<>();

    private static class Bean {
        static final JsonUtils INSTANCE = new JsonUtils();
    }

    /**
     * JSON配置类
     *
     * @author richie696
     * @version 1.0
     * @see SerializationFeature
     * @see DeserializationFeature
     * @see MapperFeature
     * @since 2024-12-30 17:04:14
     */
    @Data
    @Builder
    public static class JsonConfiguration {

        /**
         * 是否按字母顺序排序属性
         */
        private Boolean sortPropertiesAlphabetically;

        /**
         * 是否对Map的键进行排序（字母升序，）
         */
        private Boolean orderMapEntriesByKeys;

        /**
         * 是否在序列化时忽略空的JavaBean（null）
         */
        private Boolean failOnEmptyBeans;

        /**
         * 是否将自引用写为null
         */
        private Boolean writeSelfReferencesAsNull;

        /**
         * 是否序列化 ZoneId
         */
        private Boolean writeDatesWithZoneId;

        /**
         * 是否在反序列化时忽略被忽略的属性
         */
        private Boolean failOnIgnoredProperties;

        /**
         * 是否在反序列化时忽略未知属性
         */
        private Boolean failOnUnknownProperties;

        /**
         * 是否允许未引用的字段名
         */
        private Boolean allowUnquotedFieldNames;

        /**
         * 是否允许单引号
         */
        private Boolean allowSingleQuotes;

        /**
         * 是否允许数字前导零
         */
        private Boolean allowLeadingZerosForNumbers;

        /**
         * 是否允许未转义的控制字符
         */
        private Boolean allowUnescapedControlChars;

        /**
         * 是否序列化空值
         */
        private Boolean allowEmptyValue;

        /**
         * 默认日期格式
         */
        private String dateFormat;

        /**
         * 默认时区
         */
        private TimeZone defaultTimeZone;

        /**
         * 默认区域
         */
        private Locale defaultLocale;

    }

    /**
     * 获取+8时区的默认的JsonUtils对象
     * <p>默认日期格式："yyyy-MM-dd HH:mm:ss"
     *
     * @return 返回默认的JsonUtils对象
     */
    public static JsonUtils getInstance() {
        String format = HeaderContextHolder.getHeader(GlobalConstants.X_TIME_FORMAT_PATTERN);
        if (format != null) {
            try {
                return getInstance(format);
            } catch (Exception e) {
                log.error("日期格式转换失败，使用默认日期格式。(format={}, stack={})", format, e.getMessage());
            }
        }
        return Bean.INSTANCE;
    }

    /**
     * 获取自定义日期格式的JsonUtils对象
     *
     * @param dateFormat 日期格式（例：yyyy-MM-dd HH:mm:ss）
     * @return 返回自定义日期格式的JsonUtils对象
     */
    public static JsonUtils getInstance(String dateFormat) {
        if (StringUtils.isBlank(dateFormat)) {
            return Bean.INSTANCE;
        }
        if (CUSTOM_MAPPER.containsKey(dateFormat)) {
            return CUSTOM_MAPPER.get(dateFormat);
        }
        var sdf = new SimpleDateFormat(dateFormat);
        var objectMapper = Bean.INSTANCE.cloneMapper()
                .defaultDateFormat(sdf)
                .build();
        var customUtils = Bean.INSTANCE.custom(objectMapper);
        CUSTOM_MAPPER.put(dateFormat, customUtils);
        return customUtils;
    }

    /**
     * 注册全局 Jackson Module（用于 JsonUtils 默认与定制 Mapper）。
     * <p>
     * 该方法可被自动配置调用；重复类型的 Module 会被忽略。
     *
     * @param modules 需要注册的模块列表
     */
    public static synchronized void registerGlobalModules(List<? extends JacksonModule> modules) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (JacksonModule module : modules) {
            if (module == null) {
                continue;
            }
            boolean exists = GLOBAL_MODULES.stream()
                    .anyMatch(registered -> registered.getClass() == module.getClass());
            if (!exists) {
                GLOBAL_MODULES.add(module);
                changed = true;
            }
        }
        if (changed) {
            Bean.INSTANCE.rebuildDefaultMappers();
            CUSTOM_MAPPER.clear();
        }
    }

    private JsonUtils() {
        rebuildDefaultMappers();
    }

    private JsonUtils(ObjectMapper mapper) {
        // Jackson 3.0: ObjectMapper 不可变，需要使用 rebuild() 重新构建
        MAPPER_LOWER_CAMEL_CASE = mapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build();
        MAPPER_SNAKE_CASE = MAPPER_LOWER_CAMEL_CASE.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }

    private void rebuildDefaultMappers() {
        JsonConfiguration defaultConfiguration = defaultConfiguration();
        MAPPER_LOWER_CAMEL_CASE = rebuildMapper(defaultConfiguration);
        // Jackson 3.0: ObjectMapper 不可变，需要使用 rebuild() 重新构建
        MAPPER_SNAKE_CASE = MAPPER_LOWER_CAMEL_CASE.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }

    private JsonConfiguration defaultConfiguration() {
        return JsonConfiguration.builder()
                .sortPropertiesAlphabetically(true)
                .orderMapEntriesByKeys(true)
                .failOnEmptyBeans(false)
                .writeSelfReferencesAsNull(true)
                .writeDatesWithZoneId(true)
                .failOnIgnoredProperties(false)
                .failOnUnknownProperties(false)
                .allowUnquotedFieldNames(true)
                .allowSingleQuotes(true)
                .allowLeadingZerosForNumbers(true)
                .allowUnescapedControlChars(true)
                .dateFormat(StdDateFormat.DATE_FORMAT_STR_ISO8601)
                .defaultTimeZone(LocaleContextHolder.getTimeZone())
                .defaultLocale(LocaleContextHolder.getLocale())
                .build();
    }

    private JsonMapper rebuildMapper(JsonConfiguration config) {
        JsonMapper.Builder jsonBuilder = JsonMapper.builder();
        if (config.sortPropertiesAlphabetically != null) {
            jsonBuilder.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, config.sortPropertiesAlphabetically);
        }
        if (config.orderMapEntriesByKeys != null) {
            jsonBuilder.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, config.orderMapEntriesByKeys);
        }
        if (config.failOnEmptyBeans != null) {
            jsonBuilder.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, config.failOnEmptyBeans);
        }
        if (config.writeSelfReferencesAsNull != null) {
            jsonBuilder.configure(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL, config.writeSelfReferencesAsNull);
        }
        // Jackson 3.0: WRITE_DATES_WITH_ZONE_ID 已被移除，通过自定义序列化器实现
        // 创建 JavaTimeModule，并根据配置决定是否包含 ZoneId
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        if (config.writeDatesWithZoneId != null && config.writeDatesWithZoneId) {
            SimpleModule zoneIdModule = getZoneIdModule();
            jsonBuilder.addModule(zoneIdModule);
        }
        if (config.failOnIgnoredProperties != null) {
            jsonBuilder.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, config.failOnIgnoredProperties);
        }
        if (config.failOnUnknownProperties != null) {
            jsonBuilder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, config.failOnUnknownProperties);
            jsonBuilder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
        if (config.allowUnquotedFieldNames != null) {
            jsonBuilder.configure(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, config.allowUnquotedFieldNames);
        }
        if (config.allowSingleQuotes != null) {
            jsonBuilder.configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, config.allowSingleQuotes);
        }
        if (config.allowLeadingZerosForNumbers != null) {
            jsonBuilder.configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS, config.allowLeadingZerosForNumbers);
        }
        if (config.allowUnescapedControlChars != null) {
            jsonBuilder.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, config.allowUnescapedControlChars);
        }

        // 强制：null 字段不参与序列化（不可被任何配置覆盖，与 ApiResult 行为对齐）
        jsonBuilder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL));

        if (config.dateFormat != null) {
            jsonBuilder.defaultDateFormat(new SimpleDateFormat(config.dateFormat));
        }
        if (config.defaultTimeZone != null) {
            jsonBuilder.defaultTimeZone(LocaleContextHolder.getTimeZone());
        }
        if (config.defaultLocale != null) {
            jsonBuilder.defaultLocale(LocaleContextHolder.getLocale());
        }

        if (config.allowEmptyValue != null) {
            jsonBuilder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY));
        }

        for (JacksonModule module : GLOBAL_MODULES) {
            jsonBuilder.addModule(module);
        }

        return jsonBuilder.addModule(javaTimeModule).build();
    }

    private static @Nonnull SimpleModule getZoneIdModule() {
        // 启用写入 ZoneId：使用包含 ZoneId 的 ISO8601 格式
        // ZonedDateTime: "2025-12-11T03:50:20.123456789+08:00[Asia/Shanghai]"
        // OffsetDateTime: "2025-12-11T03:50:20.123456789+08:00"
        // 创建自定义模块来覆盖默认序列化器
        SimpleModule zoneIdModule = new SimpleModule("ZoneIdModule");

        // ZonedDateTime 序列化器：包含 ZoneId（格式：ISO_ZONED_DATE_TIME）
        zoneIdModule.addSerializer(ZonedDateTime.class, new StdSerializer<ZonedDateTime>(ZonedDateTime.class) {
            @Override
            public void serialize(ZonedDateTime value, JsonGenerator gen, SerializationContext ctx) {
                gen.writeString(value.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
            }
        });

        // OffsetDateTime 序列化器：包含 Offset（格式：ISO_OFFSET_DATE_TIME）
        zoneIdModule.addSerializer(OffsetDateTime.class, new StdSerializer<OffsetDateTime>(OffsetDateTime.class) {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializationContext ctx) {
                gen.writeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        });
        return zoneIdModule;
    }

    /**
     * 自定义JsonUtils对象的配置方法
     *
     * @param customConfiguration 自定义的配置对象
     */
    public void configuration(JsonConfiguration customConfiguration) {
        JsonConfiguration defaultConfiguration = defaultConfiguration();
        CommonUtils.copyProperties(customConfiguration, defaultConfiguration, true);
        MAPPER_LOWER_CAMEL_CASE = rebuildMapper(defaultConfiguration);
        MAPPER_SNAKE_CASE = MAPPER_LOWER_CAMEL_CASE.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
    /**
     * 获取可自定义的JsonUtils对象的方法
     * <p>当需要自定义序列化/反序列化配置时，可以调用此方法获取一个新的JsonUtils对象</p>
     *
     * @param mapper 自定义的ObjectMapper对象
     * @return       返回一个新的JsonUtils对象
     */
    public JsonUtils custom(ObjectMapper mapper) {
        return new JsonUtils(mapper);
    }

    /**
     * 获取一个ObjectMapper构建器的方法
     * @return 返回ObjectMapper构建器
     */
    public MapperBuilder<?, ?> cloneMapper() {
        return Bean.INSTANCE.MAPPER_LOWER_CAMEL_CASE.rebuild();
    }

    /**
     * 序列化对象为JSON字符串的方法（会过滤掉null值）
     *
     * @param value         待序列化的对象
     * @param prettyPrinter 输出美化后的JSON字符串
     * @return 返回JSON字符串（如果对象转换错误将返回null）
     */
    @Nullable
    public String serialize(Object value, boolean... prettyPrinter) {
        try {
            if (prettyPrinter != null && prettyPrinter.length > 0 && prettyPrinter[0]) {
                return MAPPER_LOWER_CAMEL_CASE.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            }
            return MAPPER_LOWER_CAMEL_CASE.writeValueAsString(value);
        } catch (JacksonException e) {
            log.error("序列化失败。content={}, stack={}", value, e.getMessage());
            return null;
        }
    }

    /**
     * 序列化对象为JSON字符串的方法
     *
     * @param value         待序列化的对象
     * @param prettyPrinter 输出美化后的JSON字符串
     * @return 返回JSON字符串（如果对象转换错误将返回null）
     */
    @Nullable
    public String serializeBySnake(Object value, boolean... prettyPrinter) {
        try {
            if (prettyPrinter != null && prettyPrinter.length > 0 && prettyPrinter[0]) {
                return MAPPER_SNAKE_CASE.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            }
            return MAPPER_SNAKE_CASE.writeValueAsString(value);
        } catch (JacksonException e) {
            log.error("序列化失败。content={}, stack={}", value, e.getMessage());
            return null;
        }
    }

    /**
     * 将指定对象序列化为字节数组的方法
     *
     * @param value 需要序列化的对象
     * @return 返回字节数组
     */
    @Nullable
    public byte[] serializeBytes(Object value) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.writeValueAsBytes(value);
        } catch (JacksonException e) {
            log.error("序列化失败。content={}, stack={}", value, e.getMessage());
            return null;
        }
    }

    /**
     * 将指定对象序列化为字节数组的方法
     *
     * @param value 需要序列化的对象
     * @return 返回字节数组
     */
    @Nullable
    public byte[] serializeBytesBySnake(Object value) {
        try {
            return MAPPER_SNAKE_CASE.writeValueAsBytes(value);
        } catch (JacksonException e) {
            log.error("序列化失败。content={}, stack={}", value, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON字符串的方法
     *
     * @param content   JSON字符串
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(String content, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(content, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。content={}, class={}, stack={}", content, valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON字符串的方法
     *
     * @param content   JSON字符串
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(String content, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(content, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。content={}, class={}, stack={}", content, valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON字符串的方法
     *
     * @param content      JSON字符串
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(String content, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(content, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。content={}, class={}, stack={}", content, valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON字符串的方法
     *
     * @param content      JSON字符串
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(String content, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_SNAKE_CASE.readValue(content, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。content={}, class={}, stack={}", content, valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化输入流内容为指定类型对象的方法
     *
     * @param src       输入流对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(InputStream src, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(src, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化输入流内容为指定类型对象的方法
     *
     * @param src       输入流对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(InputStream src, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(src, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化输入流内容为指定类型对象的方法
     *
     * @param src          输入流对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(InputStream src, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(src, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化输入流内容为指定类型对象的方法
     *
     * @param src          输入流对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(InputStream src, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_SNAKE_CASE.readValue(src, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化URL内容为指定类型对象的方法
     * <p>
     * Jackson 3.0: readValue(URL, ...) 方法已被移除，需要通过 URL.openStream() 获取 InputStream 后反序列化
     *
     * @param url       URL对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(URL url, Class<T> valueType) {
        try (InputStream inputStream = url.openStream()) {
            return MAPPER_LOWER_CAMEL_CASE.readValue(inputStream, valueType);
        } catch (IOException | JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。url={}, class={}, stack={}", url, valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化URL内容为指定类型对象的方法
     * <p>
     * Jackson 3.0: readValue(URL, ...) 方法已被移除，需要通过 URL.openStream() 获取 InputStream 后反序列化
     *
     * @param url       URL对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(URL url, Class<T> valueType) {
        try (InputStream inputStream = url.openStream()) {
            return MAPPER_SNAKE_CASE.readValue(inputStream, valueType);
        } catch (IOException | JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。url={}, class={}, stack={}", url, valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化URL内容为指定类型对象的方法
     * <p>
     * Jackson 3.0: readValue(URL, ...) 方法已被移除，需要通过 URL.openStream() 获取 InputStream 后反序列化
     *
     * @param url          URL对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(URL url, TypeReference<T> valueTypeRef) {
        try (InputStream inputStream = url.openStream()) {
            return MAPPER_LOWER_CAMEL_CASE.readValue(inputStream, valueTypeRef);
        } catch (IOException | JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。url={}, class={}, stack={}", url, valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化URL内容为指定类型对象的方法
     * <p>
     * Jackson 3.0: readValue(URL, ...) 方法已被移除，需要通过 URL.openStream() 获取 InputStream 后反序列化
     *
     * @param url          URL对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(URL url, TypeReference<T> valueTypeRef) {
        try (InputStream inputStream = url.openStream()) {
            return MAPPER_SNAKE_CASE.readValue(inputStream, valueTypeRef);
        } catch (IOException | JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。url={}, class={}, stack={}", url, valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化文件内容为目标类型对象的方法
     *
     * @param file      文件对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(File file, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(file, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。file={}, class={}, stack={}", file.getAbsoluteFile(), valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化文件内容为目标类型对象的方法
     *
     * @param file      文件对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(File file, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(file, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。file={}, class={}, stack={}", file.getAbsoluteFile(), valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化文件内容为目标类型对象的方法
     *
     * @param file         文件对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(File file, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(file, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。file={}, class={}, stack={}", file.getAbsoluteFile(), valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化文件内容为目标类型对象的方法
     *
     * @param file         文件对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(File file, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_SNAKE_CASE.readValue(file, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字符流内容为目标类型对象的方法
     *
     * @param reader    字符流对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(Reader reader, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(reader, valueType);
        } catch (JacksonException e) {
            log.error("当前字符流与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字符流内容为目标类型对象的方法
     *
     * @param reader    字符流对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(Reader reader, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(reader, valueType);
        } catch (JacksonException e) {
            log.error("当前字符流与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字符流内容为目标类型对象的方法
     *
     * @param reader       字符流对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(Reader reader, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(reader, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前字符流与需要转换的类型不匹配，转换失败。class={}, stack={}", valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字符流内容为目标类型对象的方法
     *
     * @param reader       字符流对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(Reader reader, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_SNAKE_CASE.readValue(reader, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前字符流与需要转换的类型不匹配，转换失败。class={}, stack={}", valueTypeRef, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字符流内容为目标类型对象的方法
     *
     * @param input     数据输入流对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(DataInput input, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(input, valueType);
        } catch (JacksonException e) {
            log.error("当前数据输入流与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化字符流内容为目标类型对象的方法
     *
     * @param input     数据输入流对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(DataInput input, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(input, valueType);
        } catch (JacksonException e) {
            log.error("当前数据输入流与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param parser    JSON解析器对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(JsonParser parser, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(parser, valueType);
        } catch (JacksonException e) {
            log.error("当前解析器解析的内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param parser    JSON解析器对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(JsonParser parser, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(parser, valueType);
        } catch (JacksonException e) {
            log.error("当前解析器解析的内容与需要转换的类型不匹配，转换失败。class={}, stack={}", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param parser       JSON解析器对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(JsonParser parser, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(parser, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前解析器解析的内容与需要转换的类型不匹配，转换失败。stack={}", e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param parser       JSON解析器对象
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializeBySnake(JsonParser parser, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_SNAKE_CASE.readValue(parser, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前解析器解析的内容与需要转换的类型不匹配，转换失败。(stack={})", e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化树结构对象为指定类型对象的方法
     *
     * @param node      树节点对象
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserialize(JsonNode node, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.treeToValue(node, valueType);
        } catch (JacksonException e) {
            log.error("当前解析器解析的内容与需要转换的类型不匹配，转换失败。(class={}, stack={})", valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param payload   数据负载
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializePayload(byte[] payload, Class<T> valueType) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(payload, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。(content={}, class={}, stack={})", new String(payload), valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param payload   数据负载
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializePayloadBySnake(byte[] payload, Class<T> valueType) {
        try {
            return MAPPER_SNAKE_CASE.readValue(payload, valueType);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。(content={}, class={}, stack={})", new String(payload), valueType, e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param payload      数据负载
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializePayload(byte[] payload, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_LOWER_CAMEL_CASE.readValue(payload, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。(content={}, stack={})", new String(payload), e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON Payload的方法
     *
     * @param payload      数据负载
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T deserializePayloadBySnake(byte[] payload, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER_SNAKE_CASE.readValue(payload, valueTypeRef);
        } catch (JacksonException e) {
            log.error("当前内容与需要转换的类型不匹配，转换失败。(content={}, stack={})", new String(payload), e.getMessage());
            return null;
        }
    }

    /**
     * 将对象转换为Map的方法
     *
     * @param obj 待转换的对象
     * @return 返回转换后的Map对象
     */
    public Map<String, Object> convertObjectToMap(Object obj) {
        return MAPPER_LOWER_CAMEL_CASE.convertValue(obj, new TypeReference<>() {
        });
    }

    /**
     * 将对象转换为Map的方法（驼峰转下划线）
     *
     * @param obj 待转换的对象
     * @return 返回转换后的Map对象
     */
    public Map<String, Object> convertObjectToMapBySnake(Object obj) {
        return MAPPER_SNAKE_CASE.convertValue(obj, new TypeReference<>() {
        });
    }


    /**
     * 转换对象的方法
     *
     * @param obj          数据负载
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T convertObject(Object obj, TypeReference<T> valueTypeRef) {
        return MAPPER_LOWER_CAMEL_CASE.convertValue(obj, valueTypeRef);
    }

    /**
     * 转换对象的方法
     *
     * @param obj          数据负载
     * @param valueTypeRef 反序列化字符串的类型
     * @param <T>          JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T convertObjectBySnake(Object obj, TypeReference<T> valueTypeRef) {
        return MAPPER_SNAKE_CASE.convertValue(obj, valueTypeRef);
    }

    /**
     * 转换对象的方法
     *
     * @param obj       数据负载
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T convertObject(Object obj, Class<T> valueType) {
        return MAPPER_LOWER_CAMEL_CASE.convertValue(obj, valueType);
    }

    /**
     * 转换对象的方法
     *
     * @param obj       数据负载
     * @param valueType 反序列化字符串的类型
     * @param <T>       JSON字符串对应的实体类型
     * @return 返回反序列化后的结果
     */
    @Nullable
    public <T> T convertObjectBySnake(Object obj, Class<T> valueType) {
        return MAPPER_SNAKE_CASE.convertValue(obj, valueType);
    }


    /**
     * 将Json字符串转换为通用JSON格式对象的方法
     *
     * @param jsonString 待转换的对象
     * @return 返回 JSON 通用格式对象
     */
    @Nullable
    public JsonNode convertJsonNode(@Nonnull String jsonString) {
        Objects.requireNonNull(jsonString, "待解析的对象不能为null。");
        try {
            return MAPPER_LOWER_CAMEL_CASE.readTree(jsonString);
        } catch (JacksonException e) {
            log.error("当前内容无法转换为JsonNode对象，转换失败。", e);
            return null;
        }
    }

    /**
     * 将Json字符串转换为通用JSON格式对象的方法
     *
     * @param jsonString 待转换的Json字符串
     * @return 返回 JSON 通用格式对象
     */
    @Nullable
    public JsonNode convertJsonNodeBySnake(@Nonnull String jsonString) {
        Objects.requireNonNull(jsonString, "待解析的对象不能为null。");
        try {
            return MAPPER_SNAKE_CASE.readTree(jsonString);
        } catch (JacksonException e) {
            log.error("当前内容无法转换为JsonNode对象，转换失败。", e);
            return null;
        }
    }

    /**
     * 将对象转换为通用只读JSON格式对象的方法（Json字符串请勿使用该方法）
     *
     * @param obj 待转换的对象
     * @return 返回 JSON 通用格式对象
     * @see #convertJsonNode(String) Json字符串格式化请使用 {@code convertJsonNode(String)} 方法
     */
    @Nullable
    public JsonNode toJsonNode(@Nonnull Object obj) {
        Objects.requireNonNull(obj, "待解析的对象不能为null。");
        return MAPPER_LOWER_CAMEL_CASE.valueToTree(obj);
    }

    /**
     * 将对象转换为通用可读写的JSON格式对象的方法（Json字符串请勿使用该方法）
     *
     * @param obj 待转换的对象
     * @return 返回 JSON 通用格式对象
     * @see #convertJsonNode(String) Json字符串格式化请使用 {@code convertJsonNode(String)} 方法
     */
    @Nullable
    public ObjectNode toObjectNode(@Nonnull Object obj) {
        Objects.requireNonNull(obj, "待解析的对象不能为null。");
        // Jackson 3.0: convertValue 返回类型需要明确指定
        return (ObjectNode) MAPPER_LOWER_CAMEL_CASE.convertValue(obj, ObjectNode.class);
    }

    /**
     * 将对象转换为通用JSON格式对象的方法（Json字符串请勿使用该方法）
     *
     * @param obj 待转换的对象
     * @return 返回 JSON 通用格式对象
     * @see #convertJsonNodeBySnake(String) Json字符串格式化请使用 {@code convertJsonNodeBySnake(String)} 方法
     */
    @Nullable
    public JsonNode toJsonNodeBySnake(@Nonnull Object obj) {
        Objects.requireNonNull(obj, "待解析的对象不能为null。");
        return MAPPER_SNAKE_CASE.valueToTree(obj);
    }

    /**
     * 将通用JSON格式对象转换为Java对象的方法
     *
     * @param <T> Java对象类型
     * @param node  待转换的对象
     * @param clazz Java对象类型
     * @return 返回 JSON 通用格式对象
     */
    @Nullable
    public <T> T toJavaObject(@Nonnull JsonNode node, Class<T> clazz) {
        Objects.requireNonNull(node, "待解析的对象不能为null。");
        try {
            return MAPPER_LOWER_CAMEL_CASE.treeToValue(node, clazz);
        } catch (JacksonException e) {
            log.error("当前内容无法转换为JsonNode对象，转换失败。", e);
            return null;
        }
    }

    /**
     * 将通用JSON格式对象转换为Java对象的方法
     *
     * @param <T>       Java对象类型
     * @param node      待转换的对象
     * @param javaType  反序列化字符串的类型
     * @return          返回 JSON 通用格式对象
     */
    @Nullable
    public <T> T toObjectNode(@Nonnull JsonNode node, JavaType javaType) {
        Objects.requireNonNull(node, "待解析的对象不能为null。");
        try {
            return MAPPER_LOWER_CAMEL_CASE.treeToValue(node, javaType);
        } catch (JacksonException e) {
            log.error("当前内容无法转换为JsonNode对象，转换失败。", e);
            return null;
        }
    }

}

package com.richie.component.search.config.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import jakarta.annotation.Nonnull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Elasticsearch 自定义转换器配置
 *
 * <p>配置 Elasticsearch 专用的数据类型转换器，支持：
 * <ul>
 *   <li>LocalDateTime 与字符串转换</li>
 *   <li>OffsetDateTime 与字符串转换</li>
 *   <li>ZonedDateTime 与字符串转换</li>
 *   <li>自定义日期格式处理</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "platform.component.search", name = "provider", havingValue = "elasticsearch")
public class ElasticsearchConverterConfiguration {

    /**
     * ISO8601 日期时间格式 (不带时区)
     */
    private static final DateTimeFormatter LOCAL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    /**
     * ISO8601 日期时间格式 (带时区偏移)
     */
    private static final DateTimeFormatter OFFSET_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * ISO8601 日期时间格式 (带时区)
     */
    private static final DateTimeFormatter ZONED_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'['VV']'");

    /**
     * 创建自定义的 ElasticsearchCustomConversions
     */
    @Bean
    public ElasticsearchCustomConversions elasticsearchCustomConversions() {
        log.info("创建自定义 Elasticsearch 日期转换器");

        List<Converter<?, ?>> converters = Arrays.asList(
                // LocalDateTime 转换器
                new LocalDateTimeToStringConverter(),
                new StringToLocalDateTimeConverter(),

                // OffsetDateTime 转换器
                new OffsetDateTimeToStringConverter(),
                new StringToOffsetDateTimeConverter(),

                // ZonedDateTime 转换器
                new ZonedDateTimeToStringConverter(),
                new StringToZonedDateTimeConverter()
        );

        return new ElasticsearchCustomConversions(converters);
    }

    /**
     * 创建自定义的 ElasticsearchConverter
     */
    @Bean
    public ElasticsearchConverter elasticsearchConverter(ElasticsearchCustomConversions customConversions) {
        log.info("创建自定义 ElasticsearchConverter");

        // 创建映射上下文
        SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
        mappingContext.setInitialEntitySet(java.util.Collections.emptySet());
        mappingContext.afterPropertiesSet();

        // 创建转换器
        MappingElasticsearchConverter converter = new MappingElasticsearchConverter(mappingContext);
        converter.setConversions(customConversions);
        converter.afterPropertiesSet();

        return converter;
    }

    // ==================== LocalDateTime 转换器 ====================

    @WritingConverter
    public static class LocalDateTimeToStringConverter implements Converter<LocalDateTime, String> {
        @Override
        public String convert(@Nonnull LocalDateTime source) {
            String result = source.format(LOCAL_DATETIME_FORMATTER);
            log.debug("LocalDateTime → String: {} → {}", source, result);
            return result;
        }
    }

    @ReadingConverter
    public static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(@Nonnull String source) {
            try {
                LocalDateTime result = LocalDateTime.parse(source, LOCAL_DATETIME_FORMATTER);
                log.debug("String → LocalDateTime: {} → {}", source, result);
                return result;
            } catch (DateTimeParseException e) {
                try {
                    return LocalDateTime.parse(source, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                } catch (DateTimeParseException e2) {
                    log.warn("无法解析 LocalDateTime: {}", source);
                    throw new IllegalArgumentException("无法解析 LocalDateTime 格式: " + source, e2);
                }
            }
        }
    }

    // ==================== OffsetDateTime 转换器 ====================

    @WritingConverter
    public static class OffsetDateTimeToStringConverter implements Converter<OffsetDateTime, String> {
        @Override
        public String convert(@Nonnull OffsetDateTime source) {
            String result = source.format(OFFSET_DATETIME_FORMATTER);
            log.debug("OffsetDateTime → String: {} → {}", source, result);
            return result;
        }
    }

    @ReadingConverter
    public static class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(@Nonnull String source) {
            try {
                OffsetDateTime result = OffsetDateTime.parse(source, OFFSET_DATETIME_FORMATTER);
                log.debug("String → OffsetDateTime: {} → {}", source, result);
                return result;
            } catch (DateTimeParseException e) {
                try {
                    return OffsetDateTime.parse(source);
                } catch (DateTimeParseException e2) {
                    try {
                        String fixedSource = source.replaceAll("([+-]\\d{2})$", "$1:00");
                        return OffsetDateTime.parse(fixedSource);
                    } catch (DateTimeParseException e3) {
                        log.warn("无法解析 OffsetDateTime: {}", source);
                        throw new IllegalArgumentException("无法解析 OffsetDateTime 格式: " + source, e3);
                    }
                }
            }
        }
    }

    // ==================== ZonedDateTime 转换器 ====================

    @WritingConverter
    public static class ZonedDateTimeToStringConverter implements Converter<ZonedDateTime, String> {
        @Override
        public String convert(@Nonnull ZonedDateTime source) {
            String result = source.format(ZONED_DATETIME_FORMATTER);
            log.debug("ZonedDateTime → String: {} → {}", source, result);
            return result;
        }
    }

    @ReadingConverter
    public static class StringToZonedDateTimeConverter implements Converter<String, ZonedDateTime> {
        @Override
        public ZonedDateTime convert(@Nonnull String source) {
            try {
                ZonedDateTime result = ZonedDateTime.parse(source, ZONED_DATETIME_FORMATTER);
                log.debug("String → ZonedDateTime: {} → {}", source, result);
                return result;
            } catch (DateTimeParseException e) {
                try {
                    return ZonedDateTime.parse(source);
                } catch (DateTimeParseException e2) {
                    try {
                        OffsetDateTime offsetDateTime = OffsetDateTime.parse(source);
                        return offsetDateTime.atZoneSameInstant(ZoneId.systemDefault());
                    } catch (DateTimeParseException e3) {
                        log.warn("无法解析 ZonedDateTime: {}", source);
                        throw new IllegalArgumentException("无法解析 ZonedDateTime 格式: " + source, e3);
                    }
                }
            }
        }
    }
}

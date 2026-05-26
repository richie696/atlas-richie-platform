package com.richie.component.search.config.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Elasticsearch Jackson 配置类
 *
 * <p>专门为 Elasticsearch 配置 Jackson 序列化器，确保日期时间字段能够正确序列化为 ISO8601 格式。
 *
 * <p>主要功能：
 * <ul>
 *   <li>配置 LocalDateTime 序列化为 ISO8601 格式</li>
 *   <li>配置 LocalDateTime 反序列化支持多种格式</li>
 *   <li>确保与 Elasticsearch 的日期格式兼容</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "platform.component.search", name = "provider", havingValue = "elasticsearch")
public class ElasticsearchJacksonConfiguration {

    /**
     * ISO8601 日期时间格式
     */
    private static final String ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * 创建 Elasticsearch 专用的 ObjectMapper
     *
     * <p>配置了 LocalDateTime 的序列化和反序列化，确保与 Elasticsearch 的日期格式兼容。
     *
     * @return 配置好的 ObjectMapper 实例
     */
    @Bean("elasticsearchObjectMapper")
    public ObjectMapper elasticsearchObjectMapper() {
        // 创建全新的 ObjectMapper，避免 JsonUtils 的日期格式配置干扰
        ObjectMapper mapper = new ObjectMapper();

        // 创建 JavaTimeModule 来处理 Java 8 时间类型
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 配置 LocalDateTime 序列化器 - 使用 ISO8601 格式
        LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern(ISO8601_FORMAT)
        );
        javaTimeModule.addSerializer(LocalDateTime.class, localDateTimeSerializer);

        // 配置 LocalDateTime 反序列化器 - 支持多种格式
        LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(
                DateTimeFormatter.ofPattern(ISO8601_FORMAT)
        );
        javaTimeModule.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);

        // 注册 JavaTimeModule
        mapper.registerModule(javaTimeModule);

        // 禁用将日期写为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 添加其他基本配置
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        log.info("Elasticsearch ObjectMapper 配置完成，LocalDateTime 将序列化为 ISO8601 格式: {}", ISO8601_FORMAT);

        return mapper;
    }
}

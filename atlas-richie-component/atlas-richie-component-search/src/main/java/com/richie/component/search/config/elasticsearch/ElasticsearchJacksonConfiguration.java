package com.richie.component.search.config.elasticsearch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch Jackson 配置类
 *
 * <p>注册 JavaTimeModule，让 Jackson 使用 ISO-8601 格式序列化所有 JSR-310 时间类型
 *（LocalDateTime / OffsetDateTime / ZonedDateTime / Instant 等），
 * 确保与 Elasticsearch 的日期格式兼容。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "platform.component.search", name = "provider", havingValue = "elasticsearch")
public class ElasticsearchJacksonConfiguration {

    @Bean("elasticsearchObjectMapper")
    public ObjectMapper elasticsearchObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // JavaTimeModule 内置所有 JSR-310 类型的序列化器/反序列化器，
        // 禁用 WRITE_DATES_AS_TIMESTAMPS 后自动输出 ISO-8601 格式。
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        log.info("Elasticsearch ObjectMapper 配置完成，JSR-310 时间类型将序列化为 ISO-8601 格式");

        return mapper;
    }
}

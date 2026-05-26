package com.richie.component.search.config;

import com.richie.component.search.config.elasticsearch.ElasticsearchAutoConfiguration;
import com.richie.component.search.config.elasticsearch.ElasticsearchConverterConfiguration;
import com.richie.component.search.config.elasticsearch.ElasticsearchJacksonConfiguration;
import com.richie.component.search.config.properties.ElasticsearchConfig;
import com.richie.component.search.config.properties.SearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 搜索服务主配置类
 *
 * <p>作为搜索组件的统一入口，负责：
 * <ul>
 *   <li>导入各个搜索引擎的专用配置</li>
 *   <li>启用配置属性绑定</li>
 *   <li>组件扫描配置</li>
 * </ul>
 *
 * <p>支持的搜索引擎：
 * <ul>
 *   <li>Elasticsearch：当 {@code platform.component.search.provider=elasticsearch} 时启用</li>
 *   <li>Solr：当 {@code platform.component.search.provider=solr} 时启用</li>
 * </ul>
 *
 * <p>配置结构：
 * <ul>
 *   <li><b>properties/</b>：纯配置属性类</li>
 *   <li><b>elasticsearch/</b>：ES 自动装配配置</li>
 *   <li><b>solr/</b>：Solr 自动装配配置</li>
 *   <li><b>common/</b>：公共配置基类</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @see ElasticsearchAutoConfiguration
 * @see SearchProperties
 * @since 2025-12-09
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.search")
@EnableConfigurationProperties({SearchProperties.class, ElasticsearchConfig.class})
@Import({
        // Elasticsearch 相关配置
        ElasticsearchAutoConfiguration.class,
        ElasticsearchConverterConfiguration.class,
        ElasticsearchJacksonConfiguration.class,

        // 其它搜索引擎配置继续往后加
})
public class SearchAutoConfiguration {

    /**
     * 默认构造函数，打印搜索模块初始化日志。
     */
    public SearchAutoConfiguration() {
        log.info("初始化搜索服务配置模块");
    }

}

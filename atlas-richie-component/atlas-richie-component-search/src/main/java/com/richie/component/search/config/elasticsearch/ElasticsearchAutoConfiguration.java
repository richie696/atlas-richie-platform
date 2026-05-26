package com.richie.component.search.config.elasticsearch;

import com.richie.component.search.config.common.AbstractSearchAutoConfiguration;
import com.richie.component.search.config.properties.SearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;

import javax.net.ssl.SSLContext;
import java.util.Arrays;

/**
 * Elasticsearch 专用自动配置类
 *
 * <p>负责 Elasticsearch 相关的所有 Bean 配置，包括：
 * <ul>
 *   <li>ElasticsearchClient 配置</li>
 *   <li>ReactiveElasticsearchClient 配置</li>
 *   <li>ElasticsearchTemplate 配置</li>
 *   <li>连接、认证、SSL 配置</li>
 *   <li>健康检查配置</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.component.search", name = "provider", havingValue = "elasticsearch")
public class ElasticsearchAutoConfiguration extends AbstractSearchAutoConfiguration {

    /** 搜索组件配置（含 ES 连接、认证、SSL 等） */
    private final SearchProperties searchProperties;

    /**
     * 创建响应式 Elasticsearch 客户端 Bean。
     *
     * @param elasticsearchObjectMapper ES 专用 ObjectMapper
     * @return ReactiveElasticsearchClient
     */
    @Bean
    public ReactiveElasticsearchClient reactiveElasticsearchClient(ObjectMapper elasticsearchObjectMapper) {
        log.info("创建 ReactiveElasticsearchClient");
        ElasticsearchTransport transport = createElasticsearchTransport(elasticsearchObjectMapper);
        return new ReactiveElasticsearchClient(transport);
    }

    /**
     * 创建 Elasticsearch 同步客户端 Bean（含健康检查）。
     *
     * @param elasticsearchObjectMapper ES 专用 ObjectMapper
     * @return ElasticsearchClient
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ObjectMapper elasticsearchObjectMapper) {
        log.info("创建 ElasticsearchClient");
        ElasticsearchTransport transport = createElasticsearchTransport(elasticsearchObjectMapper);
        var client = new ElasticsearchClient(transport);

        // 执行健康检查
        performHealthCheck(client);

        return client;
    }

    /**
     * 创建 ElasticsearchTemplate Bean。
     *
     * @param elasticsearchClient       ES 客户端
     * @param elasticsearchConverter    ES 转换器
     * @return ElasticsearchTemplate
     */
    @Bean
    public ElasticsearchTemplate elasticsearchTemplate(ElasticsearchClient elasticsearchClient,
                                                       ElasticsearchConverter elasticsearchConverter) {
        log.info("创建 ElasticsearchTemplate");
        return new ElasticsearchTemplate(elasticsearchClient, elasticsearchConverter);
    }

    // ==================== 私有方法 ====================

    /**
     * 创建 ElasticsearchTransport
     * <p>
     * Elasticsearch Java Client 9.x: 使用 Rest5Client 替代 RestClient
     * @param objectMapper ES 专用 ObjectMapper
     * @return ElasticsearchTransport
     */
    private ElasticsearchTransport createElasticsearchTransport(ObjectMapper objectMapper) {
        Rest5Client rest5Client = createRest5Client();
        return new Rest5ClientTransport(rest5Client, new JacksonJsonpMapper(objectMapper));
    }

    /**
     * 根据配置创建 RestClient（含认证、超时、SSL）。
     *
     * @return RestClient
     * 创建 Rest5Client (Elasticsearch Java Client 9.x)
     */
    private Rest5Client createRest5Client() {
        var pool = searchProperties.getPool();
        var sslConfig = searchProperties.getSsl();
        HttpHost[] httpHosts = parseHttpHosts(searchProperties.getHosts());

        Rest5ClientBuilder builder = Rest5Client.builder(httpHosts);

        // 认证配置
        configureAuthentication(builder, pool, sslConfig);

        // 超时配置
        configureTimeouts(builder, pool);

        return builder.build();
    }

    /**
     * 配置 RestClient 的认证、连接池与 SSL。
     *
     * @param builder RestClient 构建器
     * @param pool             连接池配置
     * @param sslConfig        SSL 配置
     */
    private void configureAuthentication(Rest5ClientBuilder builder,
                                       SearchProperties.PoolConfig pool,
                                       SearchProperties.SslConfig sslConfig) {
        if (searchProperties.getUsername() != null && searchProperties.getPassword() != null) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(null, null, -1, null, null),
                    new UsernamePasswordCredentials(searchProperties.getUsername(), searchProperties.getPassword().toCharArray())
            );

            builder.setHttpClientConfigCallback((HttpAsyncClientBuilder httpClientBuilder) -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

                // 连接池配置（包含 SSL 配置）
                var connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(pool.getMaxTotal())
                        .setMaxConnPerRoute(pool.getMaxPerRoute());

                // SSL配置（需要在连接管理器中配置）
                if (sslConfig.isEnabled()) {
                    SSLContext sslContext = configureSslContext(sslConfig);
                    // Apache HttpClient 5.x: build() 已废弃，异步客户端使用 buildAsync()
                    var tlsStrategy = ClientTlsStrategyBuilder.create()
                            .setSslContext(sslContext)
                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                            .buildAsync();
                    connectionManagerBuilder.setTlsStrategy(tlsStrategy);
                }

                httpClientBuilder.setConnectionManager(connectionManagerBuilder.build());
            });
        }
    }

    /**
     * 配置超时
     * <p>
     * Elasticsearch Java Client 9.x:
     * - 连接超时和 Socket 超时通过 setConnectionConfigCallback 配置
     * - 响应超时通过 setRequestConfigCallback 配置
     */
    private void configureTimeouts(Rest5ClientBuilder builder, SearchProperties.PoolConfig pool) {
        // 配置连接超时和 Socket 超时
        builder.setConnectionConfigCallback((ConnectionConfig.Builder connectionConfigBuilder) -> {
            connectionConfigBuilder.setConnectTimeout(Timeout.ofMilliseconds(searchProperties.getConnectTimeout()))
                    .setSocketTimeout(Timeout.ofMilliseconds(searchProperties.getSocketTimeout()));
        });

        // 配置响应超时和其他请求配置
        builder.setRequestConfigCallback((RequestConfig.Builder requestConfigBuilder) -> {
            requestConfigBuilder.setResponseTimeout(Timeout.ofMilliseconds(searchProperties.getSocketTimeout()))
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(pool.getConnectionRequestTimeout()))
                    .setRedirectsEnabled(true)
                    .setMaxRedirects(3);
        });
    }


    /**
     * 执行健康检查
     */
    private void performHealthCheck(ElasticsearchClient client) {
        var esProps = searchProperties.getElasticsearch();
        try {
            if (esProps != null && esProps.getCluster() != null && esProps.getCluster().isHealthCheck()) {
                // Ping 检查
                var pingResponse = client.ping();
                boolean pingOk = pingResponse.value();
                log.info("Elasticsearch ping result: {}", pingOk);

                if (!pingOk) {
                    throw new RuntimeException("Elasticsearch 健康检查失败：ping 未通过");
                }

                // 集群名称校验
                var info = client.info();
                var expectedClusterName = esProps.getCluster().getName();
                if (expectedClusterName != null && !expectedClusterName.isBlank()) {
                    if (!expectedClusterName.equals(info.clusterName())) {
                        throw new RuntimeException("已连接集群名称(%s)与期望名称(%s)不一致".formatted(info.clusterName(), expectedClusterName));
                    }
                }

                // 健康状态检查
                int timeoutMs = esProps.getCluster().getHealthCheckTimeout() != null ? esProps.getCluster().getHealthCheckTimeout() : 30000;
                var health = client.cluster().health(h ->
                        h.waitForStatus(HealthStatus.Yellow)
                                .timeout(t -> t.time("%dms".formatted(timeoutMs)))
                );
                log.info("Elasticsearch cluster health status: {}", health.status());

                if (health.status() == HealthStatus.Red) {
                    throw new RuntimeException("Elasticsearch 集群健康状态为 RED");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Elasticsearch 健康/集群校验执行异常", e);
        }
    }

    /**
     * 解析逗号分隔的 hosts 字符串为 HttpHost 数组。
     *
     * @param hosts 主机列表字符串（如 http://localhost:9200,http://localhost:9201）
     * @return HttpHost 数组
     * @throws IllegalArgumentException hosts 为空或未配置时抛出
     */
    private HttpHost[] parseHttpHosts(String hosts) {
        if (hosts == null || hosts.isBlank()) {
            throw new IllegalArgumentException("platform.component.search.hosts 未配置或为空");
        }
        return Arrays.stream(hosts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(host -> {
                    try {
                        return HttpHost.create(host);
                    } catch (java.net.URISyntaxException e) {
                        throw new IllegalArgumentException("无效的 Elasticsearch host: " + host, e);
                    }
                })
                .toArray(HttpHost[]::new);
    }
}

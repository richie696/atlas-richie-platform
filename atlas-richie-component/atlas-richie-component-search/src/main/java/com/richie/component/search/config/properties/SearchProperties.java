package com.richie.component.search.config.properties;

import com.richie.component.search.enums.KeystoreType;
import com.richie.component.search.enums.SearchEngineProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索服务配置属性类
 *
 * <p>该类用于配置搜索服务的各种参数，支持多种搜索引擎（Elasticsearch、Solr）的配置。
 * 通过 Spring Boot 的配置属性机制，可以从配置文件中读取相关配置。
 *
 * <p>配置前缀：{@code platform.component.search}
 *
 * <p>示例配置：
 * <pre>
 * platform:
 *   component:
 *     search:
 *       provider: ELASTICSEARCH
 *       connectionPool:
 *         maxTotal: 100
 *         maxPerRoute: 50
 *       elasticsearch:
 *         hosts: <a href="https://localhost:9200">https://localhost:9200</a>
 *         auth:
 *           username: elastic
 *           password: password
 * </pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 * @see SearchEngineProvider
 * @see PoolConfig
 * @see ElasticsearchConfig
 */
@Data
@ConfigurationProperties(prefix = "platform.component.search")
public class SearchProperties {

    /**
     * 通用连接池配置
     *
     * <p>适用于所有搜索引擎的 HTTP 客户端连接池配置。
     * <p>连接池用于管理和复用与搜索引擎集群的 HTTP 连接，提高性能并控制资源使用。
     *
     * @see PoolConfig
     */
    private PoolConfig pool = new PoolConfig();

    /**
     * 服务地址
     *
     * <p>搜索引擎服务的连接地址。
     *
     * <b>Elasticsearch 示例：</b>
     * <ul>
     *   <li>单节点：http://localhost:9200</li>
     *   <li>多节点：http://es1:9200,http://es2:9200,http://es3:9200</li>
     *   <li>HTTPS：https://es.example.com:9200</li>
     * </ul>
     */
    private String hosts;

    /**
     * 认证用户名
     *
     * <p>当搜索引擎开启安全认证时必填。
     *
     * <b>常见用户名：</b>
     * <ul>
     *   <li>Elasticsearch：elastic、admin</li>
     *   <li>Solr：solr、admin</li>
     * </ul>
     */
    private String username;

    /**
     * 认证密码
     *
     * <p>当搜索引擎开启安全认证时必填。
     * <p>建议使用强密码，并定期更换。
     */
    private String password;

    /**
     * 连接超时时间
     *
     * <p>建立连接到搜索引擎服务的超时时间。
     * <p>单位：毫秒，默认值：5000
     * <p>建议值：3000-10000
     *
     * <b>调优说明：</b>
     * <ul>
     *   <li>内网环境：3000-5000ms</li>
     *   <li>公网环境：5000-8000ms</li>
     *   <li>网络较差：8000-10000ms</li>
     * </ul>
     */
    private Integer connectTimeout = 5000;

    /**
     * Socket 读取超时时间
     *
     * <p>从搜索引擎服务读取数据的超时时间。
     * <p>单位：毫秒，默认值：60000
     * <p>建议值：30000-120000
     *
     * <b>调优说明：</b>
     * <ul>
     *   <li>简单查询：30000-60000ms</li>
     *   <li>复杂聚合：60000-120000ms</li>
     *   <li>大数据量操作：120000ms以上</li>
     * </ul>
     */
    private Integer socketTimeout = 60000;

    /**
     * SSL安全配置
     *
     * <p>包含SSL证书、信任策略等安全相关配置。
     */
    private SslConfig ssl = new SslConfig();

    /**
     * Elasticsearch 特有配置
     *
     * <p>当 provider 设置为 ELASTICSEARCH 时使用此配置。
     * <p>包含 ES 特有的功能配置，通用配置请使用顶层属性。
     */
    private ElasticsearchConfig elasticsearch;

    /**
     * 通用连接池配置类
     *
     * <p>用于配置 HTTP 客户端连接池的各项参数，适用于所有搜索引擎。
     * <p>连接池是提高搜索引擎客户端性能的关键组件，通过复用连接减少网络开销。
     *
     * <strong>连接池工作原理：</strong>
     * <ul>
     *   <li><b>连接复用</b>：多个请求可以复用同一个 HTTP 连接</li>
     *   <li><b>并发控制</b>：通过最大连接数限制并发访问</li>
     *   <li><b>资源管理</b>：自动管理连接的创建、复用和销毁</li>
     *   <li><b>故障恢复</b>：自动检测和重建失效连接</li>
     * </ul>
     *
     * <strong>性能调优建议：</strong>
     * <ul>
     *   <li><b>高并发场景</b>：增大 maxTotal 和 maxPerRoute</li>
     *   <li><b>长连接场景</b>：增大 keepAliveTime，减少连接建立开销</li>
     *   <li><b>快速响应场景</b>：减小各种超时时间</li>
     *   <li><b>稳定性优先</b>：适当增大超时时间，避免偶发性超时</li>
     * </ul>
     *
     * @author richie696
     * @version 1.0
     * @since 2025-08-08
     */
    @Data
    public static class PoolConfig {

        /**
         * 连接超时时间
         *
         * <p>建立新连接到目标主机的超时时间。
         * <p>单位：毫秒，默认值：5000
         * <p>建议值：3000-10000
         *
         * <b>调优说明：</b>
         * <ul>
         *   <li>网络环境良好：3000-5000ms</li>
         *   <li>网络环境一般：5000-8000ms</li>
         *   <li>网络环境较差：8000-10000ms</li>
         * </ul>
         */
        private Integer connectTimeout = 5000;

        /**
         * Socket 读取超时时间
         *
         * <p>从服务器读取数据的超时时间。
         * <p>单位：毫秒，默认值：60000
         * <p>建议值：30000-120000
         *
         * <b>调优说明：</b>
         * <ul>
         *   <li>简单查询：30000-60000ms</li>
         *   <li>复杂聚合：60000-120000ms</li>
         *   <li>大数据量操作：120000ms以上</li>
         * </ul>
         */
        private Integer socketTimeout = 60000;

        /**
         * 连接池获取连接超时时间
         *
         * <p>从连接池中获取可用连接的超时时间。
         * <p>单位：毫秒，默认值：5000
         * <p>建议值：3000-10000
         *
         * <b>说明：</b>
         * 当连接池中所有连接都被占用时，新请求需要等待的最长时间。
         * 超时后会抛出 ConnectionRequestTimeoutException。
         */
        private Integer connectionRequestTimeout = 5000;

        /**
         * 连接池最大总连接数
         *
         * <p>整个连接池能够保持的最大连接数。
         * <p>默认值：100
         * <p>建议值：50-200
         *
         * <b>调优建议：</b>
         * <ul>
         *   <li>低并发应用：50-100</li>
         *   <li>中等并发应用：100-150</li>
         *   <li>高并发应用：150-200</li>
         * </ul>
         */
        private Integer maxTotal = 100;

        /**
         * 单个路由（搜索引擎节点）最大连接数
         *
         * <p>对单个搜索引擎节点能够保持的最大连接数。
         * <p>默认值：100
         * <p>建议值：50-200
         *
         * <b>说明：</b>
         * 在多节点搜索引擎集群中，每个节点都被视为一个路由。
         * 该参数控制对单个节点的连接数，避免单点压力过大。
         */
        private Integer maxPerRoute = 100;

        /**
         * 连接保活时间
         *
         * <p>HTTP 连接的 keep-alive 持续时间。
         * <p>单位：毫秒，默认值：20000
         * <p>建议值：10000-60000
         *
         * <b>说明：</b>
         * 连接在空闲状态下保持活跃的时间，超过此时间的空闲连接会被关闭。
         * 适当的保活时间可以减少连接建立的开销。
         */
        private Long keepAliveTime = 20000L;

        /**
         * 空闲连接检查间隔
         *
         * <p>检查和清理空闲连接的时间间隔。
         * <p>单位：毫秒，默认值：30000
         * <p>建议值：30000-60000
         *
         * <b>说明：</b>
         * 连接池会定期检查空闲连接，关闭超过保活时间的连接，释放系统资源。
         */
        private Long idleConnectionTimeout = 30000L;

        /**
         * 连接存活验证开关
         *
         * <p>是否在使用连接前验证连接的有效性。
         * <p>默认值：true
         *
         * <b>说明：</b>
         * 开启后会在使用连接前检查连接是否仍然有效，避免使用失效连接。
         * 虽然会增加少量开销，但能提高连接的可靠性。
         */
        private Boolean validateAfterInactivity = true;

        /**
         * 连接池统计开关
         *
         * <p>是否启用连接池统计信息收集。
         * <p>默认值：false
         *
         * <b>说明：</b>
         * 开启后可以通过 JMX 或其他方式监控连接池的使用情况，
         * 包括活跃连接数、空闲连接数、连接池利用率等指标。
         */
        private Boolean enableMetrics = false;
    }

    /**
     * SSL 安全配置类
     *
     * <p>用于配置 HTTPS 连接的 SSL/TLS 相关参数。
     * <p>适用于所有支持 SSL 加密的搜索引擎。
     *
     * @author richie696
     * @version 1.0
     * @since 2025-08-08
     */
    @Data
    public static class SslConfig {

        /**
         * 是否启用SSL（https）
         *
         * <p>控制是否使用 HTTPS 协议进行加密传输。
         * <p>默认值：false
         *
         * <b>启用建议：</b>
         * <ul>
         *   <li>生产环境：建议启用</li>
         *   <li>公网环境：必须启用</li>
         *   <li>内网环境：根据安全需求决定</li>
         * </ul>
         */
        private boolean enabled = false;

        /**
         * SSL证书路径
         *
         * <p>客户端证书文件的完整路径。
         * <p>支持 JKS、PKCS12、PEM 等格式。
         *
         * <b>路径示例：</b>
         * <ul>
         *   <li>Linux：/etc/ssl/certs/client.p12</li>
         *   <li>Windows：C:\certs\client.p12</li>
         *   <li>相对路径：certs/client.p12</li>
         * </ul>
         */
        private String keystorePath;

        /**
         * SSL证书密码
         *
         * <p>用于访问SSL证书文件的密码。
         * <p>如果证书文件没有密码保护，可以留空。
         */
        private String keystorePassword;

        /**
         * SSL证书类型
         *
         * <p>证书文件的格式类型。
         * <p>默认值：PKCS12
         *
         * @see KeystoreType
         */
        private KeystoreType keystoreType = KeystoreType.JKS;

        /**
         * 信任库路径
         *
         * <p>用于验证服务端证书的信任库文件路径。
         * <p>通常用于自签名证书或企业内部 CA。
         */
        private String truststorePath;

        /**
         * 信任库密码
         *
         * <p>用于访问信任库文件的密码。
         */
        private String truststorePassword;

        /**
         * 是否信任所有证书
         *
         * <p>是否跳过服务端证书验证。
         * <p>默认值：false
         *
         * <b>安全警告：</b>
         * <ul>
         *   <li>生产环境：必须设置为 false</li>
         *   <li>测试环境：可以设置为 true</li>
         *   <li>开发环境：可以设置为 true</li>
         * </ul>
         */
        private boolean trustAll = false;

        /**
         * SSL协议版本
         *
         * <p>指定使用的 SSL/TLS 协议版本。
         * <p>默认值：TLSv1.2
         *
         * <b>推荐版本：</b>
         * <ul>
         *   <li>TLSv1.3：最新最安全</li>
         *   <li>TLSv1.2：广泛支持，推荐使用</li>
         *   <li>TLSv1.1：较老版本，不推荐</li>
         * </ul>
         */
        private String protocol = "TLSv1.2";
    }

}

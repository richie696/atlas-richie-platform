package com.richie.component.cache.redis.config.base;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.bean.MultiStringRedisTemplate;
import com.richie.component.cache.redis.enums.RedisTypeEnum;
import com.richie.component.cache.redis.manage.CacheSyncListener;
import com.richie.component.cache.redis.manage.MessageSubscriber;
import com.richie.component.cache.redis.manage.AtlasRedisCacheManager;
import jakarta.annotation.Nonnull;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.DecodeBufferPolicies;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.*;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder;
import static org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration.builder;

/**
 * Redis客户端配置类
 *
 * @author richie696
 * @version 1.1
 * @since 2020/07/02
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({AtlasRedisProperties.class, LettuceExtension.class})
@NoArgsConstructor
public class RedisBaseAutoConfiguration {

    /**
     * Redis消息监听容器
     *
     * @param connectionFactory Redis连接器工厂
     * @param listeners         消息订阅者列表
     * @param cacheSyncListener 缓存同步监听器（可选，用于 L2 键空间通知）
     * @param properties        Redis 配置
     * @return 返回Redis消息监听器容器
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            LettuceConnectionFactory connectionFactory,
            List<MessageSubscriber<?>> listeners,
            @Autowired(required = false) CacheSyncListener cacheSyncListener,
            AtlasRedisProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        listeners.forEach(listener -> container.addMessageListener(listener, listener.getTopic()));

        // 启用键空间通知
        RedisConnection connection = connectionFactory.getConnection();

        if (properties.getEnableL2Caching() && cacheSyncListener != null) {
            try {
                // 尝试设置键空间通知配置
                connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
                log.info("Redis键空间通知配置设置成功: notify-keyspace-events=KEA");
            } catch (Exception e) {
                log.warn("Redis键空间通知配置设置失败，可能是由于Redis服务器不支持CONFIG命令或权限不足: {}", e.getMessage());
                log.warn("L2缓存同步功能将不可用，建议在Redis服务器上手动设置: CONFIG SET notify-keyspace-events KEA");
                // 继续执行，不抛出异常，但L2缓存同步功能将不可用
            }

            // 监听删除事件
            container.addMessageListener(cacheSyncListener, new ChannelTopic("__keyevent@%d__:del".formatted(properties.getDatabase())));
            // 监听设置事件
            container.addMessageListener(cacheSyncListener, new ChannelTopic("__keyevent@%d__:set".formatted(properties.getDatabase())));
            // 监听过期设置事件
            container.addMessageListener(cacheSyncListener, new ChannelTopic("__keyevent@%d__:expire".formatted(properties.getDatabase())));
            // 监听KEY过期事件
            container.addMessageListener(cacheSyncListener, new ChannelTopic("__keyevent@%d__:expired".formatted(properties.getDatabase())));
            // 监听异步删除事件
            container.addMessageListener(cacheSyncListener, new ChannelTopic("__keyevent@%d__:unlink".formatted(properties.getDatabase())));
        }

        return container;
    }

    /**
     * 获取Redis模板工具类实例的方法
     *
     * @param redisProperties      Redis配置属性实例
     * @param redisKeySerializer   Redis键序列化器实例
     * @param redisValueSerializer Redis值序列化器实例
     * @return 返回Redis模板工具类实例
     */
    @Bean("redisTemplate")
    public MultiRedisTemplate<Object> redisTemplate(AtlasRedisProperties redisProperties, StringRedisSerializer redisKeySerializer, @Qualifier("redisSerializer") GenericJacksonJsonRedisSerializer redisValueSerializer) {
        return createRedisTemplate(redisProperties, redisKeySerializer, redisValueSerializer, true);
    }

    /**
     * 获取Redis模板工具类实例的方法
     *
     * @param redisProperties      Redis配置属性实例
     * @param redisKeySerializer   Redis键序列化器实例
     * @param redisValueSerializer Redis值序列化器实例
     * @return 返回Redis模板工具类实例
     */
    @Bean("jsonTemplate")
    public MultiRedisTemplate<Object> jsonTemplate(AtlasRedisProperties redisProperties, StringRedisSerializer redisKeySerializer, @Qualifier("jsonSerializer") JacksonJsonRedisSerializer<Object> redisValueSerializer) {
        return createRedisTemplate(redisProperties, redisKeySerializer, redisValueSerializer, true);
    }

    /**
     * 获取字符串类型Redis模板工具类实例的方法
     *
     * @param redisTemplate Redis模板工具类实例
     * @return 返回字符串类型Redis模板工具类实例
     */
    @Bean
    public MultiStringRedisTemplate stringRedisTemplate(MultiRedisTemplate<Object> redisTemplate) {
        MultiStringRedisTemplate stringRedisTemplate = new MultiStringRedisTemplate(redisTemplate.getRequiredConnectionFactory());
        Map<String, StringRedisTemplate> slaveMap = redisTemplate.getSlaveTemplateMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            MultiRedisTemplate<Object> value = entry.getValue();
            return new StringRedisTemplate(value.getRequiredConnectionFactory());
        }));
        stringRedisTemplate.setSlaveTemplateMap(slaveMap);
        return stringRedisTemplate;
    }


    private MultiRedisTemplate<Object> createRedisTemplate(AtlasRedisProperties redisProperties, StringRedisSerializer redisKeySerializer, RedisSerializer<Object> redisValueSerializer, boolean isMaster) {
        LettuceConnectionFactory connectionFactory;
        if (isMaster) {
            connectionFactory = lettuceConnectionFactory(redisProperties);
        } else {
            connectionFactory = slaveConnectionFactory(redisProperties);
        }

        MultiRedisTemplate<Object> redisTemplate = new MultiRedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        // 指定序列化器
        redisTemplate.setKeySerializer(redisKeySerializer);
        redisTemplate.setHashKeySerializer(redisKeySerializer);

        redisTemplate.setValueSerializer(redisValueSerializer);
        redisTemplate.setHashValueSerializer(redisValueSerializer);

        // 调用其他初始化逻辑
        redisTemplate.afterPropertiesSet();

        // 设置redis事务支持（不能开启，否则Redis分布式锁上锁时会返回null，导致获取锁失败）
//        redisTemplate.setEnableTransactionSupport(true);
        if (redisProperties.getSlaves() != null) {
            Map<String, MultiRedisTemplate<Object>> map = redisProperties.getSlaves().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                AtlasRedisProperties value = entry.getValue();
                return createRedisTemplate(value, redisKeySerializer, redisValueSerializer, false);
            }));
            redisTemplate.setSlaveTemplateMap(map);
        }
        return redisTemplate;
    }

    /**
     * 获取Redis缓存管理器实例的方法
     *
     * @param redisConnectionFactory     Redis连接工厂实例
     * @param redisSerializer            Redis序列化器实例
     * @param initialCacheConfigurations Redis缓存配置对象
     * @return 返回Redis缓存管理器实例
     */
    @Bean
    public RedisCacheManager cacheManager(LettuceConnectionFactory redisConnectionFactory, RedisSerializer<Object> redisSerializer, Map<String, RedisCacheConfiguration> initialCacheConfigurations) {
        RedisSerializer<String> serializer = new StringRedisSerializer();
        RedisCacheConfiguration configuration = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofDays(1L))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))
                .disableCachingNullValues();
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);
        return new AtlasRedisCacheManager(cacheWriter, configuration, initialCacheConfigurations);
    }

    /**
     * 初始化自定义Redis Key过期时间配置的方法
     *
     * @param redisSerializer Redis序列化器实例
     * @return 返回自定义过期配置时长
     */
    @Bean
    public Map<String, RedisCacheConfiguration> initialCacheConfigurations(@Qualifier("redisSerializer") RedisSerializer<Object> redisSerializer) {
        return Map.of("allowAccess", getRedisCacheConfigurationWithTtl(Duration.ofHours(1), redisSerializer));
    }

    /**
     * 根据指定过期时长获取Redis缓存配置对象的方法
     *
     * @param duration       指定的过期时长
     * @param redisSerializer Redis序列化器实例
     * @return 返回Redis缓存配置对象
     */
    private RedisCacheConfiguration getRedisCacheConfigurationWithTtl(Duration duration, RedisSerializer<Object> redisSerializer) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig();
        return configuration.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer)).entryTtl(duration);
    }

    /**
     * 获取Redis默认全局缓存配置对象的方法
     *
     * @param redisSerializer Redis序列化器实例
     * @return 返回Redis缓存配置对象
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(@Qualifier("redisSerializer") RedisSerializer<Object> redisSerializer) {
        return getRedisCacheConfigurationWithTtl(Duration.ofDays(1), redisSerializer);
    }

    /**
     * 键序列化器
     *
     * @return 返回键序列化器
     */
    @Bean
    public StringRedisSerializer stringRedisSerializer() {
        return new StringRedisSerializer();
    }

    /**
     * 值序列化器
     *
     * @return 返回值序列化器
     */
    @Bean("jsonSerializer")
    public JacksonJsonRedisSerializer<Object> jsonValueSerializer() {
        ObjectMapper objectMapper = JsonUtils.getInstance().cloneMapper().build();
        // 该参数会将对象类型的全限定名称序列化到字符串中，如果跨项目执行的时候会因为类型路径不同导致反序列化失败
//        if (redisProperties.isPolymorphicTypeValidator()) {
//            objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
//        }
        return new JacksonJsonRedisSerializer<>(objectMapper, Object.class);
    }

    /**
     * 值序列化器
     *
     * @return 返回值序列化器
     */
    @Bean("redisSerializer")
    public GenericJacksonJsonRedisSerializer redisValueSerializer() {
        // 使用 BasicPolymorphicTypeValidator 允许所有类型（Redis 序列化场景需要）
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        ObjectMapper objectMapper = JsonUtils.getInstance().cloneMapper()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();
        return new GenericJacksonJsonRedisSerializer(objectMapper);
    }

    /**
     * 获取 Lettuce 连接工厂实例的方法
     *
     * @param redisProperties redis配置文件对象
     * @return 返回 Lettuce 连接工厂实例
     */
    @Bean
    @Primary
    public LettuceConnectionFactory lettuceConnectionFactory(AtlasRedisProperties redisProperties) {
        return new LettuceConnectionFactory(redisConfiguration(redisProperties), lettuceClientConfiguration(redisProperties));
    }

    private LettuceConnectionFactory slaveConnectionFactory(AtlasRedisProperties redisProperties) {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfiguration(redisProperties), lettuceClientConfiguration(redisProperties));
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    /**
     * 获取 Redis 配置文件对象的方法
     *
     * @param redisProperties redis配置数据
     * @return 返回 Redis 配置文件对象的方法
     */
    public RedisConfiguration redisConfiguration(AtlasRedisProperties redisProperties) {
        return switch (redisProperties.getServerType()) {
            case SENTINEL -> getRedisSentinelConfiguration(redisProperties);
            case CLUSTER -> getRedisClusterConfiguration(redisProperties);
            default -> getRedisStandaloneConfiguration(redisProperties);
        };
    }

    @Nonnull
    private static RedisStandaloneConfiguration getRedisStandaloneConfiguration(AtlasRedisProperties redisProperties) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());
        configuration.setDatabase(redisProperties.getDatabase());
        configuration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        return configuration;
    }

    @Nonnull
    private RedisClusterConfiguration getRedisClusterConfiguration(AtlasRedisProperties redisProperties) {
        DataRedisProperties.Cluster cluster = redisProperties.getCluster();
        if (cluster == null) {
            throw new IllegalArgumentException("Redis cluster configuration is required but not provided");
        }
        RedisClusterConfiguration configuration = new RedisClusterConfiguration();
        if (cluster.getNodes() != null && !cluster.getNodes().isEmpty()) {
            cluster.getNodes().forEach(node -> configuration.addClusterNode(createNode(node)));
        } else {
            throw new IllegalArgumentException("Redis cluster nodes are required but not provided");
        }
        if (cluster.getMaxRedirects() != null) {
            configuration.setMaxRedirects(cluster.getMaxRedirects());
        }
        if (redisProperties.getPassword() != null) {
            configuration.setPassword(redisProperties.getPassword());
        }
        return configuration;
    }

    @Nonnull
    private static RedisSentinelConfiguration getRedisSentinelConfiguration(AtlasRedisProperties redisProperties) {
        DataRedisProperties.Sentinel sentinel = redisProperties.getSentinel();
        if (sentinel == null) {
            throw new IllegalArgumentException("Redis sentinel configuration is required but not provided");
        }
        if (sentinel.getMaster() == null || sentinel.getMaster().isEmpty()) {
            throw new IllegalArgumentException("Redis sentinel master name is required but not provided");
        }
        if (sentinel.getNodes() == null || sentinel.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Redis sentinel nodes are required but not provided");
        }
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration(
                sentinel.getMaster(),
                Set.of(sentinel.getNodes().toArray(new String[0]))
        );
        configuration.setDatabase(redisProperties.getDatabase());
        if (redisProperties.getPassword() != null) {
            configuration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        if (sentinel.getPassword() != null) {
            configuration.setSentinelPassword(RedisPassword.of(sentinel.getPassword()));
        }
        return configuration;
    }

    private RedisNode createNode(String node) {
        return new RedisNode(node.split(":")[0].trim(), Integer.parseInt(node.split(":")[1].trim()));
    }

    /**
     * 获取 Lettuce 客户端配置文件对象的方法
     *
     * @param redisProperties Redis配置文件
     * @return 返回 Redis 配置文件对象的方法
     */
    public LettuceClientConfiguration lettuceClientConfiguration(AtlasRedisProperties redisProperties) {
        var socketOptionsBuilder = SocketOptions.builder();
        if (redisProperties.getConnectTimeout() != null) {
            socketOptionsBuilder.connectTimeout(redisProperties.getConnectTimeout());
        }
        LettuceExtension.EpollKeepAliveProperties keepAlive = redisProperties.getLettuce().getKeepAlive();
        if (keepAlive.isEnabled()) {
            socketOptionsBuilder
                    .keepAlive(true)
                    .tcpNoDelay(true);       // 启用TCP的Nagle算法，减少小数据包的发送延迟，提高响应速度

            // Windows 和 macOS 下跳过不支持的 keep-alive 详细配置和 tcpUserTimeout
            // 注意：tcpUserTimeout 需要原生传输（io_uring 或 epoll），仅在 Linux 上可用
            String osName = System.getProperty("os.name").toLowerCase();
            if (!osName.contains("windows") && !osName.contains("mac")) {
                socketOptionsBuilder
                        .keepAlive(SocketOptions.KeepAliveOptions.builder()
                                .idle(Duration.of(keepAlive.getIdle(), keepAlive.getIdleUnit()))
                                .interval(Duration.of(keepAlive.getInterval(), keepAlive.getIntervalUnit()))
                                .count(keepAlive.getCount()).build())
                        .tcpUserTimeout(SocketOptions.TcpUserTimeoutOptions.builder()
                                .enable()
                                .tcpUserTimeout(Duration.of(keepAlive.getTcpUserTimeout(), keepAlive.getTcpUserTimeoutUnit()))
                                .build());
            }
        }
        LettucePoolingClientConfigurationBuilder configurationBuilder = builder();
        // 创建Lettuce客户端配置对象
        var clientOptionsBuilder = ClientOptions.builder()
                .socketOptions(socketOptionsBuilder.build())                                    // 设置Socket配置
                .autoReconnect(true)                                                            // 自动重连
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)       // 断开连接时拒绝命令
                .pingBeforeActivateConnection(redisProperties.isPingBeforeActivateConnection()) // 在激活连接前发送PING命令
                .suspendReconnectOnProtocolFailure(false)                                       // 在协议错误时不挂起重连
                .requestQueueSize(16384)                                                        // 设置请求队列大小
                .publishOnScheduler(true)                                                       // 在发布消息时使用调度器
                .protocolVersion(redisProperties.getProtocolVersion())                          // 使用最新版本的协议
                .scriptCharset(StandardCharsets.UTF_8);                                         // 指定Lua脚本字符集

        if (redisProperties.getTimeout() != null) {
            // 设置命令执行超时时间（不建议超过100ms）
            clientOptionsBuilder.timeoutOptions(TimeoutOptions.enabled(redisProperties.getTimeout()));
        }

        // 设置内存释放策略
        var lettuceProperties = redisProperties.getLettuce();
        var radioValue = lettuceProperties.getMemoryReleaseRatio();
        if (radioValue == null) {
            radioValue = ClientOptions.DEFAULT_BUFFER_USAGE_RATIO; // 默认值
        }
        if (radioValue < 1) {
            radioValue = 1; // 最小值为1
        } else if (radioValue > 10) {
            radioValue = 10; // 最大值为10
        }
        switch (lettuceProperties.getMemoryReleasePolicy()) {
            case USAGE_RADIO -> clientOptionsBuilder.decodeBufferPolicy(DecodeBufferPolicies.ratio(radioValue));
            case ALWAYS -> clientOptionsBuilder.decodeBufferPolicy(DecodeBufferPolicies.always());
            case ALWAYS_SOME -> clientOptionsBuilder.decodeBufferPolicy(DecodeBufferPolicies.alwaysSome());
        }

        // 设置配置的其他选项
        configurationBuilder.poolConfig(genericObjectPoolConfig(redisProperties))
                .clientOptions(clientOptionsBuilder.build());

        // 设置关闭超时时间（如果配置了）
        configurationBuilder.shutdownTimeout(lettuceProperties.getShutdownTimeout());

        // 设置客户端名称（如果配置了）
        if (redisProperties.getClientName() != null) {
            configurationBuilder.clientName(redisProperties.getClientName());
        }

        if (redisProperties.getTimeout() != null) {
            configurationBuilder.commandTimeout(redisProperties.getTimeout());
        }

        // 设置SSL连接选项
        if (redisProperties.getSsl().isEnabled()) {
            // 启用SSL连接，并禁用对等验证，客户端将不会检查服务器证书是否由受信任的CA签发，不会验证证书中的主机名是否匹配。
            configurationBuilder.useSsl().disablePeerVerification();
        }

        // 设置主从架构的读取策略
        if (redisProperties.getServerType() != RedisTypeEnum.STANDALONE) {
            configurationBuilder.readFrom(ReadFrom.REPLICA_PREFERRED);  // 优先从从节点读取，如果从节点不可用，则从主节点读取
        }
        return configurationBuilder.build();
    }

    /**
     * 获取对象池配置文件对象的方法
     *
     * @param redisProperties redis配置数据
     * @return 返回对象池实例
     */
    public GenericObjectPoolConfig<StatefulConnection<?, ?>> genericObjectPoolConfig(AtlasRedisProperties redisProperties) {

        // 判断当前连接池类型
        DataRedisProperties.Pool pool;
        if (redisProperties.getClientType() == DataRedisProperties.ClientType.JEDIS) {
            pool = redisProperties.getJedis().getPool();
        } else {
            pool = redisProperties.getLettuce().getPool();
        }

        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        // 创建连接池对象
        poolConfig.setMinIdle(pool.getMinIdle());
        poolConfig.setMaxIdle(pool.getMaxIdle());
        poolConfig.setMaxTotal(pool.getMaxActive());
        poolConfig.setMaxWait(pool.getMaxWait());
        poolConfig.setTimeBetweenEvictionRuns(pool.getTimeBetweenEvictionRuns());
        return poolConfig;
    }

    /**
     * 获取Redisson客户端实例的方法
     *
     * @param properties Redis配置属性实例
     * @return 返回Redisson客户端实例
     */
    @Bean
    public RedissonClient redissonClient(AtlasRedisProperties properties) {
        Config config = new Config();
        String redisScheme = properties.getSsl().isEnabled() ? "rediss://" : "redis://";
        if (StringUtils.hasText(properties.getPassword())) {
            config.setPassword(properties.getPassword());
        }
        if (StringUtils.hasText(properties.getUsername())) {
            config.setUsername(properties.getUsername());
        }
        switch (properties.getServerType()) {
            case STANDALONE:
                var singleServerConfig = config.useSingleServer()
                        .setAddress(resolveSingleAddress(properties, redisScheme))
                        .setDatabase(properties.getDatabase());

                if (StringUtils.hasText(properties.getClientName())) {
                    singleServerConfig.setClientName(properties.getClientName());
                }
                break;
            case CLUSTER:
                DataRedisProperties.Cluster cluster = properties.getCluster();
                if (cluster == null || cluster.getNodes() == null || cluster.getNodes().isEmpty()) {
                    throw new IllegalArgumentException("Redis cluster configuration and nodes are required but not provided");
                }
                var clusterConfig = config.useClusterServers()
                        .addNodeAddress(cluster.getNodes().stream()
                                .map(node -> normalizeRedisAddress(node, redisScheme))
                                .toArray(String[]::new));
                if (StringUtils.hasText(properties.getClientName())) {
                    clusterConfig.setClientName(properties.getClientName());
                }
                break;
            case SENTINEL:
                DataRedisProperties.Sentinel sentinel = properties.getSentinel();
                if (sentinel == null) {
                    throw new IllegalArgumentException("Redis sentinel configuration is required but not provided");
                }
                if (sentinel.getMaster() == null || sentinel.getMaster().isEmpty()) {
                    throw new IllegalArgumentException("Redis sentinel master name is required but not provided");
                }
                if (sentinel.getNodes() == null || sentinel.getNodes().isEmpty()) {
                    throw new IllegalArgumentException("Redis sentinel nodes are required but not provided");
                }
                var sentinelConfig = config.useSentinelServers()
                        .setMasterName(sentinel.getMaster())
                        .addSentinelAddress(sentinel.getNodes().stream()
                                .map(node -> normalizeRedisAddress(node, redisScheme))
                                .toArray(String[]::new))
                        .setDatabase(properties.getDatabase());
                if (StringUtils.hasText(sentinel.getPassword())) {
                    sentinelConfig.setSentinelPassword(sentinel.getPassword());
                }
                if (StringUtils.hasText(properties.getClientName())) {
                    sentinelConfig.setClientName(properties.getClientName());
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported Redis server type: " + properties.getServerType());
        }

        return Redisson.create(config);
    }

    private String resolveSingleAddress(AtlasRedisProperties properties, String redisScheme) {
        if (StringUtils.hasText(properties.getUrl())) {
            return normalizeRedisAddress(properties.getUrl(), redisScheme);
        }
        return normalizeRedisAddress("%s:%d".formatted(properties.getHost(), properties.getPort()), redisScheme);
    }

    private String normalizeRedisAddress(String address, String redisScheme) {
        String trimmed = address.trim();
        if (trimmed.startsWith("redis://") || trimmed.startsWith("rediss://")) {
            return trimmed;
        }
        return redisScheme + trimmed;
    }

}

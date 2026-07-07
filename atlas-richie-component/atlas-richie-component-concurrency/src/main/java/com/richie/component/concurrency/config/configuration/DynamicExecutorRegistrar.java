package com.richie.component.concurrency.config.configuration;

import com.richie.component.concurrency.config.properties.PoolProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 动态线程池 BeanDefinition 注册器 —— 在 Spring 最早扩展点
 * ({@link BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry})
 * 从 Environment 主动绑定 {@code platform.concurrency.thread-pools.*} 并注册
 * 每个命名池的 {@link DynamicExecutor} BeanDefinition。
 *
 * <h2>为什么用 BeanDefinitionRegistryPostProcessor 而不是 @Configuration + @Bean</h2>
 * <p>Spring Boot 4.x 改变了 {@code @ConfigurationProperties} 嵌套 {@code Map<String, POJO>}
 * 的绑定语义 —— 即使 value 类型已标注 {@code @ConfigurationProperties},
 * 反射式 Map 绑定在某些条件下仍得到空 Map。</p>
 *
 * <p>同时 Spring 7 在处理 {@code @AutoConfiguration} 时对 {@code @Import} 链
 * (父类 @AutoConfiguration @Import 子类 @Configuration)有特殊行为,可能导致
 * 子类 {@code @Configuration} 不被加载执行。本 Registrar 绕开这两个限制:
 * 既不依赖 Spring 反射式自动绑定(用 Binder 主动绑定),
 * 也不走 {@code @Configuration} 加载链(直接注册 BeanDefinition)。</p>
 *
 * <h2>注入方式</h2>
 * <p>每个池的名称即为 Bean 名称,业务方可通过以下方式注入:</p>
 * <pre>{@code
 * @Resource(name = "order-executor")
 * private DynamicExecutor orderExecutor;
 *
 * @Autowired
 * private Map<String, DynamicExecutor> executors;
 *
 * @Qualifier("notification-executor")
 * @Autowired
 * private DynamicExecutor notificationExecutor;
 * }</pre>
 *
 * <p>所有 {@link DynamicExecutor} 继承自 {@link java.util.concurrent.ThreadPoolExecutor},
 * Spring 容器关闭时会自动调用 {@code shutdown()},无需额外声明 {@code destroyMethod}。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class DynamicExecutorRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutorRegistrar.class);

    /** {@code platform.concurrency.thread-pools} 配置前缀 */
    private static final String THREAD_POOLS_PREFIX = "platform.concurrency.thread-pools";

    /**
     * 高优先级:在 ConcurrencyAutoConfiguration 创建后、其他业务 Bean 实例化之前执行,
     * 确保 {@code @Resource(name="...")} 注入时 BeanFactory 中已存在对应 BeanDefinition。
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private ConfigurableEnvironment environment;

    @Override
    public void setEnvironment(@Nonnull Environment environment) {
        if (environment instanceof ConfigurableEnvironment ce) {
            this.environment = ce;
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry)
            throws BeansException {
        if (environment == null) {
            log.warn("Environment not injected; skipping DynamicExecutor registration");
            return;
        }

        Map<String, PoolProperties> pools = bindThreadPools();
        if (pools.isEmpty()) {
            log.info("No thread-pools configured under [{}]; skipping DynamicExecutor registration",
                    THREAD_POOLS_PREFIX);
            return;
        }

        log.info("Manually binding {} thread pool(s) from Environment: {}", pools.size(), pools.keySet());

        pools.forEach((name, config) -> {
            String prefix = config.getThreadNamePrefix().isEmpty() ? name + "-" : config.getThreadNamePrefix();
            DynamicExecutor executor = new DynamicExecutor(
                    config.getCorePoolSize(), config.getMaximumPoolSize(),
                    config.getKeepAliveTime().toMillis(), TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(config.getQueueCapacity()),
                    new AlgorithmAutoConfiguration.DynamicExecutorThreadFactory(prefix),
                    AlgorithmAutoConfiguration.parseRejectedHandler(config.getRejectedHandler()));

            RootBeanDefinition definition = new RootBeanDefinition(DynamicExecutor.class, () -> executor);
            definition.setDestroyMethodName("shutdown");
            registry.registerBeanDefinition(name, definition);

            log.info("Concurrency dynamic thread pool [{}]: core={}, max={}, keepAlive={}, queue={}, handler={}",
                    name, config.getCorePoolSize(), config.getMaximumPoolSize(),
                    config.getKeepAliveTime(), config.getQueueCapacity(),
                    config.getRejectedHandler());
        });
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        // no-op: BeanDefinition 注册在 postProcessBeanDefinitionRegistry 完成
    }

    /**
     * 通过 {@link Binder} 主动从 {@link Environment} 绑定 {@code platform.concurrency.thread-pools}。
     *
     * @return 命名线程池配置 Map;无配置时返回空 Map(不会返回 null)
     */
    private Map<String, PoolProperties> bindThreadPools() {
        Binder binder = new Binder(ConfigurationPropertySources.get(environment));
        return binder
                .bind(THREAD_POOLS_PREFIX,
                        Bindable.mapOf(String.class, PoolProperties.class))
                .orElseGet(Map::of);
    }

    /**
     * 由 {@code ConcurrencyAutoConfiguration} 注册为 Spring Bean,
     * Spring 自动识别 {@link BeanDefinitionRegistryPostProcessor} 类型并调用
     * {@link #postProcessBeanDefinitionRegistry(BeanDefinitionRegistry)}。
     *
     * <p>本类不需要被业务方直接注入,Spring 不会将其加入普通 Bean 容器,
     * 不污染业务 Bean 命名空间。</p>
     */
}
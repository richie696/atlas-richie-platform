package com.richie.component.web.tomcat.executor;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardThreadExecutor;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Tomcat Executor 运行时更新器（Phase 5）。
 *
 * <p>不依赖任何配置中心。提供两种使用方式：</p>
 * <ol>
 *   <li><b>程序调用</b> — 消费方在 Nacos/Apollo/Spring Cloud Config 监听器中
 *       调用 {@link #refresh()} 触发更新</li>
 *   <li><b>自动刷新</b> — 当 classpath 中存在 Spring Cloud Context 时，
 *       {@code EnvironmentChangeEvent} 触发时自动调用 {@link #refresh()}</li>
 * </ol>
 *
 * <p>可运行时更新的参数（前提 Executor 是 {@link StandardThreadExecutor}）：</p>
 * <ul>
 *   <li>{@code maxThreads} — 最大线程数</li>
 *   <li>{@code minSpareThreads} — 最小空闲线程</li>
 *   <li>{@code maxQueueSize} — 任务队列上限</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public class TomcatThreadPoolUpdater implements EnvironmentAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TomcatThreadPoolUpdater.class);

    private static final String PREFIX = "server.tomcat.threads";

    private final WebServer webServer;
    @Nullable
    private Environment environment;

    public TomcatThreadPoolUpdater(WebServer webServer) {
        this.webServer = webServer;
    }

    @Nullable
    private TomcatWebServer resolveTomcat() {
        if (webServer instanceof TomcatWebServer tws) {
            return tws;
        }
        return null;
    }

    private List<Connector> resolveConnectors() {
        TomcatWebServer tws = resolveTomcat();
        if (tws == null) {
            return List.of();
        }
        List<Connector> result = new ArrayList<>();
        for (org.apache.catalina.Service service : tws.getTomcat().getServer().findServices()) {
            result.addAll(Arrays.asList(service.findConnectors()));
        }
        return result;
    }

    @Override
    public void setEnvironment(@Nullable Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Tomcat executor initialized — {}", executorSummary());
    }

    /**
     * 从当前 {@link Environment} 读取配置并应用到所有 Connector 的 ProtocolHandler executor。
     */
    public synchronized void refresh() {
        for (Connector c : resolveConnectors()) {
            ProtocolHandler ph = c.getProtocolHandler();
            if (!(ph instanceof AbstractProtocol<?> protocol)) {
                continue;
            }
            Executor executor = protocol.getExecutor();
            if (!(executor instanceof StandardThreadExecutor ste)) {
                log.warn("Connector [{}] executor is not StandardThreadExecutor ({}), skip",
                        c.getPort(), executor != null ? executor.getClass().getName() : "null");
                continue;
            }

            int oldMax = ste.getMaxThreads();
            int oldMin = ste.getMinSpareThreads();
            int oldQueue = ste.getMaxQueueSize();

            int newMax = readIntProperty("max", oldMax);
            int newMin = readIntProperty("min-spare", oldMin);
            int newQueue = readIntProperty("max-queue-size", oldQueue);

            boolean changed = false;
            if (newMax != oldMax) {
                ste.setMaxThreads(newMax);
                changed = true;
            }
            if (newMin != oldMin) {
                ste.setMinSpareThreads(newMin);
                changed = true;
            }
            if (newQueue != oldQueue) {
                ste.setMaxQueueSize(newQueue);
                changed = true;
            }

            if (changed) {
                log.info("Tomcat connector [{}] executor refreshed — config: max={} minSpare={} maxQueue={}  |  state: active={} queueSize={}",
                        c.getPort(), ste.getMaxThreads(), ste.getMinSpareThreads(), ste.getMaxQueueSize(),
                        ste.getActiveCount(), ste.getQueueSize());
            } else {
                log.debug("Tomcat connector [{}] executor refresh skipped: no config changes", c.getPort());
            }
        }
    }

    private int readIntProperty(String suffix, int fallback) {
        if (environment == null) return fallback;
        Integer value = environment.getProperty(PREFIX + "." + suffix, Integer.class);
        return value != null ? value : fallback;
    }

    private String executorSummary() {
        StringBuilder sb = new StringBuilder();
        for (Connector c : resolveConnectors()) {
            ProtocolHandler ph = c.getProtocolHandler();
            if (!(ph instanceof AbstractProtocol<?> protocol)) {
                sb.append(String.format("[port=%s:n/a]", c.getPort()));
                continue;
            }
            Executor executor = protocol.getExecutor();
            if (executor == null) {
                sb.append(String.format("[port=%s:none]", c.getPort()));
            } else if (executor instanceof StandardThreadExecutor ste) {
                sb.append(String.format("[port=%s: max=%d minSpare=%d active=%d queueSize=%d]",
                        c.getPort(), ste.getMaxThreads(), ste.getMinSpareThreads(),
                        ste.getActiveCount(), ste.getQueueSize()));
            } else {
                sb.append(String.format("[port=%s: %s]", c.getPort(), executor.getClass().getSimpleName()));
            }
        }
        return sb.toString();
    }
}

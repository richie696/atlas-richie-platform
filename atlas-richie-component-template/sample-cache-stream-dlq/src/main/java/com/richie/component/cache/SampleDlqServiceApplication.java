package com.richie.component.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * 死信队列服务启动类
 *
 * <p>独立的死信队列处理服务，专门负责处理各种死信消息
 *
 * <p>服务特点：
 * <ul>
 *   <li>独立部署，不影响主业务服务</li>
 *   <li>专门处理死信消息，职责单一</li>
 *   <li>支持多种死信队列策略</li>
 *   <li>提供死信消息监控和告警</li>
 * </ul>
 *
 * <p>启动方式：
 * <pre>{@code
 * java -jar dlq-service.jar --spring.profiles.active=dlq-service
 * }</pre>
 *
 * @author richie696
 * @since 2025-12-09
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.richie.component.cache",
        "com.richie.base"
})
public class SampleDlqServiceApplication {

    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext application = SpringApplication.run(SampleDlqServiceApplication.class, args);
        Environment env = application.getEnvironment();
        String appName = env.getProperty("spring.application.name");
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("local.server.port");
        String path = Objects.toString(env.getProperty("server.servlet.context-path"), "").trim();
        log.info("""
                
                ---------------------------------------------------------------
                OS Name\t\t\t\t{}
                Local URL\t\thttp://localhost:{}{}/
                External URL\thttp://{}:{}{}/
                Api Doc URL\t\thttp://{}:{}{}/swagger-ui.html
                Actuator URL\thttp://{}:{}{}/actuator
                
                Application "{}" is running!
                ---------------------------------------------------------------
                """, System.getProperty("os.name"), port, path, ip, port, path, ip, port, path, ip, port, path, appName);
    }
}

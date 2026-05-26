package com.richie.component.mfa;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * MFA 组件示例应用
 *
 * @author richie696
 * @since 2026-01-29
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.richie")
@MapperScan("com.richie.component.mfa.mapper")
public class SampleMfaApplication {

    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext application = SpringApplication.run(SampleMfaApplication.class, args);
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


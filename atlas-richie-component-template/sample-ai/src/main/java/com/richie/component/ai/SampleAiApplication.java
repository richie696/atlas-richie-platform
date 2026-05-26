package com.richie.component.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

@Slf4j
@SpringBootApplication
public class SampleAiApplication {

    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext application = SpringApplication.run(SampleAiApplication.class, args);
        Environment env = application.getEnvironment();
        String appName = env.getProperty("spring.application.name");
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("local.server.port");
        String path = Objects.toString(env.getProperty("server.servlet.context-path"), "").trim();
        log.info("""
                
                ---------------------------------------------------------------
                OS Name				{}
                Local URL		http://localhost:{}{}/
                External URL	http://{}:{}{}/
                Api Doc URL		http://{}:{}{}/swagger-ui.html
                Actuator URL	http://{}:{}{}/actuator
                
                Application "{}" is running!
                ---------------------------------------------------------------
                """, System.getProperty("os.name"), port, path, ip, port, path, ip, port, path, ip, port, path, appName);
    }
}



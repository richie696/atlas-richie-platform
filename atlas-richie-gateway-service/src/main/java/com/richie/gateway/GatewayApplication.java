/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * 网关入口程序启动类
 *
 * @author richie696
 * @version 1.0
 * @since 2021/06/29
 */
@Slf4j
@EnableCaching
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.richie.**.feign"})
@SpringBootApplication(scanBasePackages = {"com.richie"})
public class GatewayApplication {

    /**
     * 网关入口程序启动方法
     *
     * @param args 启动参数
     * @throws UnknownHostException 未知主机异常
     */
    public static void main(String[] args) throws UnknownHostException {
        ConfigurableApplicationContext application = SpringApplication.run(GatewayApplication.class, args);
        Environment env = application.getEnvironment();
        String appName = env.getProperty("spring.application.name");
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("local.server.port");
        String path = Objects.toString(env.getProperty("server.servlet.context-path"), "").trim();
        log.info("---------------------------------------------------------------\n" +
                        "测试\nApplication \"{}\" is running! " +
                        "Access URLs:\n" +
                        "Local URL\t\thttp://localhost:{}{}/\n" +
                        "External URL\thttp://{}:{}{}/\n" +
                        "Api Doc URL\t\thttp://{}:{}{}/swagger-ui.html\n" +
                        "---------------------------------------------------------------\n",
                appName, port, path, ip, port, path, ip, port, path);
    }

}

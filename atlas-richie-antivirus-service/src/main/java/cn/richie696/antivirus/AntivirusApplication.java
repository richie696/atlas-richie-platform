package cn.richie696.antivirus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import cn.richie696.antivirus.config.AntivirusProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Antivirus 服务启动入口。 */
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(AntivirusProperties.class)
@SpringBootApplication(scanBasePackages = "cn.richie696")
public class AntivirusApplication {

    public static void main(String[] args) {
        SpringApplication.run(AntivirusApplication.class, args);
    }
}

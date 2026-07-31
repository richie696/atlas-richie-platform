package cn.richie696.antivirus.grpc;

import cn.richie696.antivirus.config.AntivirusProperties;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 把 gRPC 端口以独立服务名注册到 Nacos，
 * 调用方可通过 {@code platform-antivirus-service-grpc} 这个名字直接解析 gRPC endpoint。
 *
 * <p>REST 端口由 Spring Cloud Alibaba Nacos Discovery 自动注册（{@code platform-antivirus-service}）；
 * 这里只补 gRPC 一份，不影响 REST 实例。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.antivirus.grpc", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AntivirusGrpcNacosRegistrar {

    private final AntivirusProperties antivirusProperties;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    private NamingService namingService;
    private Instance registeredInstance;

    @PostConstruct
    public void register() {
        try {
            Map<String, String> props = new HashMap<>();
            props.put(PropertyKeyConst.SERVER_ADDR, nacosDiscoveryProperties.getServerAddr());
            props.put(PropertyKeyConst.NAMESPACE, nacosDiscoveryProperties.getNamespace());
            if (nacosDiscoveryProperties.getUsername() != null) {
                props.put(PropertyKeyConst.USERNAME, nacosDiscoveryProperties.getUsername());
            }
            if (nacosDiscoveryProperties.getPassword() != null) {
                props.put(PropertyKeyConst.PASSWORD, nacosDiscoveryProperties.getPassword());
            }
            this.namingService = NacosFactory.createNamingService(toProperties(props));

            Instance instance = buildInstance();
            namingService.registerInstance(instance.getServiceName(),
                    nacosDiscoveryProperties.getGroup(), instance);
            this.registeredInstance = instance;
            log.info("gRPC instance registered to Nacos: {} {}:{} metadata={}",
                    instance.getServiceName(), instance.getIp(), instance.getPort(), instance.getMetadata());
        } catch (NacosException e) {
            log.warn("Failed to register gRPC instance to Nacos: code={} msg={}",
                    e.getErrCode(), e.getErrMsg());
        }
    }

    private Properties toProperties(Map<String, String> map) {
        Properties props = new Properties();
        map.forEach(props::setProperty);
        return props;
    }

    private Instance buildInstance() {
        Instance instance = new Instance();
        instance.setIp(resolveLocalIp());
        instance.setPort(antivirusProperties.getGrpc().getPort());
        instance.setServiceName(antivirusProperties.getGrpc().getNacosServiceName());
        instance.setHealthy(true);
        instance.setWeight(1.0);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("protocol", "grpc");
        metadata.put("grpc-port", String.valueOf(antivirusProperties.getGrpc().getPort()));
        metadata.put("pod", System.getenv().getOrDefault("HOSTNAME", "antivirus-local"));
        instance.setMetadata(metadata);
        return instance;
    }

    private String resolveLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Unable to resolve local IP, falling back to 127.0.0.1", e);
            return "127.0.0.1";
        }
    }

    @PreDestroy
    public void unregister() {
        if (namingService == null || registeredInstance == null) {
            return;
        }
        try {
            namingService.deregisterInstance(registeredInstance.getServiceName(),
                    nacosDiscoveryProperties.getGroup(), registeredInstance);
            log.info("gRPC instance deregistered from Nacos: {}", registeredInstance);
        } catch (NacosException e) {
            log.warn("Failed to deregister gRPC instance: {}", e.getErrMsg());
        }
    }
}
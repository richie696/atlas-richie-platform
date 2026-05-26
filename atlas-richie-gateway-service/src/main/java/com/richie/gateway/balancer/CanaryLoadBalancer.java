package com.richie.gateway.balancer;

import com.richie.gateway.config.GatewayConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.richie.contract.constant.GlobalConstants.*;


/**
 * 金丝雀发布版本负载均衡器（轮训规则）
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-09 15:14:38
 */
@Slf4j
public class CanaryLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final GatewayConfig config;
    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;
    private final AtomicInteger position = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    public CanaryLoadBalancer(GatewayConfig config, ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider, String serviceId) {
        this.config = config;
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        HttpHeaders headers = (HttpHeaders) request.getContext();
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next().map(list -> getInstanceResponse(list, headers));
    }


    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> instances, HttpHeaders headers) {
        // 检查当前服务是否参与灰度
        if (config.getDeploy().isEnable() && !config.getDeploy().isServiceInCanary(serviceId)) {
            // 如果服务不在灰度列表中，只返回正式实例
            List<ServiceInstance> stableInstances = instances.stream()
                    .filter(instance -> {
                        boolean serverCanaryFlag = Boolean.parseBoolean(instance.getMetadata().get(SERVER_CANARY_ENV));
                        return !serverCanaryFlag;
                    })
                    .toList();
            if (stableInstances.isEmpty()) {
                // 如果没有正式实例，返回所有实例（兜底）
                stableInstances = instances;
            }
            if (log.isTraceEnabled()) {
                log.trace("Service {} not in canary list, routing to stable instances only", serviceId);
            }
            int pos = Math.abs(this.position.incrementAndGet());
            ServiceInstance instance = stableInstances.get(pos % stableInstances.size());
            return new DefaultResponse(instance);
        }

        List<ServiceInstance> serviceInstances = instances.stream()
                .filter(instance -> {
                    boolean serverCanaryFlag = Boolean.parseBoolean(instance.getMetadata().get(SERVER_CANARY_ENV));
                    String serverCategory = instance.getMetadata().get(SERVER_CANARY_CATEGORY);
                    
                    // 如果灰度未启用，只返回非灰度实例
                    if (!config.getDeploy().isEnable()) {
                        return !serverCanaryFlag;
                    }

                    // 统一使用 ID 模式进行灰度路由
                    // 支持通过自定义字段配置提取任意类型的ID（门店ID、用户ID、版本号、环境标识等）
                    // 从请求头获取灰度ID（由 CanaryIdExtractorFilter 自动提取并设置）
                            String id = headers.getFirst(X_CANARY_ID);

                            if (StringUtils.isNotBlank(id)) {
                        // 检查ID是否在灰度列表中
                        boolean contains = config.getDeploy().getIdList() != null 
                                && config.getDeploy().getIdList().contains(id);
                        
                        // 如果ID不在灰度列表中，路由到非灰度实例
                                if (!contains && !serverCanaryFlag) {
                            return true;
                        }
                        
                        // 如果ID在灰度列表中，路由到灰度实例（canary=true 且 canary-category=ID 或为空）
                        if (contains) {
                            return serverCanaryFlag && (serverCategory == null || "ID".equalsIgnoreCase(serverCategory));
                        }
                    }
                    
                    // 如果没有灰度ID，路由到非灰度实例
                    return !serverCanaryFlag;
                }).toList();
        if (serviceInstances.isEmpty()) {
            if (log.isWarnEnabled()) {
                log.warn("No servers available for service: " + serviceId);
            }
            return new EmptyResponse();
        }
        int pos = Math.abs(this.position.incrementAndGet());
        ServiceInstance instance = serviceInstances.get(pos % serviceInstances.size());
        return new DefaultResponse(instance);
    }
}

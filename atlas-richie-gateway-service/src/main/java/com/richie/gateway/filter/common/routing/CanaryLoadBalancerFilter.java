/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.filter.common.routing;

import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.balancer.CanaryLoadBalancer;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;

@Slf4j
@Component
public class CanaryLoadBalancerFilter extends AbstractBaseFilter {

    private final LoadBalancerClientFactory clientFactory;

    public CanaryLoadBalancerFilter(GatewayConfig config, I18nResolver i18n, LoadBalancerClientFactory clientFactory) {
        super(config, i18n);
        this.clientFactory = clientFactory;
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Response<ServiceInstance> serviceInstanceResponse = exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        if (serviceInstanceResponse == null || !serviceInstanceResponse.hasServer()) {
            return chain.filter(exchange);
        }
        // 获取当前请求头里面的灰度标识
        String serviceId = serviceInstanceResponse.getServer().getServiceId();

        return this.choose(exchange, serviceId).doOnNext((response) -> {
            if (!response.hasServer()) {
                URI url = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
                assert url != null;
                throw NotFoundException.create(true, "Unable to find instance for " + url.getHost());
            } else {
                URI uri = exchange.getRequest().getURI();
                ServiceInstance serviceInstance = response.getServer();
                URI requestUrl = this.reconstructURI(serviceInstance, uri);
                if (log.isTraceEnabled()) {
                    log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
                }
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, requestUrl);
            }
        }).then(chain.filter(exchange));
    }


    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return true;
    }

    private Mono<Response<ServiceInstance>> choose(ServerWebExchange exchange, String serviceId) {
        CanaryLoadBalancer loadBalancer = new CanaryLoadBalancer(config, clientFactory.getLazyProvider(serviceId, ServiceInstanceListSupplier.class), serviceId);
        return loadBalancer.choose(this.createRequest(exchange));
    }

    private Request<HttpHeaders> createRequest(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        return new DefaultRequest<>(headers);
    }

    private URI reconstructURI(ServiceInstance serviceInstance, URI original) {
        return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
    }

    public int getOrder() {
        return FilterOrder.getCanaryLoadBalancerOrder(LOAD_BALANCER_CLIENT_FILTER_ORDER);
    }
}

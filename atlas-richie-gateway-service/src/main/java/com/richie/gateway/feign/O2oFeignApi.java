package com.richie.gateway.feign;

import org.springframework.cloud.openfeign.FeignClient;


@FeignClient(value = "portal-service", path = "/tenant", fallback = O2oFeignApiFallback.class)
public interface O2oFeignApi extends BaseFeignApi {

}

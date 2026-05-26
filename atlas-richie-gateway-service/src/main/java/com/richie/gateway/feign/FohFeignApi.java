package com.richie.gateway.feign;

import org.springframework.cloud.openfeign.FeignClient;


@FeignClient(value = "kds-config-service", path = "/foh/api/sys/auth", fallback = FohFeignApiFallback.class)
public interface FohFeignApi extends BaseFeignApi {

}

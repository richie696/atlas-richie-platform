package com.richie.gateway.feign;

import org.springframework.cloud.openfeign.FeignClient;


@FeignClient(value = "acc-service", path = "/acc/bohNotify", fallback = BohFeignApiFallback.class)
public interface BohFeignApi extends BaseFeignApi {

}

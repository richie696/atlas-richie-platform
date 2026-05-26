package com.richie.component.threadpool.service;

import com.richie.contract.model.ApiResult;
import com.richie.component.threadpool.domain.ApiInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * API服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 11:09:38
 */
public interface ApiService extends IService<ApiInfo> {

    ApiInfo getUniqueApi(Long id);

    ApiResult<Page<ApiInfo>> getApiList(ApiInfo apiInfo);

}

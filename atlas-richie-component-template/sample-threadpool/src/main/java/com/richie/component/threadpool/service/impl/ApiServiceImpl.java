package com.richie.component.threadpool.service.impl;

import com.richie.contract.model.ApiResult;
import com.richie.component.i18n.resolver.I18n;
import com.richie.component.threadpool.domain.ApiInfo;
import com.richie.component.threadpool.mapper.ApiMapper;
import com.richie.component.threadpool.service.ApiService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * API服务接口实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 11:09:24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiServiceImpl extends ServiceImpl<ApiMapper, ApiInfo> implements ApiService {

    @Override
    public ApiInfo getUniqueApi(Long id) {
        return getById(id);
    }

    @Override
    public ApiResult<Page<ApiInfo>> getApiList(ApiInfo apiInfo) {
        var i18nText = I18n.get("MSG_API_INFO", apiInfo.getName(), apiInfo.getRole().getName());
        Page<ApiInfo> page = new Page<>(apiInfo.getCurrentPage(), apiInfo.getPageSize());
        page.setOrders(apiInfo.getOrders());
        return ApiResult.success(i18nText, page(page, new QueryWrapper<>(apiInfo)));
    }
}

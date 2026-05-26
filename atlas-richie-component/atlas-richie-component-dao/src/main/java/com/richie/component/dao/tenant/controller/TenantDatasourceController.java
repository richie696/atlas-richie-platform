package com.richie.component.dao.tenant.controller;

import com.richie.component.dao.config.DaoConstant;
import com.richie.component.dao.tenant.service.TenantDatasourceService;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户数据源管理接口：提供刷新数据源等运维能力（仅在启用多租户时生效）。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-23
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/tenant")
@ConditionalOnClass(DynamicDataSourceProperties.class)
@ConditionalOnProperty(prefix = DaoConstant.DAO_PREFIX, name = DaoConstant.DAO_ENABLE_TENANT_PREFIX, havingValue = "true")
public class TenantDatasourceController {

    /** 租户数据源服务 */
    private final TenantDatasourceService tenantDatasourceService;

    @GetMapping("/refreshDatasource")
    public void refreshDatasource() {
        tenantDatasourceService.refreshDatasource();
    }

}

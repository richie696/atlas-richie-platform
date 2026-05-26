package com.richie.component.dao.tenant.service;

import com.richie.component.dao.tenant.AddTenantCode;
import com.richie.component.dao.tenant.domain.TenantDatasource;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 租户数据源表 Service：提供租户数据源配置的 CRUD、按策略分配租户到数据源、刷新数据源等能力。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-09-23
 */
public interface TenantDatasourceService extends IService<TenantDatasource> {

    /**
     * 获得所有租户和数据源关系列表
     * @return 租户和数据源关系列表
     */
    List<TenantDatasource> getAllTenantDatasourceList();

    /**
     * 新增租户，选择租户最少的数据源，并且通知所有服务刷新数据源
     * @param tenantCodeList tenantCode列表
     * @return 租户和数据源关系列表，用于通知服务刷新数据源，不成功就返回空列表
     */
    List<AddTenantCode> addTenantUseMinDatasource(List<Long> tenantCodeList);

    /**
     * 新增租户，指定数据源，并且通知所有服务刷新数据源
     * @param addTenantCodeList 租户和数据源关系列表
     * @return 是否添加成功
     */
    boolean addTenantUseSpecificDatasource(List<AddTenantCode> addTenantCodeList);

    /**
     * 刷新服务所持有的所有数据源
     * @return 是否成功
     */
    boolean refreshDatasource();

}

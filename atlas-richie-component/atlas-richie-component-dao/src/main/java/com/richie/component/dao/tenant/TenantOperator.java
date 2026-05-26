package com.richie.component.dao.tenant;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.dao.tenant.service.TenantDatasourceService;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户操作接口：添加租户（轮询分配数据源）、监听添加租户消息并刷新本地数据源等。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-05
 */
public interface TenantOperator {

    /**
     * 添加租户，使用轮询的方式添加数据源
     *
     * @param tenantCodeList 租户编码列表
     */
    void addTenantRoundRobin(List<Long> tenantCodeList);

    /**
     * 添加租户，使用指定的数据源添加数据源
     *
     * @param addTenantCodeList 租户编码列表
     */
    void addTenant(List<AddTenantCode> addTenantCodeList);

    /**
     * 添加租户缓存
     * @param addTenantCodeList 租户编码列表
     */
    void listenAddTenant(List<AddTenantCode> addTenantCodeList);

    /**
     * 租户操作实现类
     *
     * @author yuy
     * @version 1.0
     * @since 2023-10-12 16:54:16
     */
    @Slf4j
    @RequiredArgsConstructor
    class TenantOperatorImpl implements TenantOperator {

        /** 多租户配置 */
        private final TenantProperties tenantProperties;

        /** Redisson 客户端，用于消息发布 */
        private final RedissonClient redissonClient;

        /** 租户数据源服务 */
        private final TenantDatasourceService tenantDatasourceService;

        /** 租户数据源属性缓存 */
        private final TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache;

        /**
         * 添加租户，使用轮询的方式添加数据源
         *
         * @param tenantCodeList 租户编码列表
         */
        @Override
        public void addTenantRoundRobin(List<Long> tenantCodeList) {
            //先查出来所有的数据源，然后更新租户
            List<AddTenantCode> addTenantCodeList = tenantDatasourceService.addTenantUseMinDatasource(tenantCodeList);
            if (CollectionUtils.isNotEmpty(addTenantCodeList)) {
                publishAsyncAddTenantCodeList(addTenantCodeList);
            }
        }

        /**
         * 提供给服务调用，添加租户，添加数据源，并且发送消息通知所有服务刷新数据源
         *
         * @param addTenantCodeList 租户编码列表
         */
        @Override
        public void addTenant(List<AddTenantCode> addTenantCodeList) {
            //先查出来所有的数据源，然后更新租户
            boolean b = tenantDatasourceService.addTenantUseSpecificDatasource(addTenantCodeList);
            if (b) {
                publishAsyncAddTenantCodeList(addTenantCodeList);
            }
        }

        private void publishAsyncAddTenantCodeList(List<AddTenantCode> addTenantCodeList) {
            //发送消息给所有订阅服务添加数据源消息
            RFuture<Long> future = redissonClient.getTopic(tenantProperties.getAddTenantTopic()).publishAsync(addTenantCodeList);
            try {
                log.info("sourceAddTenant future:{}", future.get());
            } catch (Exception e) {
                log.error("sourceAddTenant error", e);
            }
        }

        /**
         * 添加租户缓存
         *
         * @param addTenantCodeList 租户编码列表
         */
        @Override
        public void listenAddTenant(List<AddTenantCode> addTenantCodeList) {
            log.info("tenantDsCache before:{},addTenantCodeList:{}", JsonUtils.getInstance().serialize(tenantDataSourcePropertyMapCache.getTenantDsCache()), JsonUtils.getInstance().serialize(addTenantCodeList));
            for (AddTenantCode addTenantCode : addTenantCodeList) {
                for (Long aLong : addTenantCode.getTenantDatasourceIdList()) {
                    String key = TenantConstant.DATASOURCE_PREFIX + aLong;
                    DataSourceProperty dataSourceProperty = tenantDataSourcePropertyMapCache.getDsCache().get(key);
                    if (dataSourceProperty != null) {
                        tenantDataSourcePropertyMapCache.getTenantDsCache().computeIfAbsent(addTenantCode.getTenantCode(), k -> new ConcurrentHashMap<>()).put(key, dataSourceProperty);
                    }
                }
            }
            log.info("tenantDsCache after:{}", JsonUtils.getInstance().serialize(tenantDataSourcePropertyMapCache.getTenantDsCache()));
        }

    }

}

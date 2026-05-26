package com.richie.component.dao.tenant;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.toolkit.CryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;

/**
 * 数据源操作接口：列出数据源、动态添加数据源、刷新数据源（多租户场景下由动态数据源使用）。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
public interface DatasourceOperator {

    /**
     * 列出当前所有数据源名称
     *
     * @return 数据源名称集合
     */
    Set<String> listDatasource();

    /**
     * 添加数据源
     *
     * @param dataSourceProperty 数据源配置
     * @return 添加后所有数据源名称集合
     */
    Set<String> addDatasource(DataSourceProperty dataSourceProperty);

    /**
     * 刷新数据源
     */
    void refreshDatasource();


    @Slf4j
    @RequiredArgsConstructor
    class DatasourceOperatorImpl implements DatasourceOperator {

        /** 动态路由数据源 */
        private final DataSource dataSource;

        /** 数据源创建器 */
        private final DefaultDataSourceCreator defaultDataSourceCreator;

        /** Redisson 客户端 */
        private final RedissonClient redissonClient;

        /**
         * 查询当前已注册的数据源名称列表
         *
         * @return 数据源名称集合
         */
        @Override
        public Set<String> listDatasource() {
            DynamicRoutingDataSource ds = (DynamicRoutingDataSource) dataSource;
            return ds.getDataSources().keySet();
        }

        /**
         * 添加数据源并返回当前所有数据源名称
         *
         * @param dataSourceProperty 数据源配置
         * @return 添加后所有数据源名称集合
         */
        @Override
        public Set<String> addDatasource(DataSourceProperty dataSourceProperty) {
            //数据库添加租户

            //添加数据源
            DynamicRoutingDataSource ds = (DynamicRoutingDataSource) dataSource;
            Map<String, DataSource> dataSources = ds.getDataSources();
            if (dataSources.containsKey(dataSourceProperty.getPoolName())) {
                return dataSources.keySet();
            }
            try {
                dataSourceProperty.setPassword("ENC(" + CryptoUtils.encrypt(dataSourceProperty.getPassword()) + ")");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            DataSource dataSource = defaultDataSourceCreator.createDataSource(dataSourceProperty);
            ds.addDataSource(dataSourceProperty.getPoolName(), dataSource);
            log.info("addDataSource {} success!", dataSourceProperty.getPoolName());

            ////发送消息给所有订阅服务添加数据源消息
            //RFuture<Long> future = redissonClient.getTopic("testListener").publishAsync("hello");

            return dataSources.keySet();
        }


        @Override
        public void refreshDatasource() {

        }
    }

}

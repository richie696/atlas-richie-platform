package com.richie.component.dao.tenant;


import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 租户数据源属性全局缓存：维护数据源 key 与 DataSourceProperty 的映射，以及按租户、@DS 解析当前数据源 key。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class TenantDataSourcePropertyMapCache {

    /**
     * 租户缓存
     *
     * 第一层
     * key：租户ID
     * 第二层
     * key：数据源ID
     * value：数据源信息
     * {
     *  1 : {
     *    ds1: DataSourceProperty1,
     *    ds2: DataSourceProperty2
     *  },
     *  2 : {
     *    ds3: DataSourceProperty3,
     *    ds4: DataSourceProperty4
     *  }
     * }
     */
    private final Map<Long, Map<String, DataSourceProperty>> tenantDsCache = new ConcurrentHashMap<>();

    /**
     * 数据源缓存
     *
     * key：数据源ID
     * value：数据源信息
     * {
     *   ds1: DataSourceProperty1,
     *   ds2: DataSourceProperty2,
     *   ds3: DataSourceProperty3
     * }
     */
    private final Map<String, DataSourceProperty> dsCache = new ConcurrentHashMap<>();

    /** 多租户配置 */
    private final TenantProperties tenantProperties;

    /**
     * 获取当前上下文对应的数据源 key（默认数据源）
     *
     * @return 数据源 key
     */
    public String getDatasourceKey() {
        return getDatasourceKey(null);
    }

    /**
     * 为了兼容 @DS("master") 等注解，先按租户取数据源，再按注解 key 取具体数据源
     *
     * @param annotationKey @DS 注解中的 key（如 master、slave 等），为空时使用默认数据源
     * @return 数据源 key
     */
    public String getDatasourceKey(String annotationKey) {
        //annotationKey为空的情况下，使用默认的数据源
        if (StringUtils.isBlank(annotationKey)) {
            annotationKey = TenantConstant.MASTER_DS_NAME;
        }

        Map<String, DataSourceProperty> dataSourcePropertyMap = Collections.emptyMap();
        // 数据源id和数据源信息的映射
        if (TenantCodeContextHolder.getTenantCode() != null) {
            dataSourcePropertyMap = this.tenantDsCache.get(TenantCodeContextHolder.getTenantCode());
        }

        //找不到租户的情况下，使用默认的数据源
        if (MapUtils.isEmpty(dataSourcePropertyMap)) {
            return getDsNameWithoutTenant(annotationKey);
        } else {
            //转换为数据源poolName和数据源id的映射
            Map<String, String> stringLongMap = dataSourcePropertyMap.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue().getPoolName(), Map.Entry::getKey));
            String key = stringLongMap.get(annotationKey);
            log.info("datasource key：{}", key);
            return key;
        }
    }

    /**
     * 无租户上下文时，按注解 key 获取数据源 key
     *
     * @param annotationKey @DS 注解内容（如 master）
     * @return 数据源 key，找不到或存在多个且未开启随机时抛异常
     */
    public String getDsNameWithoutTenant(String annotationKey) {
        List<Map.Entry<String, DataSourceProperty>> list = dsCache.entrySet().stream().filter(x -> StringUtils.equals(x.getValue().getPoolName(), annotationKey)).toList();
        if (list.isEmpty()) {
            log.error("master datasource is not exist");
            throw new RuntimeException("master datasource is not exist");
        } else if (list.size() == 1) {
            return list.get(0).getKey();
        } else {
            log.error("master datasource is more than one");
            if (tenantProperties.isUseRandomMaster()) {
                return list.stream().findFirst().map(Map.Entry::getKey).orElse(null);
            }else {
                throw new RuntimeException("master datasource is more than one");
            }
        }
    }

}

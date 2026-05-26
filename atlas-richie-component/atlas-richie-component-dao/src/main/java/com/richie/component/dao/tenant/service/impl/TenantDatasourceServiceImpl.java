package com.richie.component.dao.tenant.service.impl;

import com.richie.context.utils.data.Collection2MapUtils;
import com.richie.component.dao.config.DaoConstant;
import com.richie.component.dao.tenant.AddTenantCode;
import com.richie.component.dao.tenant.TenantConstant;
import com.richie.component.dao.tenant.TenantDataSourcePropertyMapCache;
import com.richie.component.dao.tenant.TenantProperties;
import com.richie.component.dao.tenant.annotation.CommonDataSource;
import com.richie.component.dao.tenant.domain.TenantDatasource;
import com.richie.component.dao.tenant.mapper.TenantDatasourceMapper;
import com.richie.component.dao.tenant.service.TenantDatasourceService;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 租户数据源服务实现：查询/维护租户数据源表、按策略分配租户到数据源、提供刷新数据源等能力。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-09-23
 */
@Service
@RequiredArgsConstructor
@ConditionalOnClass(DynamicDataSourceProperties.class)
@ConditionalOnProperty(prefix = DaoConstant.DAO_PREFIX, name = DaoConstant.DAO_ENABLE_TENANT_PREFIX, havingValue = "true")
public class TenantDatasourceServiceImpl extends ServiceImpl<TenantDatasourceMapper, TenantDatasource> implements TenantDatasourceService {

    /** 当前应用名，用于按服务筛选租户数据源 */
    @Value("${spring.application.name}")
    private String appName;

    /** 租户数据源属性缓存 */
    private final TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache;

    /** 多租户配置 */
    private final TenantProperties tenantProperties;

    @Override
    public List<TenantDatasource> getAllTenantDatasourceList() {
        return ChainWrappers.lambdaQueryChain(baseMapper).eq(TenantDatasource::getServiceName, appName).list();
    }

    @DSTransactional(rollbackFor = Exception.class)
    @CommonDataSource
    @Override
    public List<AddTenantCode> addTenantUseMinDatasource(List<Long> tenantCodeList) {
        //去重
        Set<Long> tenantCodeSet = new HashSet<>(tenantCodeList);

        //数据源id和租户编码的映射
        Map<Long, Set<Long>> dsTenantCodeMap = new HashMap<>();

        //需要更新的数据源
        List<TenantDatasource> tenantDatasourceList = new ArrayList<>();

        //查询出所有的数据源
        List<TenantDatasource> list = ChainWrappers.lambdaQueryChain(baseMapper).list();
        //根据服务名称分组
        Map<String, Collection<TenantDatasource>> serviceNameMap = Collection2MapUtils.collection2MapCollection(list, TenantDatasource::getServiceName);

        for (Map.Entry<String, Collection<TenantDatasource>> entry : serviceNameMap.entrySet()) {
            //根据数据源名称分组
            Map<String, Collection<TenantDatasource>> dsNameMap = Collection2MapUtils.collection2MapCollection(entry.getValue(), TenantDatasource::getDsName);
            for (Map.Entry<String, Collection<TenantDatasource>> collectionEntry : dsNameMap.entrySet()) {
                //如果这个服务下的的已经存在改租户编码，那么就不需要再添加了
                Set<Long> allTenantCodeSet = collectionEntry.getValue().stream().flatMap(x -> Arrays.stream(StringUtils.split(x.getTenantCodes(), ",")).map(y -> Long.parseLong(StringUtils.trim(y)))).collect(Collectors.toSet());
                //差集
                Collection<Long> subtract = CollectionUtils.subtract(tenantCodeSet, allTenantCodeSet);

                if (CollectionUtils.isEmpty(subtract)) {
                    continue;
                }

                //检查tenantCodeList是否已经存在与stringCollectionMap中
                Collection<TenantDatasource> value = collectionEntry.getValue();

                //取数据源tenantCodes最少的那个出来
                TenantDatasource tenantDatasource = value.stream().min((x, y) -> {
                    String tenantCodes = x.getTenantCodes();
                    String tenantCodes1 = y.getTenantCodes();
                    int i = StringUtils.countMatches(tenantCodes, ",");
                    int i1 = StringUtils.countMatches(tenantCodes1, ",");
                    return Integer.compare(i, i1);
                }).orElse(null);

                if (tenantDatasource != null) {
                    tenantDatasourceList.add(tenantDatasource);
                    dsTenantCodeMap.computeIfAbsent(tenantDatasource.getId(), x -> new HashSet<>()).addAll(subtract);
                }

            }
        }

        boolean b = setTenantDatasourceList(tenantDatasourceList, dsTenantCodeMap);
        List<AddTenantCode> addTenantCodeList = Collections.emptyList();
        if (b) {
            //所有的数据源id
            Set<Long> tenantDatasourceIdList = tenantDatasourceList.stream().map(TenantDatasource::getId).collect(Collectors.toSet());
            addTenantCodeList = tenantCodeSet.stream().map(tenantCode -> {
                AddTenantCode addTenantCode = new AddTenantCode();
                addTenantCode.setTenantCode(tenantCode);
                addTenantCode.setTenantDatasourceIdList(tenantDatasourceIdList);
                return addTenantCode;
            }).toList();
        }
        return addTenantCodeList;
    }

    @DSTransactional(rollbackFor = Exception.class)
    @CommonDataSource
    @Override
    public boolean addTenantUseSpecificDatasource(List<AddTenantCode> addTenantCodeList) {
        //数据源id和租户编码的映射
        Map<Long, Set<Long>> dsTenantCodeMap = new HashMap<>();

        //List<AddTenantCode>转换为Map<Long, Set<Long>>
        for (AddTenantCode addTenantCode : CollectionUtils.emptyIfNull(addTenantCodeList)) {
            if (addTenantCode != null) {
                for (Long tenantDatasourceId : CollectionUtils.emptyIfNull(addTenantCode.getTenantDatasourceIdList())) {
                    if (tenantDatasourceId != null) {
                        dsTenantCodeMap.computeIfAbsent(tenantDatasourceId, k -> new HashSet<>()).add(addTenantCode.getTenantCode());
                    }
                }
            }
        }

        //先查出来所有的数据源，然后更新租户
        List<TenantDatasource> tenantDatasourceList = baseMapper.selectByIds(dsTenantCodeMap.keySet());

        return setTenantDatasourceList(tenantDatasourceList, dsTenantCodeMap);
    }

    /**
     * 设置租户数据源
     *
     * @param tenantDatasourceList 所有的数据源，需要获取tenantCodes
     * @param map                  需要更新的数据源id和租户编码的映射
     * @return 是否更新成功
     */
    private boolean setTenantDatasourceList(List<TenantDatasource> tenantDatasourceList, Map<Long, Set<Long>> map) {
        boolean flag = false;
        for (TenantDatasource tenantDatasource : CollectionUtils.emptyIfNull(tenantDatasourceList)) {
            String tenantCodes = tenantDatasource.getTenantCodes();

            Set<Long> longList = map.get(tenantDatasource.getId());
            if (CollectionUtils.isNotEmpty(longList)) {
                //从Long转换为String
                List<String> list = longList.stream().map(String::valueOf).toList();
                //转换为set去重
                String[] split = StringUtils.split(tenantCodes, ",");
                Set<String> mySet = new LinkedHashSet<>(Arrays.asList(split));
                mySet.addAll(list);
                String join = StringUtils.join(mySet, ",");
                //不同才更新
                if (!StringUtils.equals(tenantCodes, join)) {
                    try {
                        int i = baseMapper.updateById(TenantDatasource.builder().id(tenantDatasource.getId()).tenantCodes(join).build());
                        if (i > 0) {
                            flag = true;
                        }
                    } catch (Exception e) {
                        log.error("更新租户数据源失败", e);
                    }
                }
            }
        }
        return flag;

    }

    @CommonDataSource
    @Override
    public boolean refreshDatasource() {
        List<TenantDatasource> list = ChainWrappers.lambdaQueryChain(baseMapper).eq(TenantDatasource::getServiceName, appName).list();

        Map<String, DataSourceProperty> map = new HashMap<>();

        for (TenantDatasource tenantDatasource : list) {
            DataSourceProperty property = new DataSourceProperty();
            String key = TenantConstant.DATASOURCE_PREFIX + tenantDatasource.getId();
            property.setUsername(tenantDatasource.getDbUsername());
            property.setPassword(tenantDatasource.getDbPassword());
            String dbUrlTemplate = tenantProperties.getDbUrlTemplate();
            String url = dbUrlTemplate.replace("%s", tenantDatasource.getDbParam());
            property.setUrl(url);
            property.setPoolName(tenantDatasource.getDsName());
            Arrays.stream(StringUtils.split(tenantDatasource.getTenantCodes(), ",")).forEach(x -> tenantDataSourcePropertyMapCache.getTenantDsCache().computeIfAbsent(Long.parseLong(x), y -> new ConcurrentHashMap<>()).putIfAbsent(key, property));
            map.put(key, property);
        }

        tenantDataSourcePropertyMapCache.getDsCache().putAll(map);

        return false;
    }
}







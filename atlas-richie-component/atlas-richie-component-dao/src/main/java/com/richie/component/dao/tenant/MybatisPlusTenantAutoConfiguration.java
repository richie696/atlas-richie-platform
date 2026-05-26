package com.richie.component.dao.tenant;

import com.richie.component.dao.config.DaoAutoConfiguration;
import com.richie.component.dao.config.DaoConstant;
import com.richie.component.dao.tenant.aspect.CommonDataSourceAspect;
import com.richie.component.dao.tenant.service.TenantDatasourceService;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.provider.AbstractJdbcDataSourceProvider;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.provider.YmlDynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAopConfiguration;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourcePropertiesCustomizer;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDatasourceAopProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多租户自动装配：从租户表加载数据源、注册租户行级隔离插件、覆盖 dynamic-datasource 注解 AOP、提供数据源/租户操作 Bean。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
@RequiredArgsConstructor
@ConditionalOnClass({DynamicDataSourceProperties.class, DefaultDataSourceCreator.class})
@AutoConfigureAfter(DaoAutoConfiguration.class)
@AutoConfigureBefore(DynamicDataSourceAopConfiguration.class)
@MapperScan("com.richie.component.dao.tenant.mapper")
@ConditionalOnProperty(prefix = DaoConstant.DAO_PREFIX, name = DaoConstant.DAO_ENABLE_TENANT_PREFIX, havingValue = "true")
public class MybatisPlusTenantAutoConfiguration extends MybatisConfiguration {

    /** 多租户配置 */
    private final TenantProperties tenantProperties;

    /** 动态数据源创建器 */
    private final DefaultDataSourceCreator defaultDataSourceCreator;

    /** Redisson 客户端，用于分布式能力 */
    private final RedissonClient redissonClient;

    /** 当前应用名，用于从租户表筛选数据源 */
    @Value("${spring.application.name}")
    private String appName;

    /**
     * 动态数据源 JDBC 提供者（从租户表加载数据源）
     *
     * @param tenantDataSourcePropertyMapCache 租户数据源属性缓存
     * @param dsProperties                     动态数据源配置
     * @return 数据源提供者
     */
    @Bean
    public DynamicDataSourceProvider jdbcDynamicDataSourceProvider(TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache, DynamicDataSourceProperties dsProperties) {
        Map<String, DataSourceProperty> datasource = dsProperties.getDatasource();
        DataSourceProperty dataSourceProperty = datasource.get(dsProperties.getPrimary());
        if (dataSourceProperty != null) {
            return new AbstractJdbcDataSourceProvider(defaultDataSourceCreator, dataSourceProperty.getDriverClassName(), dataSourceProperty.getUrl(), dataSourceProperty.getUsername(), dataSourceProperty.getPassword()) {
                @Override
                protected Map<String, DataSourceProperty> executeStmt(Statement statement) {
                    Map<String, DataSourceProperty> map = new HashMap<>();
                    ResultSet rs = null;
                    try {
                        String sql = "select * from %s where %s='%s'";
                        rs = statement.executeQuery(String.format(sql, tenantProperties.getTenantTableName(), TenantConstant.SERVICE_NAME_COLUMN, appName));
                        while (rs.next()) {
                            try {
                                DataSourceProperty property = new DataSourceProperty();
                                String key = TenantConstant.DATASOURCE_PREFIX + rs.getString(TenantConstant.ID_COLUMN);
                                property.setUsername(rs.getString(TenantConstant.DB_USERNAME_COLUMN));
                                property.setPassword(rs.getString(TenantConstant.DB_PASSWORD_COLUMN));
                                String dbUrlTemplate = tenantProperties.getDbUrlTemplate();
                                String dbParam = rs.getString(TenantConstant.DB_PARAM_COLUMN);
                                String url = dbUrlTemplate.replace("%s", dbParam);
                                property.setUrl(url);
                                //boolean masterFlag = rs.getBoolean(tenantProperties.getMasterFlag());
                                property.setPoolName(rs.getString(TenantConstant.DS_NAME_COLUMN));
                                Arrays.stream(StringUtils.split(rs.getString(TenantConstant.TENANT_CODES_COLUMN), ",")).forEach(x -> {
                                    long tenantCode = Long.parseLong(x);
                                    tenantDataSourcePropertyMapCache.getTenantDsCache().computeIfAbsent(tenantCode, y -> new ConcurrentHashMap<>()).putIfAbsent(key, property);
                                });
                                map.put(key, property);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        if (map.isEmpty()) {
                            map.put(dsProperties.getPrimary(), dataSourceProperty);
                        } else {
                            tenantDataSourcePropertyMapCache.getDsCache().putAll(map);
                        }
                    } catch (SQLException e) {
                        log.error("jdbcDynamicDataSourceProvider err", e);
                    } finally {
                        try {
                            if (rs != null) {
                                rs.close();
                            }
                        } catch (SQLException e) {
                            log.error("jdbcDynamicDataSourceProvider err1", e);
                        }
                        try {
                            statement.close();
                        } catch (SQLException e) {
                            log.error("jdbcDynamicDataSourceProvider err2", e);
                        }
                    }
                    return map;
                }
            };
        }
        return new YmlDynamicDataSourceProvider(defaultDataSourceCreator, datasource);
    }

    /**
     * 租户行级隔离插件
     *
     * @return 租户行级内部拦截器
     */
    @Bean
    public TenantLineInnerInterceptor addInnerInterceptor() {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {

            /**
             * 第一个执行
             * @param tableName 表名
             * @return false:需要解析并拼接多租户条件，也就是说需要租户隔离
             */
            @Override
            public boolean ignoreTable(String tableName) {
                //没有租户的话，不需要租户隔离
                Long tenantCode = TenantCodeContextHolder.getTenantCode();
                if (tenantCode == null || tenantCode == 0) {
                    return true;
                }
                //忽略的表不需要租户隔离
                String tenantTableName = tenantProperties.getTenantTableName();
                return StringUtils.equals(tenantTableName, tableName) || tenantProperties.getIgnoreTenantTables().contains(tableName);
            }

            @Override
            public String getTenantIdColumn() {
                return tenantProperties.getTenantIdColumn();
            }

            @Override
            public Expression getTenantId() {
                Long tenantCode = TenantCodeContextHolder.getTenantCode();
                return new StringValue(String.valueOf(tenantCode));
            }


        });
    }

    /**
     * 修改 MyBatis-Plus 拦截器，注入租户行级隔离插件
     *
     * @param mybatisPlusInterceptor     MyBatis-Plus 拦截器
     * @param tenantLineInnerInterceptor 租户行级内部拦截器
     * @return 包装后的拦截器
     */
    @Bean
    @ConditionalOnClass(MybatisPlusInterceptor.class)
    public ModifyMybatisPlusInterceptor modifyMybatisPlusInterceptor(MybatisPlusInterceptor mybatisPlusInterceptor, TenantLineInnerInterceptor tenantLineInnerInterceptor) {
        return new ModifyMybatisPlusInterceptor(mybatisPlusInterceptor, tenantLineInnerInterceptor);
    }

    /**
     * 租户数据源属性映射缓存 Bean
     *
     * @return 缓存实例
     */
    @Bean
    public TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache() {
        return new TenantDataSourcePropertyMapCache(tenantProperties);
    }

    /**
     * WebMvc 数据源拦截器（根据请求切换数据源）
     *
     * @param tenantDataSourcePropertyMapCache 租户数据源属性缓存
     * @return 拦截器实例
     */
    @Bean
    public WebMvcConfigurerDataSourceInterceptor webMvcConfigurerDataSourceInterceptor(TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache) {
        return new WebMvcConfigurerDataSourceInterceptor(tenantDataSourcePropertyMapCache);
    }

    /**
     * 通用数据源切面（处理 @CommonDataSource）
     *
     * @return 切面实例
     */
    @Bean
    public CommonDataSourceAspect commonDataSourceAspect() {
        return new CommonDataSourceAspect();
    }

    /**
     * 关闭 dynamic-datasource 自带的注解 AOP，由本模块接管
     *
     * @return 配置定制器
     */
    @Bean
    public DynamicDataSourcePropertiesCustomizer CustomDynamicDataSourcePropertiesCustomizer() {
        return properties -> properties.getAop().setEnabled(false);
    }

    /**
     * 覆盖 dynamic-datasource 的注解 AOP，实现多租户多数据源
     *
     * @param tenantDataSourcePropertyMapCache 租户数据源属性缓存
     * @param dsProperties                     动态数据源配置
     * @return 注解顾问（Advisor）
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public Advisor dynamicDatasourceAnnotationAdvisor(TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache, DynamicDataSourceProperties dsProperties) {
        DynamicDatasourceAopProperties aopProperties = dsProperties.getAop();
        DynamicDataSourceCustomAnnotationInterceptor interceptor = new DynamicDataSourceCustomAnnotationInterceptor(aopProperties.getAllowedPublicOnly(), tenantDataSourcePropertyMapCache);
        DynamicDataSourceAnnotationAdvisor advisor = new DynamicDataSourceAnnotationAdvisor(interceptor, DS.class);
        advisor.setOrder(aopProperties.getOrder());
        return advisor;
    }

    /**
     * 创建数据源操作类
     *
     * @param dataSource 当前数据源
     * @return 数据源操作接口实现
     */
    @Bean
    @ConditionalOnMissingBean(DatasourceOperator.class)
    public DatasourceOperator datasourceOperator(DataSource dataSource) {
        return new DatasourceOperator.DatasourceOperatorImpl(dataSource, defaultDataSourceCreator, redissonClient);
    }

    /**
     * 创建租户操作类
     *
     * @param tenantDatasourceService         租户数据源服务
     * @param tenantDataSourcePropertyMapCache 租户数据源属性缓存
     * @return 租户操作接口实现
     */
    @Bean
    @ConditionalOnMissingBean(TenantOperator.class)
    public TenantOperator tenantOperator(TenantDatasourceService tenantDatasourceService, TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache) {
        return new TenantOperator.TenantOperatorImpl(tenantProperties, redissonClient, tenantDatasourceService, tenantDataSourcePropertyMapCache);
    }

    /**
     * 数据源监听器（监听租户数据源变更）
     *
     * @param tenantOperator 租户操作类
     * @return 监听器实例
     */
    @Bean(initMethod = "listen")
    public DataSourceListener dataSourceListener(TenantOperator tenantOperator) {
        return new DataSourceListener(tenantProperties, redissonClient, tenantOperator);
    }

}

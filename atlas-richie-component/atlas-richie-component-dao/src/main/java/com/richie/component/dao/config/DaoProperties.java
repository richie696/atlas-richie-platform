package com.richie.component.dao.config;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DAO配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-24 21:49:37
 */
@Data
@ConfigurationProperties(prefix = DaoConstant.DAO_PREFIX)
public class DaoProperties {

    /**
     * 分页功能使用的数据库类型
     */
    private DbType dbType = DbType.MYSQL;

    /**
     * 是否格式化输出日志信息
     */
    private boolean enableLogging = false;

    /**
     * 是否启用多租户组件
     */
    private boolean enableTenant = false;

    /**
     * 批量更新限制阈值
     */
    private int batchUpdateLimit = 1000;

    /**
     * 是否启用默认字段处理器（如创建时间、更新时间等）
     */
    private boolean enableDefaultFieldHandler = true;

    /**
     * 防全表更新与删除保护（默认：启用）
     */
    private boolean enableBlockAttack = true;

}

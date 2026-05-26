package com.richie.component.dao.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户数据源配置实体，对应租户数据源表，存储各服务下数据源与租户编码的映射。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
@TableName
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TenantDatasource implements Serializable {
    /**
     * 
     */
    @TableId
    private Long id;

    /**
     * 服务名
     */
    private String serviceName;

    /**
     * 多数据源名
     */
    private String dsName;

    /**
     * 租户编码，逗号分隔
     */
    private String tenantCodes;

    /**
     * 数据库用户名
     */
    private String dbUsername;

    /**
     * 数据库密码
     */
    private String dbPassword;

    /**
     * 数据库名称
     */
    private String dbParam;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * ·修改时间
     */
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

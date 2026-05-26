package com.richie.component.dao.tenant;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.util.Collection;

/**
 * 添加租户编码 DTO：用于消息或接口中传递待写入某数据源的租户编码列表及数据源 key。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-05
 */
@Data
public class AddTenantCode implements Serializable {

    /**
     * 租户编码
     */
    private Long tenantCode;

    /**
     * tenant_datasource表id
     */
    private Collection<Long> tenantDatasourceIdList;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}

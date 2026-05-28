package com.richie.component.mfa.management.mapper;

import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MFA用户信息Mapper
 * 
 * <p><b>注意</b>：此Mapper只操作MFA相关的表，不涉及业务系统的User表。
 * userId参数是业务系统User表的主键ID，用于关联，但不查询User表。
 * 
 * @author richie696
 * @since 1.0.0
 */
@Mapper
public interface MfaUserMapper extends BaseMapper<MfaUserInfo> {

    /**
     * 根据租户ID和用户ID查询MFA信息
     * <p>
     * 注意：userId是业务系统User表的主键ID，此方法只查询MFA表，不查询User表。
     * <p>
     * 如果未启用租户，tenantId应为null，通过uk_user_id唯一索引查询
     * <p>
     * 如果启用租户，通过uk_tenant_user唯一索引查询(tenant_id, user_id)
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return MFA用户信息（如果不存在则返回 null）
     */
    MfaUserInfo selectByTenantAndUser(@Param("tenantId") String tenantId, 
                                      @Param("userId") String userId);

    /**
     * 根据租户ID和用户ID查询所有MFA信息（包括已删除的记录）
     * <p>
     * 用于检查是否存在重复绑定或清理重复数据
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return MFA用户信息列表（包括已删除的记录）
     */
    List<MfaUserInfo> selectAllByTenantAndUser(@Param("tenantId") String tenantId, 
                                               @Param("userId") String userId);
}

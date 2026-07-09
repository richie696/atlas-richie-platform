/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.management.mapper;

import com.richie.component.mfa.core.entity.MfaTrustedDevice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MFA可信设备Mapper
 * 
 * @author richie696
 * @since 1.0.0
 */
@Mapper
public interface MfaTrustedDeviceMapper extends BaseMapper<MfaTrustedDevice> {

    /**
     * 根据租户ID和用户ID查询所有可信设备
     * <p>
     * 查询指定用户的所有可信设备列表（包括已过期的设备）
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 可信设备列表（如果不存在则返回空列表）
     */
    List<MfaTrustedDevice> selectByTenantAndUser(@Param("tenantId") String tenantId,
                                                  @Param("userId") String userId);

    /**
     * 根据租户ID、用户ID和设备ID查询可信设备
     * <p>
     * 查询指定用户的指定可信设备
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param deviceId 设备ID（必填，设备指纹）
     * @return 可信设备（如果不存在则返回 null）
     */
    MfaTrustedDevice selectByDeviceId(@Param("tenantId") String tenantId,
                                      @Param("userId") String userId,
                                      @Param("deviceId") String deviceId);

    /**
     * 统计用户的可信设备数量
     * <p>
     * 统计指定用户的可信设备数量（包括已过期的设备）
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 可信设备数量（如果查询失败或用户不存在，返回 0）
     */
    Long countByTenantAndUser(@Param("tenantId") String tenantId,
                              @Param("userId") String userId);

    /**
     * 查询用户的主管理设备（is_primary=1）
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID
     * @return 主设备，若无则 null
     */
    MfaTrustedDevice selectPrimaryByTenantAndUser(@Param("tenantId") String tenantId,
                                                   @Param("userId") String userId);

    /**
     * 清除该用户下所有设备的主标记
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID
     * @return 更新行数
     */
    int clearPrimaryByTenantAndUser(@Param("tenantId") String tenantId,
                                     @Param("userId") String userId);

    /**
     * 将指定设备设为主设备（is_primary=1）
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 更新行数
     */
    int setPrimaryByTenantAndUserAndDevice(@Param("tenantId") String tenantId,
                                           @Param("userId") String userId,
                                           @Param("deviceId") String deviceId);
}

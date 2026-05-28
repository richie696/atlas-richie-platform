package com.richie.component.mfa.core.entity;

import com.richie.context.common.api.domain.AuditDomain;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * MFA用户信息实体
 * 
 * 对应数据库表：mfa_user_info
 * 
 * <p><b>重要设计原则</b>：MFA组件只关联业务系统User表的主键ID，不维护任何用户信息。
 * 这样设计的好处：
 * <ul>
 *   <li>MFA组件完全独立，不依赖业务系统的User表结构</li>
 *   <li>不同业务系统的User表结构可以不同，MFA组件都能适配</li>
 *   <li>业务系统无需为MFA修改已有的User模块代码</li>
 * </ul>
 * 
 * @author richie696
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfa_user_info")
public class MfaUserInfo extends AuditDomain {

    /**
     * 租户ID（VARCHAR类型）
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 用户ID（业务系统User表的主键ID）
     * 
     * <p>注意：此字段仅存储业务系统User表的主键ID，不存储任何其他用户信息。
     * MFA组件通过此ID与业务系统的User表建立关联，但不需要知道User表的其他字段。
     * 
     * <p>类型说明：
     * <ul>
     *   <li>如果业务系统User表主键是String类型，直接使用</li>
     *   <li>如果业务系统User表主键是Long类型，需要转换为String存储</li>
     * </ul>
     */
    private String userId;


    /**
     * 设备类型（TOTP/HOTP/SMS/EMAIL）
     */
    private String deviceType;

    /**
     * 算法（SHA1/SHA256/SHA512）
     */
    private String algorithm;

    /**
     * 验证码位数
     */
    private Integer digits;

    /**
     * 时间窗口（秒）
     */
    private Integer period;

    /**
     * 状态（NOT_ACTIVATED=未激活，ENABLED=已启用，DISABLED=已禁用）
     */
    private MfaStatusEnum status;

    /**
     * 绑定时间
     */
    private OffsetDateTime bindTime;

    private OffsetDateTime activatedTime;

    private OffsetDateTime lastUsedTime;

    /**
     * 失败尝试次数
     */
    private Integer failedAttempts;

    /**
     * 锁定截止时间
     */
    private OffsetDateTime lockedUntil;

    private OffsetDateTime keyRotationTime;

    /**
     * 备份码（哈希后，JSON格式）
     */
    private String backupCodesHashed;
}

package com.richie.component.mfa.core.entity;

import com.richie.context.common.api.domain.AuditDomain;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * MFA可信设备实体
 *
 * 对应数据库表：mfa_trusted_device
 *
 * <p><b>设计说明</b>：
 * <ul>
 *   <li>存储用户信任的设备信息，用于跳过MFA验证</li>
 *   <li>设备ID由前端生成（浏览器指纹、Android ID、iOS IDFV等）</li>
 *   <li>信任过期后需要重新进行MFA验证</li>
 *   <li>支持多租户，tenantId可为null（当未启用租户时）</li>
 * </ul>
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfa_trusted_device")
public class MfaTrustedDevice extends AuditDomain {

    /**
     * 租户ID（VARCHAR类型，可选）
     *
     * <p>注意：如果系统未启用租户，此字段为null
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 用户ID（业务系统User表的主键ID）
     *
     * <p>注意：此字段仅存储业务系统User表的主键ID，不存储任何其他用户信息。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 设备唯一标识（设备指纹）
     *
     * <p>由前端生成：
     * <ul>
     *   <li>网页端：浏览器指纹（Canvas、WebGL等特征组合的SHA-256哈希）</li>
     *   <li>Android：Android ID或自定义UUID</li>
     *   <li>iOS：IdentifierForVendor (IDFV)或Keychain UUID</li>
     * </ul>
     */
    @TableField("device_id")
    private String deviceId;

    /**
     * 设备名称（用于用户界面显示）
     *
     * <p>示例：
     * <ul>
     *   <li>"Chrome on Windows"</li>
     *   <li>"iPhone 14 Pro"</li>
     *   <li>"Samsung Galaxy S23"</li>
     * </ul>
     */
    @TableField("device_name")
    private String deviceName;

    /**
     * 设备指纹（原始指纹字符串的哈希，用于审计）
     *
     * <p>可选字段，用于记录设备指纹的原始特征（哈希后），便于审计和异常检测
     */
    @TableField("device_fingerprint")
    private String deviceFingerprint;

    /**
     * 信任过期时间
     *
     * <p>超过此时间后，设备信任失效，需要重新进行MFA验证
     */
    @TableField("trusted_until")
    private OffsetDateTime trustedUntil;

    private OffsetDateTime lastUsedTime;

    /**
     * 是否为主管理设备（0=否，1=是）
     * <p>
     * 主设备可移除其他可信设备、设置新的主设备；非主设备仅可查看列表。每用户仅允许一台主设备。
     */
    @TableField("is_primary")
    private Integer isPrimary;
}

package com.richie.component.mfa.dto;

import lombok.Data;

/**
 * 登录请求 DTO
 *
 * @author richie696
 * @since 2026-01-29
 */
@Data
public class LoginDTO {

    /**
     * 租户ID
     */
    private String tenantId;
    
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 可选：MFA 验证码（如 TOTP）
     */
    private String mfaCode;
}


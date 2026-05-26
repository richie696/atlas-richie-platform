package com.richie.component.mfa.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 登录响应 DTO
 *
 * @author richie696
 * @since 2026-01-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LoginUserPrincipal extends com.richie.contract.model.LoginUserPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（示例）
     */
    private Long userId;

    /**
     * 访问令牌（示例）
     */
    private String accessToken;

    /**
     * 是否已绑定MFA设备
     */
    private Boolean mfaBound;
}


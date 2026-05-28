package com.richie.gateway.dto;

import lombok.Data;

/**
 * 登出请求 DTO
 * <p>
 * 普通访问令牌通过请求头 X-Access-Token 传递，MFA 临时令牌可通过 body 传递。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class LogoutRequest {

    /**
     * MFA 临时令牌（可选，曾走 MFA 流程时传入，用于一并加入黑名单）
     */
    private String mfaToken;
}

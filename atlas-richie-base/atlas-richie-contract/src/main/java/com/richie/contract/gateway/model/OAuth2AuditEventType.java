package com.richie.contract.gateway.model;

/**
 * OAuth2.0 审计事件类型枚举
 * <p>
 * 用于标识审计事件的具体类型，例如：
 * <ul>
 *   <li>TOKEN_ISSUED：access_token 颁发</li>
 *   <li>TOKEN_REFRESHED：refresh_token 刷新成功</li>
 *   <li>TOKEN_REVOKED：token 撤销</li>
   *   <li>ACCESS_GRANTED：访问通过</li>
   *   <li>ACCESS_DENIED：访问被拒绝</li>
   *   <li>SUSPICIOUS_ACTIVITY：可疑行为</li>
   * </ul>
 */
public enum OAuth2AuditEventType {

    /** access_token 颁发成功 */
    TOKEN_ISSUED,

    /** access_token 颁发失败 */
    TOKEN_ISSUE_FAILED,

    /** refresh_token 刷新成功 */
    TOKEN_REFRESHED,

    /** refresh_token 刷新失败 */
    TOKEN_REFRESHED_FAILED,

    /** 任意 token 被显式撤销 */
    TOKEN_REVOKED,

    /** token 校验通过（访问授权成功） */
    ACCESS_GRANTED,

    /** token 无效 / 过期 / 黑名单等导致访问被拒绝 */
    ACCESS_DENIED,

    /** 其他安全相关的可疑行为 */
    SUSPICIOUS_ACTIVITY
}


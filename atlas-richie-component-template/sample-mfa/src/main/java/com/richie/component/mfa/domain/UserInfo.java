package com.richie.component.mfa.domain;

import com.richie.context.common.api.domain.AuditDomain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 示例用户信息表（user_info）
 * <p>
 * 后续可以通过 userId 与 MFA 组件的 mfa_user_info 表进行关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_info")
public class UserInfo extends AuditDomain {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码摘要（示例中使用简单摘要，实际项目请使用强密码加密方案）
     */
    private String passwordHash;

}


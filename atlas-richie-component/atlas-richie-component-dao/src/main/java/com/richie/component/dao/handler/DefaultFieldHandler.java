package com.richie.component.dao.handler;

import com.richie.context.common.api.LoginUserContextHolder;
import com.richie.contract.model.LoginUserPrincipal;
import com.richie.component.dao.config.DaoProperties;
import com.richie.component.tenant.context.TenantContext;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;


/**
 * 默认字段处理器
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-09 17:43:20
 */
@Component
@RequiredArgsConstructor
public class DefaultFieldHandler implements MetaObjectHandler {

    /** DAO 配置，用于判断是否启用默认字段填充 */
    private final DaoProperties daoProperties;
    private static final String CREATE_ID = "createId";
    private static final String UPDATE_ID = "updateId";
    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";
    private static final String DELETED = "deleted";

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!daoProperties.isEnableDefaultFieldHandler()) {
            return;
        }
        String username = getUsername();
        var currentDateTime = Instant.now();
        if (metaObject.hasGetter(CREATE_ID) && metaObject.getValue(CREATE_ID) == null) {
            this.strictInsertFill(metaObject, CREATE_ID, String.class, username);
        }
        if (metaObject.hasGetter(UPDATE_ID) && metaObject.getValue(UPDATE_ID) == null) {
            this.strictInsertFill(metaObject, UPDATE_ID, String.class, username);
        }
        if (metaObject.hasGetter(CREATE_TIME) && metaObject.getValue(CREATE_TIME) == null) {
            this.strictInsertFill(metaObject, CREATE_TIME, Instant.class, currentDateTime);
        }
        if (metaObject.hasGetter(UPDATE_TIME) && metaObject.getValue(UPDATE_TIME) == null) {
            this.strictInsertFill(metaObject, UPDATE_TIME, Instant.class, currentDateTime);
        }
        if (metaObject.hasGetter(DELETED) && metaObject.getValue(DELETED) == null) {
            this.strictInsertFill(metaObject, DELETED, Boolean.class, false);
        }
        if (metaObject.hasGetter("tenantId") && metaObject.getValue("tenantId") == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                this.strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
            } else {
                // 未开启租户时，tenant_id 默认为 0，DDL 约定：BIGINT NOT NULL DEFAULT 0
                this.strictInsertFill(metaObject, "tenantId", Long.class, 0L);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (!daoProperties.isEnableDefaultFieldHandler()) {
            return;
        }
        if (metaObject.hasGetter(UPDATE_ID) && metaObject.getValue(UPDATE_ID) == null) {
            this.strictUpdateFill(metaObject, UPDATE_ID, String.class, getUsername());
        }
        if (metaObject.hasGetter(UPDATE_TIME) && metaObject.getValue(UPDATE_TIME) == null) {
            this.strictUpdateFill(metaObject, UPDATE_TIME, Instant.class, Instant.now());
        }
    }

    private String getUsername() {
        LoginUserPrincipal userInfo = LoginUserContextHolder.getUserInfo(true);
        return userInfo == null ? "" : userInfo.getUsername();
    }

}

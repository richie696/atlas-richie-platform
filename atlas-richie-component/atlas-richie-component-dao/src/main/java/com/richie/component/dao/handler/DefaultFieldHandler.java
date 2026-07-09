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
package com.richie.component.dao.handler;

import com.richie.context.common.api.LoginUserContextHolder;
import com.richie.contract.model.LoginUserPrincipal;
import com.richie.component.dao.config.DaoProperties;
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

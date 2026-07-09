/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.dao.handler;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.richie.component.dao.config.DaoProperties;
import com.richie.context.common.api.LoginUserContextHolder;
import com.richie.contract.model.LoginUserPrincipal;
import lombok.Data;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFieldHandlerTest {

    private DefaultFieldHandler handler;
    private DaoProperties properties;

    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, AuditableEntity.class);
    }

    @BeforeEach
    void setUp() {
        properties = new DaoProperties();
        handler = new DefaultFieldHandler(properties);
    }

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.clear();
    }

    @Test
    void insertFill_skipsWhenHandlerDisabled() {
        properties.setEnableDefaultFieldHandler(false);
        AuditableEntity entity = new AuditableEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateId()).isNull();
        assertThat(entity.getCreateTime()).isNull();
        assertThat(entity.getDeleted()).isNull();
    }

    @Test
    void insertFill_populatesAuditFieldsWhenUserPresent() {
        LoginUserPrincipal user = new LoginUserPrincipal().setUsername("alice");
        LoginUserContextHolder.setUserInfo(user);
        AuditableEntity entity = new AuditableEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateId()).isEqualTo("alice");
        assertThat(entity.getUpdateId()).isEqualTo("alice");
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isEqualTo(entity.getCreateTime());
        assertThat(entity.getDeleted()).isFalse();
    }

    @Test
    void insertFill_usesEmptyUsernameWhenNoLoginUser() {
        AuditableEntity entity = new AuditableEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateId()).isEmpty();
        assertThat(entity.getUpdateId()).isEmpty();
    }

    @Test
    void insertFill_doesNotOverwriteExistingValues() {
        AuditableEntity entity = new AuditableEntity();
        entity.setCreateId("preset");
        entity.setCreateTime(Instant.parse("2020-01-01T00:00:00Z"));
        entity.setDeleted(true);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateId()).isEqualTo("preset");
        assertThat(entity.getCreateTime()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(entity.getDeleted()).isTrue();
    }

    @Test
    void updateFill_populatesUpdateFieldsOnly() {
        LoginUserPrincipal user = new LoginUserPrincipal().setUsername("bob");
        LoginUserContextHolder.setUserInfo(user);
        AuditableEntity entity = new AuditableEntity();
        entity.setCreateId("old");

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateId()).isEqualTo("old");
        assertThat(entity.getUpdateId()).isEqualTo("bob");
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void updateFill_skipsWhenHandlerDisabled() {
        properties.setEnableDefaultFieldHandler(false);
        AuditableEntity entity = new AuditableEntity();

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateId()).isNull();
        assertThat(entity.getUpdateTime()).isNull();
    }

    @TableName("t_auditable")
    @Data
    static class AuditableEntity {
        @TableField(fill = FieldFill.INSERT)
        private String createId;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private String updateId;
        @TableField(fill = FieldFill.INSERT)
        private Instant createTime;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private Instant updateTime;
        @TableField(fill = FieldFill.INSERT)
        private Boolean deleted;
    }
}

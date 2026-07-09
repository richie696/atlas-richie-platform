/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mongodb.core;

import com.richie.component.mongodb.builder.LambdaField;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LambdaMetaTest {

    @Test
    void resolveFieldName_withGetPrefix_shouldReturnFieldName() {
        LambdaField<LambdaEntity, String> idFn = LambdaEntity::getId;
        LambdaField<LambdaEntity, String> nameFn = LambdaEntity::getName;
        LambdaField<LambdaEntity, String> usernameFn = LambdaEntity::getUsername;
        LambdaField<LambdaEntity, String> createTimeFn = LambdaEntity::getCreateTime;

        assertThat(LambdaMeta.resolveFieldName(idFn)).isEqualTo("id");
        assertThat(LambdaMeta.resolveFieldName(nameFn)).isEqualTo("name");
        assertThat(LambdaMeta.resolveFieldName(usernameFn)).isEqualTo("username");
        assertThat(LambdaMeta.resolveFieldName(createTimeFn)).isEqualTo("createTime");
    }

    @Test
    void resolveFieldName_withIsPrefix_shouldReturnFieldName() {
        LambdaField<LambdaEntity, Boolean> activeFn = LambdaEntity::isActive;
        LambdaField<LambdaEntity, Boolean> validFn = LambdaEntity::isValid;

        assertThat(LambdaMeta.resolveFieldName(activeFn)).isEqualTo("active");
        assertThat(LambdaMeta.resolveFieldName(validFn)).isEqualTo("valid");
    }

    @Test
    void resolveFieldName_withNoStandardPrefix_shouldReturnMethodName() {
        LambdaField<LambdaEntity, String> customFn = LambdaEntity::customMethod;
        assertThat(LambdaMeta.resolveFieldName(customFn)).isEqualTo("customMethod");
    }

    @Test
    void extract_withValidLambda_shouldReturnSerializedLambda() {
        LambdaField<LambdaEntity, String> lambda = LambdaEntity::getName;
        var serialized = LambdaMeta.extract(lambda);
        assertThat(serialized).isNotNull();
        assertThat(serialized.getImplMethodName()).isEqualTo("getName");
    }

    public static class LambdaEntity {
        private String id;
        private String name;
        private String username;
        private String createTime;
        private boolean active;
        private boolean valid;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String customMethod() { return "custom"; }
    }
}

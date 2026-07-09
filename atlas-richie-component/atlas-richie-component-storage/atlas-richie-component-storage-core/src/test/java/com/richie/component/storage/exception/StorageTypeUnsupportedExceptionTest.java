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
package com.richie.component.storage.exception;

import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageTypeUnsupportedExceptionTest {

    @Test
    void message_shouldIncludeEngineAndType() {
        RuntimeException cause = new RuntimeException("root");
        var ex = new StorageTypeUnsupportedException(
                StorageEngineEnum.AWS_S3,
                StorageTypeEnum.MULTI_AZ_STANDARD,
                "unsupported");
        assertThat(ex.getMessage()).contains("AWS S3");
        assertThat(ex.getMessage()).contains("unsupported");

        var withCause = new StorageTypeUnsupportedException(
                StorageEngineEnum.AWS_S3, StorageTypeEnum.STANDARD, "msg", cause);
        assertThat(withCause.getCause()).isSameAs(cause);

        var causeOnly = new StorageTypeUnsupportedException(
                StorageEngineEnum.AWS_S3, StorageTypeEnum.STANDARD, cause);
        assertThat(causeOnly.getCause()).isSameAs(cause);
    }
}

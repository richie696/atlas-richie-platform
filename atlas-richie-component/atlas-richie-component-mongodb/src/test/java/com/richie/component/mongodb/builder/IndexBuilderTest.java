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
package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexBuilderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private EntityIntrospector entityIntrospector;

    @Mock
    private org.springframework.data.mongodb.core.index.IndexOperations indexOperations;

    private IndexBuilder indexBuilder;

    @BeforeEach
    void setUp() {
        indexBuilder = new IndexBuilder(mongoTemplate, entityIntrospector);
    }

    @Test
    void ensureIndexes_shouldCallCreateIndex() throws NoSuchFieldException {
        when(mongoTemplate.indexOps(User.class)).thenReturn(indexOperations);
        when(entityIntrospector.getIndexedFields(User.class))
                .thenReturn(List.of(User.class.getDeclaredField("username"), User.class.getDeclaredField("email")));
        when(entityIntrospector.getExpireAfterFields(User.class)).thenReturn(Collections.emptyList());
        indexBuilder.ensureIndexes(User.class);
        verify(indexOperations, times(2)).createIndex(any(IndexDefinition.class));
    }

    @Data
    static class User {
        @Id
        private String id;

        @Indexed(unique = true)
        private String username;

        @Indexed
        private String email;

    }
}

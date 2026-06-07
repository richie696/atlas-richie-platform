package com.richie.component.mongodb.builder;

import com.richie.component.mongodb.core.EntityIntrospector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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
    void ensureIndexes_shouldCallEnsureIndex() {
        when(mongoTemplate.indexOps(User.class)).thenReturn(indexOperations);
        indexBuilder.ensureIndexes(User.class);
        verify(indexOperations, times(2)).ensureIndex(any(IndexDefinition.class));
    }

    static class User {
        @org.springframework.data.annotation.Id
        private String id;

        @org.springframework.data.mongodb.core.index.Indexed(unique = true)
        private String username;

        @org.springframework.data.mongodb.core.index.Indexed
        private String email;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}

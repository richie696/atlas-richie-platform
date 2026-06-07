package com.richie.component.mongodb.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.lang.reflect.Field;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EntityIntrospectorTest {

    private EntityIntrospector introspector;

    @BeforeEach
    void setUp() {
        introspector = new EntityIntrospector();
    }

    @Test
    void getCollectionName_withDocumentAnnotation_shouldReturnCollectionName() {
        String name = introspector.getCollectionName(DocumentedEntity.class);
        assertThat(name).isEqualTo("custom_collection");
    }

    @Test
    void getCollectionName_withoutDocumentAnnotation_shouldReturnLowercaseClassName() {
        String name = introspector.getCollectionName(SimpleEntity.class);
        assertThat(name).isEqualTo("simpleentity");
    }

    @Test
    void getIdFieldName_withIdAnnotation_shouldReturnIdFieldName() {
        String name = introspector.getIdFieldName(DocumentedEntity.class);
        assertThat(name).isEqualTo("customId");
    }

    @Test
    void getIdFieldName_withoutIdAnnotation_shouldReturnId() {
        String name = introspector.getIdFieldName(SimpleEntity.class);
        assertThat(name).isEqualTo("id");
    }

    @Test
    void getIndexedFields_shouldReturnFieldsWithIndexedAnnotation() {
        List<Field> fields = introspector.getIndexedFields(DocumentedEntity.class);
        assertThat(fields).hasSize(2);
    }

    @Document("custom_collection")
    static class DocumentedEntity {
        @org.springframework.data.annotation.Id
        private String customId;

        @Indexed
        private String indexedField1;

        @Indexed(unique = true)
        private String indexedField2;

        private String normalField;

        public String getCustomId() { return customId; }
        public void setCustomId(String customId) { this.customId = customId; }
        public String getIndexedField1() { return indexedField1; }
        public void setIndexedField1(String indexedField1) { this.indexedField1 = indexedField1; }
        public String getIndexedField2() { return indexedField2; }
        public void setIndexedField2(String indexedField2) { this.indexedField2 = indexedField2; }
        public String getNormalField() { return normalField; }
        public void setNormalField(String normalField) { this.normalField = normalField; }
    }

    static class SimpleEntity {
        private String id;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

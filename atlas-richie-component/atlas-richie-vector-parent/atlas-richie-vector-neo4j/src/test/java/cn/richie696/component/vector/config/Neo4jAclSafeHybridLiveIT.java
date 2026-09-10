package cn.richie696.component.vector.config;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.impl.Neo4jAclAwareHybridSearchOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** True Neo4j E2E using ACL-first Cypher candidate queries. */
@EnabledIfEnvironmentVariable(named = "VECTOR_NEO4J_IT_RUN", matches = "true")
class Neo4jAclSafeHybridLiveIT {
    @Test
    void excludesDeniedLexicalWinnerAndDeletesTheAllowedNode() {
        String base = "aclhybrid" + UUID.randomUUID().toString().replace("-", "");
        String label = "VectorDocument_" + base;
        try (Driver driver = GraphDatabase.driver(required("VECTOR_NEO4J_URI"),
                AuthTokens.basic(required("VECTOR_NEO4J_USERNAME"), required("VECTOR_NEO4J_PASSWORD")))) {
            try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
                session.run("CREATE CONSTRAINT `" + base + "_id_unique` IF NOT EXISTS FOR (n:" + label + ") REQUIRE n.id IS UNIQUE").consume();
                String allowed = UUID.randomUUID().toString(); String denied = UUID.randomUUID().toString();
                session.run("CREATE (n:" + label + " {id:$id, content:$content, embedding:$embedding, `metadata.tenantId`:$tenant})",
                        Map.of("id", allowed, "content", "ordinary permitted", "embedding", List.of(1F,0F,0F,0F), "tenant", "tenant-a")).consume();
                session.run("CREATE (n:" + label + " {id:$id, content:$content, embedding:$embedding, `metadata.tenantId`:$tenant})",
                        Map.of("id", denied, "content", "rare restricted", "embedding", List.of(0F,1F,0F,0F), "tenant", "tenant-b")).consume();
                Neo4jAclAwareHybridSearchOperations hybrid = new Neo4jAclAwareHybridSearchOperations(driver, SessionConfig.forDatabase("neo4j"), embedding(),
                        new HybridStoreOptions(true, "embedding", "content", null, 10, HybridStoreOptions.FallbackMode.CORE_RRF), Map.of("documents", base), "cosine");
                var results = hybrid.hybridSearch("documents", "ordinary", "rare", 5,
                        HybridSearchOptions.builder().vectorWeight(.3D).keywordWeight(.7D).build(), VectorFilter.eq("tenantId", "tenant-a"));
                assertThat(results).extracting(result -> result.getId()).containsExactly(allowed);
                assertThat(results).noneMatch(result -> denied.equals(result.getId()));
                session.run("MATCH (n:" + label + " {id:$id}) DELETE n", Map.of("id", allowed)).consume();
                assertThat(hybrid.hybridSearch("documents", "ordinary", "ordinary", 5, null, VectorFilter.eq("tenantId", "tenant-a"))).isEmpty();
            } finally {
                try (var cleanup = driver.session(SessionConfig.forDatabase("neo4j"))) { cleanup.run("MATCH (n:" + label + ") DETACH DELETE n").consume(); cleanup.run("DROP CONSTRAINT `" + base + "_id_unique` IF EXISTS").consume(); }
            }
        }
    }
    private static String required(String name){ String value=System.getenv(name); if(value==null||value.isBlank()) throw new IllegalStateException("missing integration-test environment variable: "+name); return value; }
    private static EmbeddingModel embedding(){ return EmbeddingModel.class.cast(Proxy.newProxyInstance(EmbeddingModel.class.getClassLoader(),new Class<?>[]{EmbeddingModel.class},(p,m,a)->"embed".equals(m.getName())?new float[]{1F,0F,0F,0F}:"dimensions".equals(m.getName())?4:null)); }
}

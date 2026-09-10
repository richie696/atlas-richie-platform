package cn.richie696.component.vector.service.impl;
import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbFilterExpressionConverter;
import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbVectorStore;
import cn.richie696.component.vector.filter.TencentVectorDbAclFilterAdapter;
import cn.richie696.component.vector.model.*;
import cn.richie696.component.vector.service.*;
import cn.richie696.component.vector.service.support.AclSafeHybridSearchFallback;
import com.tencent.tcvectordb.model.param.dml.*;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;
/** Native Tencent VectorDB hybrid request, with ACL filter attached before dense/sparse fusion. */
public final class TencentVectorDbAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final TencentVectorDbVectorStore store; private final org.springframework.ai.embedding.EmbeddingModel embedding; private final SparseVectorizer sparse; private final HybridStoreOptions options; private final Map<String,String> collections;
    private final ThreadLocal<String> execution = ThreadLocal.withInitial(() -> "native");
    public TencentVectorDbAclAwareHybridSearchOperations(TencentVectorDbVectorStore store,org.springframework.ai.embedding.EmbeddingModel embedding,SparseVectorizer sparse,HybridStoreOptions options,Map<String,String> collections){this.store=store;this.embedding=embedding;this.sparse=sparse;this.options=options;this.collections=Map.copyOf(collections);}
    @Override public String hybridExecutionMode(){return execution.get();}
    @Override public List<VectorSearchResult> hybridSearch(String i,String t,String k,int l,HybridSearchOptions o){throw new UnsupportedOperationException("Tencent VectorDB ACL-safe hybrid search requires an explicit structured ACL filter");}
    @Override public List<VectorSearchResult> hybridSearch(String index,String text,String keyword,int limit,HybridSearchOptions query,VectorFilter filter){
        if(filter==null)throw new IllegalArgumentException("ACL filter must not be null");if(limit<=0)throw new IllegalArgumentException("hybrid search limit must be greater than zero");String dense=text==null||text.isBlank()?keyword:text;String lexical=keyword==null||keyword.isBlank()?dense:keyword;if(dense==null||dense.isBlank())throw new IllegalArgumentException("text and keywordQuery must not both be blank");if(!collections.containsKey(index))throw new IllegalArgumentException("index is not declared by this Tencent VectorDB Store: "+index);
        List<Double> vector=new ArrayList<>();for(float v:embedding.embed(dense))vector.add((double)v);List<Pair<Long,Float>> coords=sparse.encode(lexical).coordinates().entrySet().stream().map(e->Pair.of(e.getKey(),e.getValue())).toList();
        String acl=new TencentVectorDbFilterExpressionConverter().convertExpression(TencentVectorDbAclFilterAdapter.toSpring(filter));
        int candidateLimit = Math.max(limit, options.candidateLimit());
        HybridSearchParam nativeRequest = HybridSearchParam.newBuilder()
                .withAnn(AnnOption.newBuilder().withFieldName(options.denseField()).withData(vector).withLimit(candidateLimit).build())
                .withMatch(MatchOption.newBuilder().withFieldName(options.sparseField()).withData(List.of(coords)).withLimit(candidateLimit).build())
                .withFilter(acl).withOutputFields(List.of("content")).withLimit(limit).build();
        try {
            if (options.fallbackMode() == HybridStoreOptions.FallbackMode.CORE_RRF) {
                return AclSafeHybridSearchFallback.nativeFirst(
                    () -> nativeSearch(nativeRequest),
                    () -> candidates(AnnOption.newBuilder().withFieldName(options.denseField()).withData(vector).withLimit(candidateLimit).build(), acl, candidateLimit),
                    () -> candidates(MatchOption.newBuilder().withFieldName(options.sparseField()).withData(List.of(coords)).withLimit(candidateLimit).build(), acl, candidateLimit),
                    query, limit, TencentVectorDbAclAwareHybridSearchOperations::isUnsupportedNativeHybrid);
            }
            return nativeSearch(nativeRequest);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Tencent VectorDB ACL-safe hybrid query failed", error);
        }
    }

    private List<VectorSearchResult> nativeSearch(HybridSearchParam request) {
        execution.set("native");
        return map(store.hybridSearch(request));
    }

    private List<VectorSearchResult> candidates(AnnOption ann, String filter, int limit) {
        execution.set("core-rrf");
        return map(store.hybridSearch(HybridSearchParam.newBuilder().withAnn(ann).withFilter(filter)
                .withOutputFields(List.of("content")).withLimit(limit).build()));
    }

    private List<VectorSearchResult> candidates(MatchOption match, String filter, int limit) {
        execution.set("core-rrf");
        return map(store.hybridSearch(HybridSearchParam.newBuilder().withMatch(match).withFilter(filter)
                .withOutputFields(List.of("content")).withLimit(limit).build()));
    }

    private static List<VectorSearchResult> map(com.tencent.tcvectordb.model.param.entity.HybridSearchRes response) {
        if (response == null || response.getDocuments() == null) return List.of();
        return response.getDocuments().stream().map(doc -> VectorSearchResult.of(doc.getId(),
                doc.getDoc() == null ? "" : doc.getDoc(), doc.getScore() == null ? 0D : doc.getScore())).toList();
    }

    static boolean isUnsupportedNativeHybrid(RuntimeException error) {
        Throwable current = error;
        while (current != null) {
            String message = String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT);
            if ((message.contains("hybrid") || message.contains("rerank"))
                    && (message.contains("unsupported") || message.contains("not support")
                    || message.contains("not implemented") || message.contains("invalid parameter"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

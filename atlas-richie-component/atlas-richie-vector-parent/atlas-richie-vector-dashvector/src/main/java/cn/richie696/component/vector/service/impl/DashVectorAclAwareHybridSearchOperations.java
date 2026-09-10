package cn.richie696.component.vector.service.impl;

import cn.richie696.ai.vectorstore.dashvector.DashVectorFilterExpressionConverter;
import cn.richie696.ai.vectorstore.dashvector.DashVectorVectorStore;
import cn.richie696.component.vector.filter.DashVectorAclFilterAdapter;
import cn.richie696.component.vector.model.*;
import cn.richie696.component.vector.service.*;
import com.aliyun.dashvector.models.Vector;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.requests.QueryDocRequest;
import java.util.*;

/** Native DashVector dense+sparse request with one server-side ACL filter. */
public final class DashVectorAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private final DashVectorVectorStore store; private final org.springframework.ai.embedding.EmbeddingModel embedding; private final SparseVectorizer sparse; private final HybridStoreOptions options; private final Map<String,String> collections;
    public DashVectorAclAwareHybridSearchOperations(DashVectorVectorStore store,org.springframework.ai.embedding.EmbeddingModel embedding,SparseVectorizer sparse,HybridStoreOptions options,Map<String,String> collections){this.store=store;this.embedding=embedding;this.sparse=sparse;this.options=options;this.collections=Map.copyOf(collections);}
    @Override public String hybridExecutionMode(){return "native";}
    @Override public List<VectorSearchResult> hybridSearch(String i,String t,String k,int l,HybridSearchOptions o){throw new UnsupportedOperationException("DashVector ACL-safe hybrid search requires an explicit structured ACL filter");}
    @Override public List<VectorSearchResult> hybridSearch(String index,String text,String keyword,int limit,HybridSearchOptions query,VectorFilter filter){
        if(filter==null)throw new IllegalArgumentException("ACL filter must not be null");if(limit<=0)throw new IllegalArgumentException("hybrid search limit must be greater than zero");String dense=text==null||text.isBlank()?keyword:text;String lexical=keyword==null||keyword.isBlank()?dense:keyword;if(dense==null||dense.isBlank())throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        if(!collections.containsKey(index))throw new IllegalArgumentException("index is not declared by this DashVector Store: "+index);
        float[] values=embedding.embed(dense);Map<Long,Float> sparseValues=sparse.encode(lexical).coordinates();
        String acl=new DashVectorFilterExpressionConverter().convertExpression(DashVectorAclFilterAdapter.toSpring(filter));
        var response=store.query(collections.get(index),QueryDocRequest.builder().vector(Vector.builder().value(floats(values)).build()).sparseVector(sparseValues).topk(Math.max(limit,options.candidateLimit())).filter(acl).build());
        if(response==null||!response.isSuccess())throw new IllegalStateException(failure(response));
        return response.getOutput().stream().limit(limit).map(doc->VectorSearchResult.of(doc.getId(),String.valueOf(doc.getFields().getOrDefault("content","")),(double) doc.getScore())).toList();
    }
    private static List<Float> floats(float[] values){List<Float> out=new ArrayList<>(values.length);for(float value:values)out.add(value);return out;}
    private static String failure(com.aliyun.dashvector.models.responses.Response<?> response){if(response==null)return "DashVector ACL-safe hybrid query failed: empty provider response";return "DashVector ACL-safe hybrid query failed: code="+response.getCode()+", message="+String.valueOf(response.getMessage())+", requestId="+String.valueOf(response.getRequestId());}
}

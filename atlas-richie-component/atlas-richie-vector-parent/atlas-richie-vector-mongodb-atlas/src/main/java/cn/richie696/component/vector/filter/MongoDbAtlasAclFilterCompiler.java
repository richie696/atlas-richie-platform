package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.bson.Document;

import java.util.List;

/** Structured ACL filter mapper shared by Atlas Vector Search and Atlas Search compound filters. */
public final class MongoDbAtlasAclFilterCompiler {
    public Document vectorFilter(VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        return node(filter);
    }
    /** Atlas Search compound filter clause, semantically equivalent to {@link #vectorFilter(VectorFilter)}. */
    public Document searchFilter(VectorFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        return searchNode(filter);
    }
    private static Document node(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> new Document(path(eq.field()), eq.value());
            case VectorFilter.In in -> new Document(path(in.field()), new Document("$in", in.values()));
            case VectorFilter.ContainsAny any -> new Document(path(any.field()), new Document("$in", any.values()));
            case VectorFilter.Range range -> range(range);
            case VectorFilter.Exists exists -> new Document(path(exists.field()), new Document("$exists", true));
            case VectorFilter.Not not -> new Document("$nor", List.of(node(not.filter())));
            case VectorFilter.And and -> new Document("$and", and.filters().stream().map(MongoDbAtlasAclFilterCompiler::node).toList());
            case VectorFilter.Or or -> new Document("$or", or.filters().stream().map(MongoDbAtlasAclFilterCompiler::node).toList());
        };
    }
    private static Document range(VectorFilter.Range range) { Document values=new Document(); if(range.greaterThanOrEqual()!=null) values.append("$gte", number(range.greaterThanOrEqual())); if(range.lessThanOrEqual()!=null) values.append("$lte", number(range.lessThanOrEqual())); return new Document(path(range.field()),values); }
    private static Document searchNode(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> new Document("equals", new Document("path", path(eq.field())).append("value", eq.value()));
            case VectorFilter.In in -> new Document("in", new Document("path", path(in.field())).append("value", in.values()));
            case VectorFilter.ContainsAny any -> new Document("in", new Document("path", path(any.field())).append("value", any.values()));
            case VectorFilter.Range range -> new Document("range", new Document("path", path(range.field()))
                    .append("gte", range.greaterThanOrEqual()).append("lte", range.lessThanOrEqual()));
            case VectorFilter.Exists exists -> new Document("exists", new Document("path", path(exists.field())));
            case VectorFilter.Not not -> new Document("compound", new Document("mustNot", List.of(searchNode(not.filter()))));
            case VectorFilter.And and -> new Document("compound", new Document("filter", and.filters().stream().map(MongoDbAtlasAclFilterCompiler::searchNode).toList()));
            case VectorFilter.Or or -> new Document("compound", new Document("should", or.filters().stream().map(MongoDbAtlasAclFilterCompiler::searchNode).toList()).append("minimumShouldMatch", 1));
        };
    }
    private static Object number(Object value) { if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue())) throw new UnsupportedOperationException("MongoDB Atlas hybrid ACL range bounds must be finite numbers"); return number; }
    private static String path(String field) { if(field==null||!field.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("invalid MongoDB Atlas metadata field"); return "metadata."+field; }
}

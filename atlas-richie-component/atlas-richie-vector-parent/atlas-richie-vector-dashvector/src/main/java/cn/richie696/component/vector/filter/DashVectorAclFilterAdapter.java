package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.springframework.ai.vectorstore.filter.Filter;

/** Converts the common ACL tree into the DashVector plugin's typed filter input. */
public final class DashVectorAclFilterAdapter {
    private DashVectorAclFilterAdapter() { }
    public static Filter.Expression toSpring(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> leaf(Filter.ExpressionType.EQ, eq.field(), eq.value());
            case VectorFilter.In in -> leaf(Filter.ExpressionType.IN, in.field(), in.values());
            case VectorFilter.ContainsAny any -> leaf(Filter.ExpressionType.IN, any.field(), any.values());
            case VectorFilter.Range r -> r.greaterThanOrEqual() == null ? leaf(Filter.ExpressionType.LTE,r.field(),r.lessThanOrEqual()) : r.lessThanOrEqual() == null ? leaf(Filter.ExpressionType.GTE,r.field(),r.greaterThanOrEqual()) : new Filter.Expression(Filter.ExpressionType.AND,leaf(Filter.ExpressionType.GTE,r.field(),r.greaterThanOrEqual()),leaf(Filter.ExpressionType.LTE,r.field(),r.lessThanOrEqual()));
            case VectorFilter.Exists exists -> new Filter.Expression(Filter.ExpressionType.ISNOTNULL,new Filter.Key(exists.field()));
            case VectorFilter.Not not -> new Filter.Expression(Filter.ExpressionType.NOT,toSpring(not.filter()));
            case VectorFilter.And and -> combine(Filter.ExpressionType.AND,and.filters());
            case VectorFilter.Or or -> combine(Filter.ExpressionType.OR,or.filters());
        };
    }
    private static Filter.Expression leaf(Filter.ExpressionType type,String field,Object value){return new Filter.Expression(type,new Filter.Key(field),new Filter.Value(value));}
    private static Filter.Expression combine(Filter.ExpressionType type,java.util.List<VectorFilter> filters){Filter.Expression out=toSpring(filters.getFirst());for(int i=1;i<filters.size();i++)out=new Filter.Expression(type,out,toSpring(filters.get(i)));return out;}
}

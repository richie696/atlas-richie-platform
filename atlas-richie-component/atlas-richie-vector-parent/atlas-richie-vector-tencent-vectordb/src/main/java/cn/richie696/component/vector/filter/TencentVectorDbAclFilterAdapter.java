package cn.richie696.component.vector.filter;
import cn.richie696.component.vector.model.VectorFilter;
import org.springframework.ai.vectorstore.filter.Filter;
/** Typed common-filter adapter for Tencent VectorDB. */
public final class TencentVectorDbAclFilterAdapter {
    private TencentVectorDbAclFilterAdapter() { }
    public static Filter.Expression toSpring(VectorFilter filter) { return switch(filter) {
        case VectorFilter.Eq e -> leaf(Filter.ExpressionType.EQ,e.field(),e.value()); case VectorFilter.In e -> leaf(Filter.ExpressionType.IN,e.field(),e.values()); case VectorFilter.ContainsAny e -> leaf(Filter.ExpressionType.IN,e.field(),e.values());
        case VectorFilter.Range r -> r.greaterThanOrEqual()==null?leaf(Filter.ExpressionType.LTE,r.field(),r.lessThanOrEqual()):r.lessThanOrEqual()==null?leaf(Filter.ExpressionType.GTE,r.field(),r.greaterThanOrEqual()):new Filter.Expression(Filter.ExpressionType.AND,leaf(Filter.ExpressionType.GTE,r.field(),r.greaterThanOrEqual()),leaf(Filter.ExpressionType.LTE,r.field(),r.lessThanOrEqual()));
        case VectorFilter.Exists e -> new Filter.Expression(Filter.ExpressionType.ISNOTNULL,new Filter.Key(e.field())); case VectorFilter.Not n -> new Filter.Expression(Filter.ExpressionType.NOT,toSpring(n.filter())); case VectorFilter.And a -> combine(Filter.ExpressionType.AND,a.filters()); case VectorFilter.Or o -> combine(Filter.ExpressionType.OR,o.filters()); }; }
    private static Filter.Expression leaf(Filter.ExpressionType t,String f,Object v){return new Filter.Expression(t,new Filter.Key(f),new Filter.Value(v));}
    private static Filter.Expression combine(Filter.ExpressionType t,java.util.List<VectorFilter> xs){Filter.Expression out=toSpring(xs.getFirst());for(int i=1;i<xs.size();i++)out=new Filter.Expression(t,out,toSpring(xs.get(i)));return out;}
}

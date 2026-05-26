package com.richie.component.search.model;

import com.richie.component.search.enums.QueryType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class NativeQueryWrapper<T> extends AbstractQueryWrapper<T, NativeQueryWrapper<T>> {

    /**
     * 原生查询
     */
    protected Query nativeQuery;

    public NativeQueryWrapper(Class<T> entityClass, String indexOrCollection) {
        super(entityClass, indexOrCollection);
    }

    public NativeQueryWrapper(Class<T> entityClass, String indexOrCollection, Query nativeQuery) {
        super(entityClass, indexOrCollection);
        this.nativeQuery = nativeQuery;
    }

    public static <T> NativeQueryWrapper<T> create(Class<T> entityClass, String indexOrCollection) {
        return new NativeQueryWrapper<>(entityClass, indexOrCollection);
    }

    public static <T> NativeQueryWrapper<T> create(Class<T> entityClass, String indexOrCollection, Query nativeQuery) {
        return new NativeQueryWrapper<>(entityClass, indexOrCollection, nativeQuery);
    }

    /**
     * 原生查询
     */
    public NativeQueryWrapper<T> nativeQuery(Query query) {
        this.nativeQuery = query;
        return this;
    }


    @Override
    public SearchQuery<T> build() {
        return super.build()
                .setType(QueryType.NATIVE)
                .setNativeQuery(this.nativeQuery);
    }
}

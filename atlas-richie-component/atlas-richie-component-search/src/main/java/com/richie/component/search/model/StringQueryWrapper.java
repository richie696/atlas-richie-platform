package com.richie.component.search.model;

import com.richie.component.search.enums.QueryType;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
public class StringQueryWrapper<T> extends AbstractQueryWrapper<T, StringQueryWrapper<T>> {

    /**
     * 原生查询
     */
    protected String stringQuery;

    public StringQueryWrapper(Class<T> entityClass, String indexOrCollection) {
        super(entityClass, indexOrCollection);
    }

    public StringQueryWrapper(Class<T> entityClass, String indexOrCollection, String stringQuery) {
        super(entityClass, indexOrCollection);
        this.stringQuery = stringQuery;
    }

    public static <T> StringQueryWrapper<T> create(Class<T> entityClass, String indexOrCollection) {
        return new StringQueryWrapper<>(entityClass, indexOrCollection);
    }

    public static <T> StringQueryWrapper<T> create(Class<T> entityClass, String indexOrCollection, String stringQuery) {
        return new StringQueryWrapper<>(entityClass, indexOrCollection, stringQuery);
    }

    /**
     * 原生查询
     */
    public StringQueryWrapper<T> stringQuery(String query) {
        this.stringQuery = query;
        return this;
    }

    @Override
    public SearchQuery<T> build() {
        return super.build()
                .setType(QueryType.STRING)
                .setStringQuery(this.stringQuery);
    }
}

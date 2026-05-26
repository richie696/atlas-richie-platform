package com.richie.component.search.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Data
public abstract class AbstractQueryWrapper<T, R extends AbstractQueryWrapper<T, R>> {

    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.NONE)
    protected Class<T> entityClass;

    /**
     * 索引或集合名称
     */
    protected String indexOrCollection;

    /**
     * 排序条件
     */
    protected Map<String, String> sort = new LinkedHashMap<>();

    /**
     * 分页参数
     */
    protected int page = 0;
    protected int size = 10;

    /**
     * 分页查询中获取精确的总数
     */
    private Boolean trackTotalHits;

    /**
     * 高亮字段
     */
    protected List<String> highlightFields = new ArrayList<>();


    /**
     * 建议查询配置
     */
    protected Map<String, Object> suggestions = new HashMap<>();

    // ==================== 构造函数 ====================

    public AbstractQueryWrapper(Class<T> entityClass, String indexOrCollection) {
        this.entityClass = entityClass;
        this.indexOrCollection = indexOrCollection;
        validateEntityClass(entityClass);
    }

    // ==================== 便捷创建方法 ====================

    public R page(int page, int size) {
        this.page = page;
        this.size = size;
        return (R) this;
    }

    /**
     * 设置索引或集合名称
     */
    public R index(String indexOrCollection) {
        this.indexOrCollection = indexOrCollection;
        return (R) this;
    }

    public R setTrackTotalHits() {
        this.trackTotalHits = true;
        return (R) this;
    }

    /**
     * 验证实体类型
     *
     * <p>只允许 Map 或 Domain 对象。
     *
     * @param entityClass 实体类型
     * @throws IllegalArgumentException 当实体类型不符合要求时抛出
     */
    protected void validateEntityClass(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("实体类型不能为null");
        }

        // 允许 Map 类型
        if (Map.class.isAssignableFrom(entityClass)) {
            return;
        }

        // 检查是否为普通对象类型
        if (entityClass.isInterface() || entityClass.isArray() || entityClass.isPrimitive()) {
            throw new IllegalArgumentException("实体类型必须是 Map 或 Domain 对象");
        }
    }


    /**
     * 构建 SearchQuery 对象
     */
    public SearchQuery<T> build() {
        SearchQuery<T> query = new SearchQuery<>();
        query.setIndexOrCollection(this.indexOrCollection);
        query.setPage(this.page);
        query.setSize(this.size);
        query.setSort(this.sort);
        query.setHighlightFields(this.highlightFields);
        query.setSuggestions(this.suggestions);
        query.setTrackTotalHits(this.trackTotalHits);
        // 直接设置查询条件列表，不需要类型转换
        query.setEntityClass(entityClass);

        return query;
    }
}

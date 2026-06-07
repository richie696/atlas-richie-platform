package com.richie.component.mongodb.builder;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {
    private List<T> content;
    private long total;
    private int pageNum;
    private int pageSize;

    public int getTotalPages() {
        return (int) Math.ceil((double) total / pageSize);
    }
}

/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.vector.service;

/** Optional Store-bound sparse encoder. The same implementation encodes indexed text and queries. */
@FunctionalInterface
public interface SparseVectorizer {
    SparseVector encode(String text);
}

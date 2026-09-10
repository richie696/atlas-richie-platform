package cn.richie696.component.vector.service;

/** Optional provider signal used only for payload-free ACL-safe hybrid observability. */
public interface HybridSearchExecutionMode {
    /** Returns {@code native}, {@code core-rrf}, or {@code unknown}. */
    String hybridExecutionMode();
}

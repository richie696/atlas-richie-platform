/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

/** Sanitized configured/effective/applied evidence for one option. */
public record VectorParameterEvidence(
        String name,
        String requested,
        String configured,
        String effective,
        String applied,
        VectorParameterSource source,
        VectorParameterDisposition disposition,
        String evidence) {

    public VectorParameterEvidence {
        if (name == null || name.isBlank() || source == null || disposition == null
                || evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("vector parameter evidence is incomplete");
        }
    }
}

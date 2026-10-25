package com.txscope.core.model.pool;

/**
 * A single observed fact. No interpretation — only what was measured.
 */
public class Fact {

    private final String id;
    private final String text;
    private final String source;

    public Fact(String id, String text, String source) {
        this.id = id;
        this.text = text;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getSource() {
        return source;
    }
}

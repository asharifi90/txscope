package com.txscope.core.model;


import java.util.List;

/**
 * A diagnostic hypothesis. Explicitly not a fact — labelled with confidence.
 */
public class Hypothesis {

    private final String id;
    private final String summary;
    private final String confidence; // low | medium | high
    private final List<String> supports; // fact ids
    private final List<String> nextSteps;

    public Hypothesis(
            String id,
            String summary,
            String confidence,
            List<String> supports,
            List<String> nextSteps) {
        this.id = id;
        this.summary = summary;
        this.confidence = confidence;
        this.supports = supports != null ? List.copyOf(supports) : List.of();
        this.nextSteps = nextSteps != null ? List.copyOf(nextSteps) : List.of();
    }

    public String getId() {
        return id;
    }

    public String getSummary() {
        return summary;
    }

    public String getConfidence() {
        return confidence;
    }

    public List<String> getSupports() {
        return supports;
    }

    public List<String> getNextSteps() {
        return nextSteps;
    }
}

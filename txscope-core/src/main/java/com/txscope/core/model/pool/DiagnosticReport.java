package com.txscope.core.model.pool;

import com.txscope.core.model.Hypothesis;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Full diagnostic report: pool + facts + hypotheses + gaps.
 */
public class DiagnosticReport {

    private final Instant generatedAt;
    private final PoolSnapshot pool;
    private final List<Fact> facts;
    private final List<Hypothesis> hypotheses;
    private final List<String> insufficientEvidence;

    public DiagnosticReport(
            Instant generatedAt,
            PoolSnapshot pool,
            List<Fact> facts,
            List<Hypothesis> hypotheses,
            List<String> insufficientEvidence) {
        this.generatedAt = generatedAt;
        this.pool = pool;
        this.facts = facts != null ? List.copyOf(facts) : List.of();
        this.hypotheses = hypotheses != null ? List.copyOf(hypotheses) : List.of();
        this.insufficientEvidence = insufficientEvidence != null ? List.copyOf(insufficientEvidence) : List.of();
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public PoolSnapshot getPool() {
        return pool;
    }

    public List<Fact> getFacts() {
        return facts;
    }

    public List<Hypothesis> getHypotheses() {
        return hypotheses;
    }

    public List<String> getInsufficientEvidence() {
        return insufficientEvidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Instant generatedAt = Instant.now();
        private PoolSnapshot pool;
        private final List<Fact> facts = new ArrayList<>();
        private final List<Hypothesis> hypotheses = new ArrayList<>();
        private final List<String> insufficientEvidence = new ArrayList<>();

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder pool(PoolSnapshot pool) {
            this.pool = pool;
            return this;
        }

        public Builder addFact(Fact fact) {
            this.facts.add(fact);
            return this;
        }

        public Builder facts(List<Fact> facts) {
            this.facts.clear();
            if (facts != null) {
                this.facts.addAll(facts);
            }
            return this;
        }

        public Builder addHypothesis(Hypothesis hypothesis) {
            this.hypotheses.add(hypothesis);
            return this;
        }

        public Builder hypotheses(List<Hypothesis> hypotheses) {
            this.hypotheses.clear();
            if (hypotheses != null) {
                this.hypotheses.addAll(hypotheses);
            }
            return this;
        }

        public Builder addInsufficientEvidence(String message) {
            this.insufficientEvidence.add(message);
            return this;
        }

        public DiagnosticReport build() {
            return new DiagnosticReport(generatedAt, pool, facts, hypotheses, insufficientEvidence);
        }
    }
}

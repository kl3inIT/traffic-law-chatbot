package com.vn.traffic.chatbot.checks.evaluator;

/**
 * Evaluates semantic similarity between a reference answer and an actual answer.
 */
public interface SemanticEvaluator {

    /**
     * Evaluate semantic similarity between a reference and actual answer.
     *
     * @param referenceAnswer the expected correct answer
     * @param actualAnswer    the answer produced by the system under evaluation
     * @return score in [0.0, 1.0] — 0.0 on failure (never throws)
     */
    double evaluate(String referenceAnswer, String actualAnswer);

    /**
     * Evaluate using a specific evaluator model (overrides default).
     */
    default double evaluate(String referenceAnswer, String actualAnswer, String evaluatorModelId) {
        return evaluate(referenceAnswer, actualAnswer);
    }
}

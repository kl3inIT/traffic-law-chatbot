package com.vn.traffic.chatbot.chat.regression;

/**
 * Phase 7 refusal-rate baseline — NOT RECOVERABLE from P7 artifacts at Plan 08-01 execution time.
 *
 * <p>A grep of {@code .planning/phases/07-chat-latency-foundation/07-SUMMARY.md} and
 * {@code 07-SMOKE-REPORT.md} returned no aggregate refusal-rate percentage. P7 SMOKE-REPORT
 * row 3 only confirms that one legal query returned a grounded answer (not a refusal);
 * no 20-query sweep was executed in P7. See Plan 08-01 SUMMARY §Task 4 for the decision trace.
 *
 * <p>TODO (Plan 08-04 Task 1, post-live-run): once {@code VietnameseRegressionIT} runs live,
 * replace {@link #REFUSAL_RATE_PERCENT} with the observed P7 baseline — EITHER backfill the
 * number retroactively by running the 20-query suite against the pre-P8 main branch
 * (commit {@code HEAD^} on merge), OR redefine the ±10% parity check as "refusal rate is
 * within [0%, 40%]" (absolute band, not drift).
 *
 * <p>Using {@link Double#NaN} here so any comparison ({@code Math.abs(rate - NaN)} → NaN,
 * which is never {@code <= 10}) FAILS LOUD rather than passes vacuously. This preserves the
 * SC5 parity-check contract without inventing a fake baseline.
 *
 * @see VietnameseRegressionIT#refusalRateWithinTenPercentOfPhase7Baseline()
 */
public final class Phase7Baseline {

    /**
     * Phase-7 refusal-rate baseline, in percent [0, 100]. {@link Double#NaN} signals
     * "not yet recovered"; Plan 08-04 must replace this before running the parity check.
     */
    public static final double REFUSAL_RATE_PERCENT = Double.NaN;

    private Phase7Baseline() {}
}

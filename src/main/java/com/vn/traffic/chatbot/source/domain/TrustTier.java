package com.vn.traffic.chatbot.source.domain;

/**
 * Trust tier classification for knowledge base sources.
 *
 * <p>Trust tier is additive metadata only. It does NOT affect the retrieval gate
 * (approvalState == APPROVED &amp;&amp; trustedState == TRUSTED &amp;&amp; status == ACTIVE).
 * Tier assignment helps with citation grounding decisions in citation display logic,
 * but never bypasses the approval/trusted/active gate.
 */
public enum TrustTier {
    PRIMARY,
    SECONDARY,
    MANUAL_REVIEW
}

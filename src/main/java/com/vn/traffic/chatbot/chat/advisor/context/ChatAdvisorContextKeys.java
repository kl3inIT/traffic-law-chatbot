package com.vn.traffic.chatbot.chat.advisor.context;

/**
 * Shared advisor-chain context keys for the Phase-9 modular RAG pipeline.
 * Used by CitationPostProcessor (labelNumber metadata) and CitationStashAdvisor
 * (citations/sources passback) and read by ChatService post-call.
 */
public final class ChatAdvisorContextKeys {

    public static final String CITATIONS_KEY = "chat.rag.citations";
    public static final String SOURCES_KEY = "chat.rag.sources";
    public static final String LABEL_NUMBER_METADATA = "labelNumber";

    private ChatAdvisorContextKeys() {
    }
}

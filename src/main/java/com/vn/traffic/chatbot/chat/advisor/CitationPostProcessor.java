package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-augmentation DocumentPostProcessor that stamps 1..n labelNumber onto
 * retrieved Document metadata (D-04, Pitfall 4). Does not mutate input
 * metadata; emits new Document instances with fresh HashMap copies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class CitationPostProcessor implements DocumentPostProcessor {

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Document> labeled = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            Map<String, Object> md = new HashMap<>(d.getMetadata());
            md.put(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA, i + 1);
            labeled.add(Document.builder()
                    .id(d.getId())
                    .text(d.getText())
                    .metadata(md)
                    .score(d.getScore())
                    .build());
        }
        return labeled;
    }
}

package com.vn.traffic.chatbot.parameter.service;

import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultParameterSetSeeder implements ApplicationRunner {

    private final AiParameterSetRepository repository;
    private final ResourceLoader resourceLoader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (repository.count() == 0) {
            String content = loadDefaultContent();
            AiParameterSet defaultSet = AiParameterSet.builder()
                    .name("Bộ tham số mặc định")
                    .content(content)
                    .active(true)
                    .build();
            repository.save(defaultSet);
            log.info("Seeded default AI parameter set");
        } else {
            log.debug("AI parameter sets already exist, skipping seed");
        }
    }

    private String loadDefaultContent() throws IOException {
        var resource = resourceLoader.getResource("classpath:default-parameter-set.yml");
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

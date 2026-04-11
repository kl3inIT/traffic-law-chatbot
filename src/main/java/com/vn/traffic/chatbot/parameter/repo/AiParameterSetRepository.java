package com.vn.traffic.chatbot.parameter.repo;

import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiParameterSetRepository extends JpaRepository<AiParameterSet, UUID> {

    Optional<AiParameterSet> findByActiveTrue();

    List<AiParameterSet> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("UPDATE AiParameterSet p SET p.active = false")
    int deactivateAll();
}

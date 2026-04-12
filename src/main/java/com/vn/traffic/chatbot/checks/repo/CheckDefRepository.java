package com.vn.traffic.chatbot.checks.repo;

import com.vn.traffic.chatbot.checks.domain.CheckDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CheckDefRepository extends JpaRepository<CheckDef, UUID> {

    List<CheckDef> findByActiveTrue();

    List<CheckDef> findAllByOrderByCreatedAtDesc();
}

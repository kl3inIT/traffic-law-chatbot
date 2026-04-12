package com.vn.traffic.chatbot.checks.repo;

import com.vn.traffic.chatbot.checks.domain.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

    List<CheckResult> findByCheckRunId(UUID checkRunId);
}

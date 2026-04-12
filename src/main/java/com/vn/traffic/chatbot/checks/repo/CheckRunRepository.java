package com.vn.traffic.chatbot.checks.repo;

import com.vn.traffic.chatbot.checks.domain.CheckRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CheckRunRepository extends JpaRepository<CheckRun, UUID> {

    List<CheckRun> findAllByOrderByCreatedDateDesc();
}

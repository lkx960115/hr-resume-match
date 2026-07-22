package com.hr.resumematch.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateRepository extends JpaRepository<CandidateEntity, Long> {
    List<CandidateEntity> findByJobIdOrderByIdAsc(Long jobId);
}

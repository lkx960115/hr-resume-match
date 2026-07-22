package com.hr.resumematch.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchReportRepository extends JpaRepository<MatchReportEntity, Long> {
    List<MatchReportEntity> findByJobIdOrderByTotalScoreDesc(Long jobId);

    Optional<MatchReportEntity> findByJobIdAndCandidateId(Long jobId, Long candidateId);

    void deleteByJobId(Long jobId);
}

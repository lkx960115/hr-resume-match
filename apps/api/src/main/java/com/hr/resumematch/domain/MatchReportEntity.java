package com.hr.resumematch.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "match_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private Long candidateId;

    private boolean passHardGate;

    private Double totalScore;

    /** JSON: dimension scores + evidence */
    @Column(columnDefinition = "CLOB")
    private String detailJson;

    @Column(columnDefinition = "CLOB")
    private String summary;

    /** JSON: interview questions */
    @Column(columnDefinition = "CLOB")
    private String interviewPackJson;

    /** PENDING | RUNNING | DONE | FAILED */
    @Column(nullable = false)
    private String status;

    private String errorMessage;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

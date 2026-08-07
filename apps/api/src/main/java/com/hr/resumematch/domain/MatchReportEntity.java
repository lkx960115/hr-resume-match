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

    /** 是否已发送面试邀约 */
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    @Builder.Default
    private boolean invited = false;

    private Instant invitedAt;

    /** JSON: 面试官评价（保存后锁定） */
    @Column(columnDefinition = "CLOB")
    private String interviewEvaluationJson;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

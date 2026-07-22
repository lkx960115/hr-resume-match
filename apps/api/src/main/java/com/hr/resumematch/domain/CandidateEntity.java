package com.hr.resumematch.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    private String fileName;
    private String storedPath;

    @Column(columnDefinition = "CLOB")
    private String rawText;

    /** PENDING | PARSING | READY | FAILED */
    @Column(nullable = false)
    private String parseStatus;

    private String parseError;

    /** JSON: structured profile */
    @Column(columnDefinition = "CLOB")
    private String profileJson;

    /** JSON: risk flags */
    @Column(columnDefinition = "CLOB")
    private String riskFlagsJson;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

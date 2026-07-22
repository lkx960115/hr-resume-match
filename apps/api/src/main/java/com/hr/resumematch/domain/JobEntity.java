package com.hr.resumematch.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String jdText;

    /** JSON: HardRequirement[] */
    @Column(columnDefinition = "CLOB")
    private String hardRequirementsJson;

    /** JSON: DimensionWeight[] */
    @Column(columnDefinition = "CLOB")
    private String dimensionsJson;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

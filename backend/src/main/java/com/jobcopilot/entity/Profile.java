package com.jobcopilot.entity;

import com.jobcopilot.entity.enums.RemotePreference;
import com.jobcopilot.entity.enums.Seniority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single user's job-search profile and preferences.
 *
 * <p>Collection-like fields ({@code titles}, {@code locations}, {@code keywords},
 * {@code excludedCompanies}) are stored as JSON strings via a converter to keep the schema
 * simple while remaining queryable at the application layer.</p>
 */
@Entity
@Table(name = "profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(name = "resume_path", length = 1024)
    private String resumePath;

    @Column(name = "linked_in", length = 512)
    private String linkedIn;

    @Column(length = 512)
    private String github;

    @Column(length = 512)
    private String portfolio;

    /** JSON array of desired job titles. */
    @Column(columnDefinition = "TEXT")
    private String titles;

    /** JSON array of desired locations. */
    @Column(columnDefinition = "TEXT")
    private String locations;

    @Column(name = "salary_minimum")
    private Integer salaryMinimum;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_preference", nullable = false, length = 30)
    @Builder.Default
    private RemotePreference remotePreference = RemotePreference.ANY;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Seniority seniority;

    /** JSON array of required keywords. */
    @Column(columnDefinition = "TEXT")
    private String keywords;

    /** JSON array of blacklisted companies. */
    @Column(name = "excluded_companies", columnDefinition = "TEXT")
    private String excludedCompanies;
}

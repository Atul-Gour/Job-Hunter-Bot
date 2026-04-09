package com.jobhunter.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_listings")
@Data
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_job_id", nullable = false)
    private String externalJobId;   // ID from RapidAPI — used for dedup

    private String title;
    private String company;
    private String location;
    private String platform;
    private String url;
    private Integer salary;
    private String employmentType;
    private String requiredExperience;


    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt = LocalDateTime.now();
}
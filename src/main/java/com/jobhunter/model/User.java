package com.jobhunter.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data                             // Lombok: generates getters, setters, toString automatically
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;
    private String phone;

    @Column(name = "telegram_chat_id", unique = true)
    private Long telegramChatId;

    @Column(name = "resume_link")
    private String resumeLink;

    private String skills;
    private String location;

    @Column(name = "keyword_filters")
    private String keywordFilters;

    @Column(name = "salary_filter")
    private Integer salaryFilter;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "notifications_paused")
    private Boolean notificationsPaused = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
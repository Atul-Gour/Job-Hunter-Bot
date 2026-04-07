package com.jobhunter.repository;

import com.jobhunter.model.Application;
import com.jobhunter.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByUserOrderByCreatedAtDesc(User user);
    long countByUserAndStatus(User user, Application.ApplicationStatus status);
}
package com.jobhunter.repository;

import com.jobhunter.model.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JobListingRepository extends JpaRepository<JobListing, Long> {
    Optional<JobListing> findByExternalJobId(String externalJobId);
}
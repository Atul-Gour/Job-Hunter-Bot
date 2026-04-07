package com.jobhunter.controller;

import com.jobhunter.service.JobFetcherService;
import com.jobhunter.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobFetcherService jobFetcherService;
    private final UserService userService;

    // manual trigger for testing
    @PostMapping("/fetch")
    public ResponseEntity<String> triggerFetch(Authentication auth) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        jobFetcherService.fetchJobsForUser(userId);
        return ResponseEntity.ok("Fetch triggered for user: " + userId);
    }
}
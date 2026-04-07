package com.jobhunter.service;

import com.jobhunter.config.RabbitMQConfig;
import com.jobhunter.dto.JobDto;
import com.jobhunter.dto.JobNotificationMessage;
import com.jobhunter.model.JobListing;
import com.jobhunter.model.User;
import com.jobhunter.repository.JobListingRepository;
import com.jobhunter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobFetcherService {

    private final WebClient.Builder webClientBuilder;
    private final JobListingRepository jobListingRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${RAPIDAPI_KEY}")
    private String rapidApiKey;

    @Value("${RAPIDAPI_HOST}")
    private String rapidApiHost;

    // called by Quartz scheduler for each user
    public void fetchJobsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (Boolean.TRUE.equals(user.getNotificationsPaused())) {
            log.info("Notifications paused for user {}, skipping fetch", userId);
            return;
        }

        String keywords = user.getKeywordFilters() != null
                ? user.getKeywordFilters() : "software developer";
        String location = user.getLocation() != null
                ? user.getLocation() : "India";

        log.info("Fetching jobs for user {} with keywords: {}", userId, keywords);

        // fetch from API
        List<JobDto> jobs = fetchFromJSearch(keywords, location);

        int newJobsFound = 0;
        for (JobDto job : jobs) {
            // skip if already seen
            if (redisService.isJobAlreadySeen(userId, job.getExternalJobId())) {
                continue;
            }

            // apply salary filter
            if (user.getSalaryFilter() != null && job.getSalary() != null
                    && job.getSalary() < user.getSalaryFilter()) {
                continue;
            }

            // save to DB
            JobListing listing = saveJobListing(job);

            // mark as seen in Redis
            redisService.markJobAsSeen(userId, job.getExternalJobId());

            newJobsFound++;
            log.info("New job found for user {}: {}", userId, job.getTitle());

            JobNotificationMessage notifMsg = new JobNotificationMessage(
                    userId,
                    listing.getId(),
                    listing.getTitle(),
                    listing.getCompany(),
                    listing.getLocation(),
                    listing.getPlatform(),
                    listing.getUrl(),
                    listing.getSalary(),
                    trimDescription(listing.getDescription())
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.JOB_EXCHANGE,
                    RabbitMQConfig.NEW_ROUTING_KEY,
                    notifMsg
            );
        }

        log.info("Fetch complete for user {}. {} new jobs found.", userId, newJobsFound);
    }

    private List<JobDto> fetchFromJSearch(String keywords, String location) {
        try {
            WebClient client = webClientBuilder.build();

            // call JSearch API
            Map response = client.get()
                    .uri("https://jsearch.p.rapidapi.com/search?query={q}&location={loc}&num_pages=1",
                            keywords + " " + location, location)
                    .header("X-RapidAPI-Key", rapidApiKey)
                    .header("X-RapidAPI-Host", rapidApiHost)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(); // blocking for now — Phase 4 makes this async

            List<JobDto> jobs = new ArrayList<>();
            if (response != null && response.containsKey("data")) {
                List<Map> data = (List<Map>) response.get("data");
                for (Map item : data) {
                    JobDto dto = new JobDto();
                    dto.setExternalJobId(String.valueOf(item.get("job_id")));
                    dto.setTitle(String.valueOf(item.get("job_title")));
                    dto.setCompany(String.valueOf(item.get("employer_name")));
                    dto.setLocation(String.valueOf(item.get("job_city")));
                    dto.setPlatform("JSEARCH");
                    dto.setUrl(String.valueOf(item.get("job_apply_link")));
                    dto.setDescription(String.valueOf(item.get("job_description")));
                    jobs.add(dto);
                }
            }
            return jobs;

        } catch (Exception e) {
            log.error("Error fetching jobs from JSearch: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private JobListing saveJobListing(JobDto dto) {
        // check if already in DB
        return jobListingRepository.findByExternalJobId(dto.getExternalJobId())
                .orElseGet(() -> {
                    JobListing listing = new JobListing();
                    listing.setExternalJobId(dto.getExternalJobId());
                    listing.setTitle(dto.getTitle());
                    listing.setCompany(dto.getCompany());
                    listing.setLocation(dto.getLocation());
                    listing.setPlatform(dto.getPlatform());
                    listing.setUrl(dto.getUrl());
                    listing.setDescription(dto.getDescription());
                    listing.setSalary(dto.getSalary());
                    return jobListingRepository.save(listing);
                });
    }
    private String trimDescription(String desc) {
        if (desc == null) return "No description available";

        return desc.length() > 200
                ? desc.substring(0, 200) + "..."
                : desc;
    }
}
package com.jobhunter.scheduler;

import com.jobhunter.service.JobFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobFetchQuartzJob extends QuartzJobBean {

    private final JobFetcherService jobFetcherService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        Long userId = context.getMergedJobDataMap().getLong("userId");
        log.info("Quartz trigger fired for userId: {}", userId);
        jobFetcherService.fetchJobsForUser(userId);
    }
}

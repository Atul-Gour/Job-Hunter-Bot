package com.jobhunter.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final Scheduler scheduler;

    // schedule a fetch job for a user — default every 30 min
    public void scheduleUserJob(Long userId) {
        scheduleUserJob(userId, "0 */30 * * * ?");
    }

    public void scheduleUserJob(Long userId, String cronExpression) {
        try {
            JobKey jobKey = JobKey.jobKey("fetch-job-" + userId, "job-fetch");

            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }

            JobDetail jobDetail = JobBuilder.newJob(JobFetchQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("userId", userId)
                    .storeDurably()
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + userId, "job-fetch")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled fetch job for userId {} with cron: {}", userId, cronExpression);

        } catch (SchedulerException e) {
            log.error("Failed to schedule job for userId {}: {}", userId, e.getMessage());
        }
    }

    public void pauseUserJob(Long userId) {
        try {
            scheduler.pauseJob(JobKey.jobKey("fetch-job-" + userId, "job-fetch"));
            log.info("Paused job for userId {}", userId);
        } catch (SchedulerException e) {
            log.error("Failed to pause job: {}", e.getMessage());
        }
    }

    public void resumeUserJob(Long userId) {
        try {
            scheduler.resumeJob(JobKey.jobKey("fetch-job-" + userId, "job-fetch"));
            log.info("Resumed job for userId {}", userId);
        } catch (SchedulerException e) {
            log.error("Failed to resume job: {}", e.getMessage());
        }
    }

    public void deleteUserJob(Long userId) {
        try {
            scheduler.deleteJob(JobKey.jobKey("fetch-job-" + userId, "job-fetch"));
        } catch (SchedulerException e) {
            log.error("Failed to delete job: {}", e.getMessage());
        }
    }
}
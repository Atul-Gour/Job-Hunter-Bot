package com.jobhunter.worker;

import com.jobhunter.config.RabbitMQConfig;
import com.jobhunter.dto.JobApplyMessage;
import com.jobhunter.model.Application;
import com.jobhunter.model.JobListing;
import com.jobhunter.model.User;
import com.jobhunter.repository.ApplicationRepository;
import com.jobhunter.repository.JobListingRepository;
import com.jobhunter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyWorker {

    private final UserRepository userRepository;
    private final JobListingRepository jobListingRepository;
    private final ApplicationRepository applicationRepository;

    @RabbitListener(queues = RabbitMQConfig.JOB_APPLY_QUEUE)
    public void handleApply(JobApplyMessage message) {
        log.info("Processing apply for user {} job {}", message.getUserId(), message.getJobId());

        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        JobListing job = jobListingRepository.findById(message.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found"));

        // find or create application record
        Application application = new Application();
        application.setUser(user);
        application.setJob(job);
        application.setStatus(Application.ApplicationStatus.PENDING);
        applicationRepository.save(application);

        ChromeDriver driver = null;
        try {
            driver = createHeadlessDriver();
            applyToJob(driver, message, application);

        } catch (Exception e) {
            log.error("Apply failed for job {}: {}", message.getJobId(), e.getMessage());
            application.setStatus(Application.ApplicationStatus.FAILED);
            application.setErrorMessage(e.getMessage());
            applicationRepository.save(application);
            throw new RuntimeException(e); // re-throw for DLQ

        } finally {
            if (driver != null) {
                driver.quit(); // always close browser
            }
        }
    }

    private ChromeDriver createHeadlessDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");          // no visible window
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        return new ChromeDriver(options);
    }

    private void applyToJob(ChromeDriver driver, JobApplyMessage message,
                            Application application) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.get(message.getJobUrl());

        // try to fill common form fields
        tryFillField(driver, wait, "name", message.getUserName());
        tryFillField(driver, wait, "email", message.getUserEmail());
        tryFillField(driver, wait, "phone", message.getUserPhone());

        // try to submit
        try {
            WebElement submitBtn = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("button[type='submit'], input[type='submit']")));
            submitBtn.click();

            // wait a moment for submission
            Thread.sleep(2000);

            application.setStatus(Application.ApplicationStatus.APPLIED);
            application.setAppliedAt(LocalDateTime.now());
            applicationRepository.save(application);
            log.info("Successfully applied to job {}", message.getJobId());

        } catch (Exception e) {
            throw new RuntimeException("Could not find or click submit button: " + e.getMessage());
        }
    }

    private void tryFillField(ChromeDriver driver, WebDriverWait wait,
                              String fieldName, String value) {
        if (value == null) return;
        try {
            WebElement field = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("input[name*='" + fieldName + "'], " +
                                    "input[placeholder*='" + fieldName + "']")));
            field.clear();
            field.sendKeys(value);
        } catch (Exception e) {
            log.warn("Could not fill field '{}': {}", fieldName, e.getMessage());
        }
    }
}
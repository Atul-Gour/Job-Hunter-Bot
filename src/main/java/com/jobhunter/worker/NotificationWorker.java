package com.jobhunter.worker;

import com.jobhunter.bot.JobHunterBot;
import com.jobhunter.config.RabbitMQConfig;
import com.jobhunter.dto.JobNotificationMessage;
import com.jobhunter.model.User;
import com.jobhunter.repository.UserRepository;
import com.jobhunter.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWorker {

    private final UserRepository userRepository;
    private final JobHunterBot jobHunterBot;
    private final RedisService redisService;

    @RabbitListener(queues = RabbitMQConfig.JOB_NEW_QUEUE)
    public void handleNewJob(JobNotificationMessage message) {
        try {
            User user = userRepository.findById(message.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.getTelegramChatId() == null) {
                log.warn("User {} has no telegram chat ID linked", message.getUserId());
                return;
            }

            // check rate limit
            if (redisService.isRateLimited(message.getUserId())) {
                log.warn("Rate limit hit for user {}", message.getUserId());
                return;
            }

            // build message text
            String salaryLabel = message.getSalary() != null
                    ? String.valueOf(message.getSalary())
                    : "Not disclosed";

            String text = String.format("""
                    New Job Alert!
                    
                    Company: %s
                    Role: %s
                    Location: %s
                    Salary: %s
                    Employment : %s
                    Experience Required: %s
                    Description: %s
                    
                    What would you like to do?
                    """,
                    message.getCompany(),
                    message.getTitle(),
                    message.getLocation(),
                    salaryLabel,
                    message.getEmploymentType(),
                    message.getRequiredExperience(),
                    message.getDescription()
            );

            // inline keyboard buttons
            InlineKeyboardButton applyBtn = InlineKeyboardButton.builder()
                    .text("Apply Now")
                    .callbackData("apply:" + message.getJobId())
                    .build();

            InlineKeyboardButton skipBtn = InlineKeyboardButton.builder()
                    .text("Skip")
                    .callbackData("skip:" + message.getJobId())
                    .build();

            InlineKeyboardButton saveBtn = InlineKeyboardButton.builder()
                    .text("Save for Later")
                    .callbackData("save:" + message.getJobId())
                    .build();

            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(applyBtn, skipBtn, saveBtn))
                    .build();

            SendMessage sendMessage = SendMessage.builder()
                    .chatId(user.getTelegramChatId().toString())
                    .text(text)
                    .replyMarkup(keyboard)
                    .build();

            jobHunterBot.execute(sendMessage);
            log.info("Notification sent to user {} for job {}", message.getUserId(), message.getJobId());

        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            throw new RuntimeException(e); // re-throw so RabbitMQ retries
        }
    }
}
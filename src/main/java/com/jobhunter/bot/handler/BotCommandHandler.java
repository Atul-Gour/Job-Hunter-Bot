package com.jobhunter.bot.handler;

import com.jobhunter.config.RabbitMQConfig;
import com.jobhunter.dto.JobApplyMessage;
import com.jobhunter.model.Application;
import com.jobhunter.model.JobListing;
import com.jobhunter.model.User;
import com.jobhunter.repository.ApplicationRepository;
import com.jobhunter.repository.JobListingRepository;
import com.jobhunter.repository.UserRepository;
import com.jobhunter.scheduler.SchedulerService;
import com.jobhunter.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotCommandHandler {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final JobListingRepository jobListingRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ProfileSetupHandler profileSetupHandler;
    private final RegistrationHandler registrationHandler;
    private final SchedulerService schedulerService;
    private final RedisService redisService;

    public void handle(Update update, AbsSender bot) throws TelegramApiException {

        // handle inline button clicks
        if (update.hasCallbackQuery()) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = update.getCallbackQuery().getData();
            handleCallback(chatId, data, bot);
            return;
        }

        // handle text messages
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();

            String firstName = "User"; // fallback

            if (update.hasMessage() && update.getMessage().getFrom() != null) {
                firstName = update.getMessage().getFrom().getFirstName();
            }

            // check if user is in registration flow
            boolean registrationHandled = registrationHandler.handleIfInRegistration(chatId, text, bot);
            if (registrationHandled) return;

            // check if user is mid profile setup
            boolean setupHandled = profileSetupHandler.handleIfInSetup(chatId, text, bot);
            if (setupHandled) return;

            // /start can include a deep-link token, e.g., "/start ABC123DEF"
            if (text.equals("/start") || text.startsWith("/start ")) {
                handleStart(chatId, firstName, text, bot);
                return;
            }

            // route other commands
            switch (text) {
                case "/setup"     -> profileSetupHandler.startSetup(chatId, bot);
                case "/myprofile" -> handleMyProfile(chatId, bot);
                case "/status"    -> handleStatus(chatId, bot);
                case "/pause"     -> handlePause(chatId, bot);
                case "/resume"    -> handleResume(chatId, bot);
                case "/help"      -> handleHelp(chatId, bot);
                default           -> sendText(chatId,
                        "Unknown command. Send /help to see all commands.", bot);
            }
        }
    }

    private void handleStart(Long chatId, String firstName,
                             String fullText, AbsSender bot) throws TelegramApiException {

        // check if /start came with a deep link token
        // Telegram sends it as "/start TOKENVALUE"
        if (fullText.startsWith("/start ")) {
            String token = fullText.substring(7).trim();
            handleLinkToken(chatId, token, bot);
            return;
        }

        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isPresent()) {
            // User already registered and linked
            sendMainMenu(chatId, "Welcome back, "
                    + userOpt.get().getName() + "!", bot);
        } else {
            // User not registered - send link to signup page
            sendText(chatId, """
                    Welcome 👋
                    
                    Please sign up or login on our website first:
                    
                    🌐 http://localhost:8080/index.html
                    
                    Then click "Connect Telegram" in your dashboard to link your account.
                    
                    📱 After linking, you can use all the bot features!
                    """, bot);
        }
    }

    private void handleLinkToken(Long chatId, String token,
                                 AbsSender bot) throws TelegramApiException {
        String email = redisService.getLinkToken(token);

        if (email == null) {
            sendText(chatId, "This link has expired or is invalid.\n\n"
                    + "Please go back to the website and click "
                    + "Connect Telegram again.", bot);
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            sendText(chatId, "Account not found. Please sign up first.", bot);
            return;
        }

        User user = userOpt.get();

        // check if already linked to another account
        if (user.getTelegramChatId() != null
                && !user.getTelegramChatId().equals(chatId)) {
            sendText(chatId, "This account is already linked to a different Telegram.", bot);
            return;
        }

        // link the account
        user.setTelegramChatId(chatId);
        userRepository.save(user);
        redisService.deleteLinkToken(token);

        sendMainMenu(chatId, "Account linked successfully!\n\n"
                + "Welcome, " + user.getName() + "!", bot);
    }

    private void sendMainMenu(Long chatId, String header,
                              AbsSender bot) throws TelegramApiException {
        InlineKeyboardButton setupBtn = InlineKeyboardButton.builder()
                .text("Setup Profile")
                .callbackData("setup:start")
                .build();
        InlineKeyboardButton profileBtn = InlineKeyboardButton.builder()
                .text("My Profile")
                .callbackData("menu:profile")
                .build();
        InlineKeyboardButton statusBtn = InlineKeyboardButton.builder()
                .text("My Applications")
                .callbackData("menu:status")
                .build();
        InlineKeyboardButton pauseBtn = InlineKeyboardButton.builder()
                .text("Pause Alerts")
                .callbackData("menu:pause")
                .build();
        InlineKeyboardButton resumeBtn = InlineKeyboardButton.builder()
                .text("Resume Alerts")
                .callbackData("menu:resume")
                .build();

        bot.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(header + "\n\nWhat would you like to do?")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(setupBtn, profileBtn))
                        .keyboardRow(List.of(statusBtn))
                        .keyboardRow(List.of(pauseBtn, resumeBtn))
                        .build())
                .build());
    }

    private void handleCallback(Long chatId, String data,
                                AbsSender bot) throws TelegramApiException {
        switch (data) {
            case "setup:start"  -> profileSetupHandler.startSetup(chatId, bot);
            case "menu:profile" -> handleMyProfile(chatId, bot);
            case "menu:status"  -> handleStatus(chatId, bot);
            case "menu:pause"   -> handlePause(chatId, bot);
            case "menu:resume"  -> handleResume(chatId, bot);
            default -> {
                if (data.contains(":")) {
                    String[] parts = data.split(":");
                    String action  = parts[0];
                    Long jobId     = Long.parseLong(parts[1]);
                    handleJobAction(chatId, action, jobId, bot);
                }
            }
        }
    }

    private void handleJobAction(Long chatId, String action,
                                 Long jobId, AbsSender bot) throws TelegramApiException {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) {
            sendText(chatId, "Account not linked.", bot);
            return;
        }
        User user = userOpt.get();

        switch (action) {
            case "apply" -> {
                JobListing job = jobListingRepository.findById(jobId).orElseThrow();
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.JOB_EXCHANGE,
                        RabbitMQConfig.APPLY_ROUTING_KEY,
                        new JobApplyMessage(
                                user.getId(), jobId, job.getUrl(),
                                user.getName(), user.getEmail(),
                                user.getPhone(), user.getSkills()
                        ));
                sendText(chatId, "Applying to " + job.getTitle()
                        + " at " + job.getCompany() + "...", bot);
            }
            case "skip" -> sendText(chatId, "Job skipped.", bot);
            case "save" -> sendText(chatId, "Job saved!", bot);
        }
    }

    private void handleMyProfile(Long chatId,
                                 AbsSender bot) throws TelegramApiException {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) {
            sendText(chatId, "Account not linked.", bot);
            return;
        }
        User user = userOpt.get();

        bot.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(String.format("""
                        Your Profile:
                        
                        Name:       %s
                        Phone:      %s
                        Location:   %s
                        Skills:     %s
                        Keywords:   %s
                        Min Salary: %s
                        Resume:     %s
                        """,
                        nullSafe(user.getName()),
                        nullSafe(user.getPhone()),
                        nullSafe(user.getLocation()),
                        nullSafe(user.getSkills()),
                        nullSafe(user.getKeywordFilters()),
                        user.getSalaryFilter() != null
                                ? String.valueOf(user.getSalaryFilter()) : "Not set",
                        nullSafe(user.getResumeLink())))
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(InlineKeyboardButton.builder()
                                .text("Edit Profile")
                                .callbackData("setup:start")
                                .build()))
                        .build())
                .build());
    }

    private void handleStatus(Long chatId,
                              AbsSender bot) throws TelegramApiException {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) {
            sendText(chatId, "Account not linked.", bot);
            return;
        }
        User user = userOpt.get();
        long applied = applicationRepository.countByUserAndStatus(
                user, Application.ApplicationStatus.APPLIED);
        long pending = applicationRepository.countByUserAndStatus(
                user, Application.ApplicationStatus.PENDING);
        long failed  = applicationRepository.countByUserAndStatus(
                user, Application.ApplicationStatus.FAILED);

        sendText(chatId, String.format("""
                Your Application Stats:
                
                Applied:  %d
                Pending:  %d
                Failed:   %d
                Total:    %d
                """, applied, pending, failed,
                applied + pending + failed), bot);
    }

    private void handlePause(Long chatId,
                             AbsSender bot) throws TelegramApiException {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendText(chatId, "Account not linked.", bot); return; }
        User user = userOpt.get();
        user.setNotificationsPaused(true);
        userRepository.save(user);
        schedulerService.pauseUserJob(user.getId());
        sendText(chatId, "Notifications paused. Tap Resume Alerts to turn back on.", bot);
    }

    private void handleResume(Long chatId,
                              AbsSender bot) throws TelegramApiException {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendText(chatId, "Account not linked.", bot); return; }
        User user = userOpt.get();
        user.setNotificationsPaused(false);
        userRepository.save(user);
        schedulerService.scheduleUserJob(user.getId());
        sendText(chatId, "Notifications resumed! Job alerts are back on.", bot);
    }

    private void handleHelp(Long chatId,
                            AbsSender bot) throws TelegramApiException {
        sendText(chatId, """
                Available commands:
                
                /start      - main menu with buttons
                /setup      - setup profile step by step
                /myprofile  - view your profile
                /status     - view application stats
                /pause      - pause job notifications
                /resume     - resume notifications
                /help       - show this menu
                
                Tip: type /skip during setup to skip any question.
                """, bot);
    }

    public void sendText(Long chatId, String text,
                         AbsSender bot) throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }

    private String nullSafe(String value) {
        return value != null ? value : "Not set";
    }
}
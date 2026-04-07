package com.jobhunter.bot.handler;

import com.jobhunter.bot.state.ConversationState;
import com.jobhunter.model.User;
import com.jobhunter.repository.UserRepository;
import com.jobhunter.scheduler.SchedulerService;
import com.jobhunter.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileSetupHandler {

    private final UserRepository userRepository;
    private final RedisService redisService;
    private final SchedulerService schedulerService;

    public void startSetup(Long chatId, AbsSender bot) throws TelegramApiException {
        Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) {
            sendText(chatId, "Please link your account first via /start", bot);
            return;
        }
        redisService.setConversationState(chatId, ConversationState.SETUP_NAME);
        sendText(chatId, """
                Let's set up your profile!
                I'll ask you a few questions one by one.
                Type /skip to skip any question.
                
                What is your full name?
                """, bot);
    }

    // returns true if message was handled by setup flow
    public boolean handleIfInSetup(Long chatId, String text,
                                   AbsSender bot) throws TelegramApiException {
        String state = redisService.getConversationState(chatId);
        if (state == null || state.equals(ConversationState.IDLE)) {
            return false;
        }

        boolean skipped = text.equals("/skip");

        switch (state) {
            case ConversationState.SETUP_NAME -> {
                if (!skipped) saveName(chatId, text);
                redisService.setConversationState(chatId, ConversationState.SETUP_PHONE);
                sendText(chatId, skipped
                        ? "Skipped! What is your phone number?"
                        : "Got it! What is your phone number?", bot);
            }
            case ConversationState.SETUP_PHONE -> {
                if (!skipped) savePhone(chatId, text);
                redisService.setConversationState(chatId, ConversationState.SETUP_LOCATION);
                sendText(chatId, skipped
                        ? "Skipped! What is your preferred location? (e.g. Bangalore, Remote)"
                        : "Got it! What is your preferred location? (e.g. Bangalore, Remote)", bot);
            }
            case ConversationState.SETUP_LOCATION -> {
                if (!skipped) saveLocation(chatId, text);
                redisService.setConversationState(chatId, ConversationState.SETUP_SKILLS);
                sendText(chatId, skipped
                        ? "Skipped! What are your skills? (e.g. Java, Spring Boot, MySQL)"
                        : "Got it! What are your skills? (e.g. Java, Spring Boot, MySQL)", bot);
            }
            case ConversationState.SETUP_SKILLS -> {
                if (!skipped) saveSkills(chatId, text);
                redisService.setConversationState(chatId, ConversationState.SETUP_KEYWORDS);
                sendText(chatId, skipped
                        ? "Skipped! What job keywords should I search? (e.g. Software Developer)"
                        : "Got it! What job keywords should I search? (e.g. Software Developer)", bot);
            }
            case ConversationState.SETUP_KEYWORDS -> {
                if (!skipped) saveKeywords(chatId, text);
                redisService.setConversationState(chatId, ConversationState.SETUP_SALARY);
                sendText(chatId, skipped
                        ? "Skipped! What is your minimum expected salary? (e.g. 50000)"
                        : "Got it! What is your minimum expected salary? (e.g. 50000)", bot);
            }
            case ConversationState.SETUP_SALARY -> {
                if (!skipped) {
                    try {
                        saveSalary(chatId, text);
                    } catch (NumberFormatException e) {
                        sendText(chatId,
                                "Please enter a valid number (e.g. 50000) or type /skip", bot);
                        return true;
                    }
                }
                redisService.setConversationState(chatId, ConversationState.SETUP_RESUME);
                sendText(chatId, skipped
                        ? "Skipped! Please share your resume link (Google Drive / any URL)"
                        : "Got it! Please share your resume link (Google Drive / any URL)", bot);
            }
            case ConversationState.SETUP_RESUME -> {
                if (!skipped) saveResume(chatId, text);
                redisService.clearConversationState(chatId);
                finishSetup(chatId, bot);
            }
        }
        return true;
    }

    private void finishSetup(Long chatId, AbsSender bot) throws TelegramApiException {
        User user = userRepository.findByTelegramChatId(chatId).orElseThrow();
        schedulerService.scheduleUserJob(user.getId());

        InlineKeyboardButton editBtn = InlineKeyboardButton.builder()
                .text("Edit Profile")
                .callbackData("setup:start")
                .build();

        bot.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(String.format("""
                        Profile setup complete!
                        
                        Name:       %s
                        Phone:      %s
                        Location:   %s
                        Skills:     %s
                        Keywords:   %s
                        Min Salary: %s
                        Resume:     %s
                        
                        I'll start sending job alerts every 30 minutes!
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
                        .keyboardRow(List.of(editBtn))
                        .build())
                .build());
    }

    private void saveName(Long chatId, String value) {
        User user = getUser(chatId);
        user.setName(value);
        userRepository.save(user);
    }

    private void savePhone(Long chatId, String value) {
        User user = getUser(chatId);
        user.setPhone(value);
        userRepository.save(user);
    }

    private void saveLocation(Long chatId, String value) {
        User user = getUser(chatId);
        user.setLocation(value);
        userRepository.save(user);
    }

    private void saveSkills(Long chatId, String value) {
        User user = getUser(chatId);
        user.setSkills(value);
        userRepository.save(user);
    }

    private void saveKeywords(Long chatId, String value) {
        User user = getUser(chatId);
        user.setKeywordFilters(value);
        userRepository.save(user);
    }

    private void saveSalary(Long chatId, String value) {
        User user = getUser(chatId);
        user.setSalaryFilter(Integer.parseInt(value.trim()));
        userRepository.save(user);
    }

    private void saveResume(Long chatId, String value) {
        User user = getUser(chatId);
        user.setResumeLink(value);
        userRepository.save(user);
    }

    private User getUser(Long chatId) {
        return userRepository.findByTelegramChatId(chatId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private String nullSafe(String value) {
        return value != null ? value : "Not set";
    }

    private void sendText(Long chatId, String text,
                          AbsSender bot) throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }
}
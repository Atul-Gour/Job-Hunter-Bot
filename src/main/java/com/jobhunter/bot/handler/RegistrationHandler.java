package com.jobhunter.bot.handler;

import com.jobhunter.bot.state.ConversationState;
import com.jobhunter.model.User;
import com.jobhunter.repository.UserRepository;
import com.jobhunter.scheduler.SchedulerService;
import com.jobhunter.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationHandler {

    private final UserRepository userRepository;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final SchedulerService schedulerService;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}$");

    /**
     * Start the registration process
     */
    public void startRegistration(Long chatId, String firstName, AbsSender bot)
            throws TelegramApiException {

        redisService.setConversationState(chatId, ConversationState.REGISTER_EMAIL);
        sendText(chatId, """
                Welcome to Job Hunter Bot! 🎉
                
                Let's create your account.
                Type /cancel to abort registration.
                
                What is your email address?
                """, bot);
    }

    /**
     * Handle incoming messages during registration flow
     * Returns true if the message was handled by registration flow
     */
    public boolean handleIfInRegistration(Long chatId, String text,
                                          AbsSender bot) throws TelegramApiException {
        String state = redisService.getConversationState(chatId);

        if (state == null || !state.startsWith("REGISTER_")) {
            return false;
        }

        // Handle /cancel command
        if (text.equals("/cancel")) {
            redisService.clearConversationState(chatId);
            sendText(chatId, "Registration cancelled. Type /start to begin again.", bot);
            return true;
        }

        switch (state) {
            case ConversationState.REGISTER_EMAIL -> handleEmailInput(chatId, text, bot);
            case ConversationState.REGISTER_PASSWORD -> handlePasswordInput(chatId, text, bot);
            case ConversationState.REGISTER_NAME -> handleNameInput(chatId, text, bot);
            case ConversationState.REGISTER_PHONE -> handlePhoneInput(chatId, text, bot);
        }
        return true;
    }

    private void handleEmailInput(Long chatId, String email, AbsSender bot)
            throws TelegramApiException {

        email = email.trim();

        // Validate email format
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            sendText(chatId, "Invalid email format. Please enter a valid email address.", bot);
            return;
        }

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            sendText(chatId, "This email is already registered. Please use /start to login.", bot);
            return;
        }

        // Save email to temporary storage (using Redis key pattern)
        redisService.saveRegistrationData(chatId, "email", email);

        // Move to password state
        redisService.setConversationState(chatId, ConversationState.REGISTER_PASSWORD);
        sendText(chatId, """
                Great! Email saved.
                
                Now, create a password.
                Requirements:
                • At least 6 characters
                • At least one uppercase letter (A-Z)
                • At least one lowercase letter (a-z)
                • At least one number (0-9)
                
                Example: MyPassword123
                """, bot);
    }

    private void handlePasswordInput(Long chatId, String password, AbsSender bot)
            throws TelegramApiException {

        password = password.trim();

        // Validate password strength
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            sendText(chatId, """
                    Password doesn't meet requirements:
                    • At least 6 characters
                    • At least one uppercase letter
                    • At least one lowercase letter
                    • At least one number
                    
                    Please try again.
                    """, bot);
            return;
        }

        // Save password to temporary storage
        redisService.saveRegistrationData(chatId, "password", password);

        // Move to name state
        redisService.setConversationState(chatId, ConversationState.REGISTER_NAME);
        sendText(chatId, "Perfect! Now, what is your full name?", bot);
    }

    private void handleNameInput(Long chatId, String name, AbsSender bot)
            throws TelegramApiException {

        name = name.trim();

        if (name.length() < 2) {
            sendText(chatId, "Please enter a valid name (at least 2 characters).", bot);
            return;
        }

        // Save name to temporary storage
        redisService.saveRegistrationData(chatId, "name", name);

        // Move to phone state
        redisService.setConversationState(chatId, ConversationState.REGISTER_PHONE);
        sendText(chatId, """
                Great! Name saved.
                
                Finally, what is your phone number?
                (Type /skip if you'd like to add it later)
                """, bot);
    }

    private void handlePhoneInput(Long chatId, String phone, AbsSender bot)
            throws TelegramApiException {

        if (!phone.equals("/skip")) {
            phone = phone.trim();
            if (phone.length() < 7) {
                sendText(chatId, "Please enter a valid phone number.", bot);
                return;
            }
            redisService.saveRegistrationData(chatId, "phone", phone);
        }

        // Get all registration data from Redis
        String email = redisService.getRegistrationData(chatId, "email");
        String password = redisService.getRegistrationData(chatId, "password");
        String name = redisService.getRegistrationData(chatId, "name");
        String phoneData = redisService.getRegistrationData(chatId, "phone");

        // Create user in database
        try {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setPassword(passwordEncoder.encode(password));
            newUser.setName(name);
            newUser.setPhone(phoneData);
            newUser.setTelegramChatId(chatId);
            newUser.setIsActive(true);
            newUser.setNotificationsPaused(false);
            newUser.setCreatedAt(LocalDateTime.now());

            userRepository.save(newUser);

            // Clear registration state
            redisService.clearConversationState(chatId);
            redisService.clearRegistrationData(chatId);

            // Schedule job for this user
            schedulerService.scheduleUserJob(newUser.getId());

            sendText(chatId, """
                    ✅ Account created successfully!
                    
                    Name: %s
                    Email: %s
                    Phone: %s
                    
                    Now let's set up your profile by typing /setup
                    """.formatted(name, email, phoneData != null ? phoneData : "Not provided"), bot);

            log.info("New user registered: {} (chatId: {})", email, chatId);

        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            redisService.clearConversationState(chatId);
            redisService.clearRegistrationData(chatId);
            sendText(chatId, "An error occurred during registration. Please try /start again.", bot);
        }
    }

    private void sendText(Long chatId, String text, AbsSender bot)
            throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }
}
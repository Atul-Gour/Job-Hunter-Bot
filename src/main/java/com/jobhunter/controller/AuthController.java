package com.jobhunter.controller;

import java.util.Map;
import java.util.UUID;

import com.jobhunter.config.BotConfigProperties;
import com.jobhunter.dto.*;
import com.jobhunter.model.User;
import com.jobhunter.service.RedisService;
import com.jobhunter.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RedisService redisService;
    private final UserService userService;
    private final BotConfigProperties botConfig;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<User> me(Authentication auth) {
        return ResponseEntity.ok(userService.getByEmail(auth.getName()));
    }

    @PostMapping("/link-telegram")
    public ResponseEntity<String> linkTelegram(
            Authentication auth,
            @RequestParam Long chatId) {
        userService.linkTelegramAccount(auth.getName(), chatId);
        return ResponseEntity.ok("Telegram account linked successfully");
    }

    @GetMapping("/generate-link-token")
    public ResponseEntity<Map<String, String>> generateLinkToken(Authentication auth) {
        String email = auth.getName();
        // generate a random 6-char token
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // store in Redis: linktoken:{token} = email, expires in 10 minutes
        redisService.saveLinkToken(token, email);

        String botUsername = botConfig.getBotUsername();
        String deepLink = "https://t.me/" + botUsername + "?start=" + token;

        return ResponseEntity.ok(Map.of(
                "token", token,
                "deepLink", deepLink
        ));
    }
}
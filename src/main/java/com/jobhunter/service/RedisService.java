package com.jobhunter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    private String dedupKey(Long userId) {
        return "job:seen:" + userId;
    }

    public boolean isJobAlreadySeen(Long userId, String externalJobId) {
        Boolean seen = redisTemplate.opsForSet()
                .isMember(dedupKey(userId), externalJobId);
        return Boolean.TRUE.equals(seen);
    }

    public void markJobAsSeen(Long userId, String externalJobId) {
        String key = dedupKey(userId);
        redisTemplate.opsForSet().add(key, externalJobId);
        redisTemplate.expire(key, Duration.ofDays(30));
    }

    public boolean isRateLimited(Long userId) {
        String key = "ratelimit:telegram:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        return count > 50;
    }

    public void setConversationState(Long chatId, String state) {
        redisTemplate.opsForValue().set(
                "conv:state:" + chatId, state, Duration.ofMinutes(30));
    }

    public String getConversationState(Long chatId) {
        return redisTemplate.opsForValue().get("conv:state:" + chatId);
    }

    public void clearConversationState(Long chatId) {
        redisTemplate.delete("conv:state:" + chatId);
    }

    public void saveLinkToken(String token, String email) {
        redisTemplate.opsForValue().set(
                "linktoken:" + token, email, Duration.ofMinutes(10));
    }

    public String getLinkToken(String token) {
        return redisTemplate.opsForValue().get("linktoken:" + token);
    }

    public void deleteLinkToken(String token) {
        redisTemplate.delete("linktoken:" + token);
    }
}
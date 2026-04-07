package com.jobhunter.bot;

import com.jobhunter.bot.handler.BotCommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
public class JobHunterBot extends TelegramLongPollingBot {

    private final BotCommandHandler commandHandler;

    @Value("${telegram.bot-username}")
    private String botUsername;

    public JobHunterBot(@Value("${telegram.bot-token}") String botToken,
                        BotCommandHandler commandHandler) {
        super(botToken);
        this.commandHandler = commandHandler;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            System.out.println(update);
            commandHandler.handle(update, this);
        } catch (Exception e) {
            log.error("Error handling update: {}", e.getMessage(), e);
        }
    }
}
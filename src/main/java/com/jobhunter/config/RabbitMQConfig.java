package com.jobhunter.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // queue names
    public static final String JOB_NEW_QUEUE       = "job.new";
    public static final String JOB_APPLY_QUEUE     = "job.apply";
    public static final String JOB_NEW_DLQ         = "job.new.dlq";
    public static final String JOB_APPLY_DLQ       = "job.apply.dlq";

    // exchanges
    public static final String JOB_EXCHANGE        = "job.exchange";
    public static final String JOB_DLX             = "job.dlx";

    // routing keys
    public static final String NEW_ROUTING_KEY     = "job.new";
    public static final String APPLY_ROUTING_KEY   = "job.apply";

    // --- Exchanges ---
    @Bean
    public DirectExchange jobExchange() {
        return new DirectExchange(JOB_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(JOB_DLX, true, false);
    }

    // --- Main Queues (with DLQ configured) ---
    @Bean
    public Queue jobNewQueue() {
        return QueueBuilder.durable(JOB_NEW_QUEUE)
                .withArgument("x-dead-letter-exchange", JOB_DLX)
                .withArgument("x-dead-letter-routing-key", JOB_NEW_DLQ)
                .build();
    }

    public RabbitMQConfig() {
    }

    @Bean
    public Queue jobApplyQueue() {
        return QueueBuilder.durable(JOB_APPLY_QUEUE)
                .withArgument("x-dead-letter-exchange", JOB_DLX)
                .withArgument("x-dead-letter-routing-key", JOB_APPLY_DLQ)
                .build();
    }

    // --- Dead Letter Queues ---
    @Bean
    public Queue jobNewDlq() {
        return QueueBuilder.durable(JOB_NEW_DLQ).build();
    }

    @Bean
    public Queue jobApplyDlq() {
        return QueueBuilder.durable(JOB_APPLY_DLQ).build();
    }

    // --- Bindings ---
    @Bean
    public Binding jobNewBinding() {
        return BindingBuilder.bind(jobNewQueue())
                .to(jobExchange())
                .with(NEW_ROUTING_KEY);
    }

    @Bean
    public Binding jobApplyBinding() {
        return BindingBuilder.bind(jobApplyQueue())
                .to(jobExchange())
                .with(APPLY_ROUTING_KEY);
    }

    @Bean
    public Binding jobNewDlqBinding() {
        return BindingBuilder.bind(jobNewDlq())
                .to(deadLetterExchange())
                .with(JOB_NEW_DLQ);
    }

    @Bean
    public Binding jobApplyDlqBinding() {
        return BindingBuilder.bind(jobApplyDlq())
                .to(deadLetterExchange())
                .with(JOB_APPLY_DLQ);
    }

    // --- JSON message converter ---
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
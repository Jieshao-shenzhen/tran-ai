package com.gdcp.lab.business.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "lab.event.exchange";
    public static final String QUEUE_STATS = "lab.stats.queue";
    public static final String QUEUE_NOTIFY = "lab.notify.queue";

    @Bean
    public TopicExchange labExchange() { return new TopicExchange(EXCHANGE); }

    @Bean
    public Queue statsQueue() { return new Queue(QUEUE_STATS); }

    @Bean
    public Queue notifyQueue() { return new Queue(QUEUE_NOTIFY); }

    @Bean
    public Binding statsBinding(Queue statsQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(statsQueue).to(labExchange).with("lab.stats.#");
    }

    @Bean
    public Binding notifyBinding(Queue notifyQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(notifyQueue).to(labExchange).with("lab.notify.#");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}

package com.hl.study.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // 1. 声明直连交换机
    @Bean
    public DirectExchange reportExchange() {
        return new DirectExchange(MqConst.EXCHANGE, true, false);
    }

    // 2. 主队列（带死信路由参数，失败后进入 retry 队列）
    @Bean
    public Queue reportQueue() {
        return QueueBuilder.durable(MqConst.QUEUE_MAIN)
                .deadLetterExchange(MqConst.EXCHANGE)
                .deadLetterRoutingKey(MqConst.RK_RETRY)
                .build();
    }

    // 3. 重试队列（TTL 15s，到期后通过死信路由回到主队列）
    @Bean
    public Queue reportRetryQueue() {
        return QueueBuilder.durable(MqConst.QUEUE_RETRY)
                .ttl(15_000)
                .deadLetterExchange(MqConst.EXCHANGE)
                .deadLetterRoutingKey(MqConst.RK_GENERATE)
                .build();
    }

    // 4. 死信队列（不挂消费者，只做审计）
    @Bean
    public Queue reportDlq() {
        return QueueBuilder.durable(MqConst.QUEUE_DLQ).build();
    }

    // 5. 三个绑定
    @Bean
    public Binding bindMain() {
        return BindingBuilder.bind(reportQueue()).to(reportExchange()).with(MqConst.RK_GENERATE);
    }

    @Bean
    public Binding bindRetry() {
        return BindingBuilder.bind(reportRetryQueue()).to(reportExchange()).with(MqConst.RK_RETRY);
    }

    @Bean
    public Binding bindDlq() {
        return BindingBuilder.bind(reportDlq()).to(reportExchange()).with(MqConst.RK_DLQ);
    }

    // 6. 自定义消息转换器（使用 JSON，拒绝 Java 默认序列化）
    @Bean
    public MessageConverter jsonConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor da integração com o Google Calendar: as chamadas ao Google nunca rodam na requisição do usuário,
 * então um Google lento ou fora do ar não atrasa nem derruba salvar um agendamento.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class GoogleCalendarAsyncConfig {

    public static final String GOOGLE_CALENDAR_EXECUTOR = "googleCalendarExecutor";

    @Bean(name = GOOGLE_CALENDAR_EXECUTOR)
    public Executor googleCalendarExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("gcal-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

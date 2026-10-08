package com.timekeeper.bibexpo.bootstrap;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "smsCampaignTaskExecutor")
    public Executor smsCampaignTaskExecutor() {
        return pool("sms-campaign-", 5, 20, 50);
    }

    @Bean(name = "whatsAppCampaignTaskExecutor")
    public Executor whatsAppCampaignTaskExecutor() {
        return pool("whatsapp-campaign-", 5, 20, 50);
    }

    // A full queue sends on the counter's own thread rather than dropping the message or failing the collection.
    @Bean(name = "bibCollectedMessageTaskExecutor")
    public Executor bibCollectedMessageTaskExecutor() {
        ThreadPoolTaskExecutor executor = pool("bib-collected-message-", 4, 8, 500);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(name = "participantAccessTaskExecutor")
    public Executor participantAccessTaskExecutor() {
        return pool("participant-access-", 2, 5, 10);
    }

    @Bean(name = "passwordResetTaskExecutor")
    public Executor passwordResetTaskExecutor() {
        return pool("password-reset-", 2, 5, 50);
    }

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        return pool("audit-", 2, 5, 100);
    }

    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        return pool("notification-", 2, 5, 100);
    }

    /** Left uninitialized: Spring initializes the bean, after any extra settings the caller adds. */
    private static ThreadPoolTaskExecutor pool(String threadNamePrefix, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setTaskDecorator(AsyncConfig::withCallerLogContext);
        return executor;
    }

    // Carries the submitting request's log context (request id, user, organization) onto the worker
    // thread, then restores the thread's own: CallerRunsPolicy can run a task on the request thread.
    private static Runnable withCallerLogContext(Runnable task) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> ownContext = MDC.getCopyOfContextMap();
            setLogContext(callerContext);
            try {
                task.run();
            } finally {
                setLogContext(ownContext);
            }
        };
    }

    private static void setLogContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}

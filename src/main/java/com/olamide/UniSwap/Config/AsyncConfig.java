package com.olamide.UniSwap.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Email delivery runs off the request thread: SMTP relays can be slow or
// unreachable, and register/login-code/reset must never block on them.
@Configuration
@EnableAsync
public class AsyncConfig {

    // Small bounded pool: transactional emails are low-volume, and a queue cap
    // plus caller-runs policy means an SMTP outage degrades to slow requests
    // instead of exhausting memory.
    @Bean(name = "mailExecutor")
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

package com.timekeeper.bibexpo.config;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.JobRegistrySmartInitializingSingleton;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;

@Configuration
public class BatchConfig {

    /**
     * Spring Boot 4.x auto-config targets Batch 6.x, so schema init does not fire
     * for Batch 5.2.x. We explicitly run the Batch 5.2 MySQL DDL here.
     * setContinueOnError(true) makes re-runs idempotent (tables already exist = harmless).
     */
    @Bean
    public DataSourceInitializer batchSchemaInitializer(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("org/springframework/batch/core/schema-mysql.sql"));
        populator.setContinueOnError(true);

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    // Managed executor — participates in Spring lifecycle so graceful shutdown waits for running imports.
    @Bean(name = "batchJobTaskExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor batchJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("batch-import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository,
                                         ThreadPoolTaskExecutor batchJobTaskExecutor) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(batchJobTaskExecutor);
        launcher.afterPropertiesSet();
        return launcher;
    }

    // Registers every Job bean with the JobRegistry so SimpleJobOperator can resolve them by name on stop/restart.
    // SmartInitializingSingleton variant avoids the early-bean-initialization problem of the old BeanPostProcessor.
    // It auto-discovers Job beans via the BeanFactory once all singletons are instantiated.
    @Bean
    public JobRegistrySmartInitializingSingleton jobRegistrar(JobRegistry jobRegistry) {
        return new JobRegistrySmartInitializingSingleton(jobRegistry);
    }

    @Bean
    public JobOperator jobOperator(JobLauncher asyncJobLauncher,
                                    JobRepository jobRepository,
                                    JobRegistry jobRegistry,
                                    JobExplorer jobExplorer) throws Exception {
        SimpleJobOperator operator = new SimpleJobOperator();
        operator.setJobLauncher(asyncJobLauncher);
        operator.setJobRepository(jobRepository);
        operator.setJobRegistry(jobRegistry);
        operator.setJobExplorer(jobExplorer);
        operator.afterPropertiesSet();
        return operator;
    }
}

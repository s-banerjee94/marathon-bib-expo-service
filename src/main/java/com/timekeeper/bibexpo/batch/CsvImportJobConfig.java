package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.util.CsvRow;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CsvImportJobConfig {

    @Bean
    public Job csvImportJob(JobRepository repo, Step csvImportStep,
                            BatchJobNotificationListener notificationListener) {
        return new JobBuilder("csvImportJob", repo)
                .listener(notificationListener)
                .start(csvImportStep)
                .build();
    }

    @Bean
    public Step csvImportStep(JobRepository repo, PlatformTransactionManager tx,
                               CsvItemReader reader, CsvItemProcessor processor,
                               CsvItemWriter writer, BatchSkipListener skipListener) {
        return new StepBuilder("csvImportStep", repo)
                .<CsvRow, ParticipantDDB>chunk(25, tx)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(Integer.MAX_VALUE)
                .skip(BatchValidationException.class)
                .listener((SkipListener<CsvRow, ParticipantDDB>) skipListener)
                .listener((StepExecutionListener) skipListener)
                .build();
    }
}

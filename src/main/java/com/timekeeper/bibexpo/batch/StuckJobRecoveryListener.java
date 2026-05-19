package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.model.entity.ImportJob;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StuckJobRecoveryListener {

    private final ImportJobRepository importJobRepository;
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStuckJobs() {
        recoverImportJobRows();
        recoverSpringBatchExecutions();
    }

    private void recoverImportJobRows() {
        List<ImportJob> stuck = importJobRepository.findByStatus(ImportJob.ImportStatus.IN_PROGRESS);
        if (stuck.isEmpty()) {
            return;
        }
        for (ImportJob job : stuck) {
            job.setStatus(ImportJob.ImportStatus.FAILED);
            job.setErrorSummary("{\"reason\":\"Application restarted before import completed\"}");
        }
        importJobRepository.saveAll(stuck);
        log.warn("Recovered {} stuck ImportJob records on startup", stuck.size());
    }

    private void recoverSpringBatchExecutions() {
        int recovered = 0;
        for (String jobName : jobExplorer.getJobNames()) {
            for (JobExecution exec : jobExplorer.findRunningJobExecutions(jobName)) {
                for (StepExecution step : exec.getStepExecutions()) {
                    if (step.getStatus() == BatchStatus.STARTED || step.getStatus() == BatchStatus.STARTING) {
                        step.setStatus(BatchStatus.ABANDONED);
                        step.setExitStatus(new ExitStatus("ABANDONED", "Recovered after app restart"));
                        step.setEndTime(LocalDateTime.now());
                        jobRepository.update(step);
                    }
                }
                exec.setStatus(BatchStatus.ABANDONED);
                exec.setExitStatus(new ExitStatus("ABANDONED", "Recovered after app restart"));
                exec.setEndTime(LocalDateTime.now());
                jobRepository.update(exec);
                recovered++;
            }
        }
        if (recovered > 0) {
            log.warn("Marked {} stuck Spring Batch executions as ABANDONED on startup", recovered);
        }
    }
}

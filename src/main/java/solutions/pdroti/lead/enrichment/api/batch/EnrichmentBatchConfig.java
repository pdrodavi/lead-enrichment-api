package solutions.pdroti.lead.enrichment.api.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.repository.LeadRepository;
import solutions.pdroti.lead.enrichment.api.service.LeadService;
import java.util.Map;
import java.util.Collections;

@Configuration
public class EnrichmentBatchConfig {

  @SuppressWarnings("null")
  @Bean
  public RepositoryItemReader<Lead> reader(LeadRepository repository) {
    return new RepositoryItemReaderBuilder<Lead>()
        .name("leadReader")
        .repository(repository)
        .methodName("findByStatus")
        .arguments(Collections.singletonList("PENDING"))
        .sorts(Map.of("id", Sort.Direction.ASC))
        .pageSize(10)
        .build();
  }

  @Bean
  public ItemProcessor<Lead, Lead> processor(LeadService leadService) {
    return leadService::enrichLead;
  }

  @SuppressWarnings("null")
  @Bean
  public RepositoryItemWriter<Lead> writer(LeadRepository repository) {
    return new RepositoryItemWriterBuilder<Lead>().repository(repository).methodName("save").build();
  }

  @Bean
  public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.initialize();
    return executor;
  }

  @SuppressWarnings("null")
  @Bean
  public Step enrichmentStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
      RepositoryItemReader<Lead> reader, ItemProcessor<Lead, Lead> processor, RepositoryItemWriter<Lead> writer,
      TaskExecutor taskExecutor) {
    return new StepBuilder("enrichmentStep", jobRepository).<Lead, Lead>chunk(10, transactionManager).reader(reader)
        .processor(processor).writer(writer).taskExecutor(taskExecutor).build();
  }

  @SuppressWarnings("null")
  @Bean
  public Job enrichmentJob(JobRepository jobRepository, Step enrichmentStep) {
    return new JobBuilder("enrichmentJob", jobRepository).start(enrichmentStep).build();
  }
}
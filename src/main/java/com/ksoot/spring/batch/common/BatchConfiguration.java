package com.ksoot.spring.batch.common;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParametersIncrementer;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.batch.core.launch.support.DataFieldMaxValueJobParametersIncrementer;
import org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.incrementer.PostgresSequenceMaxValueIncrementer;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.BackOffPolicyBuilder;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(value = {BatchProperties.class})
@RequiredArgsConstructor
public class BatchConfiguration extends DefaultBatchConfiguration {

  private final DataSource dataSource;

  private final BatchProperties batchProperties;

  //  Define Async Task Executor when executing the jobs from Rest API, to submit job
  // asynchronously.
  @Override
  protected TaskExecutor getTaskExecutor() {
    if (StringUtils.isNotBlank(this.batchProperties.getTaskExecutor())) {
      return this.applicationContext.getBean(
          this.batchProperties.getTaskExecutor(), TaskExecutor.class);
    } else {
      return super.getTaskExecutor();
    }
  }

  @ConditionalOnMissingBean
  @Bean
  JobParametersIncrementer jobParametersIncrementer() {
    return new DataFieldMaxValueJobParametersIncrementer(
        new PostgresSequenceMaxValueIncrementer(
            this.dataSource, this.batchProperties.getRunIdSequence()));
  }

  @Override
  protected PlatformTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(this.dataSource);
  }

  @ConditionalOnMissingBean
  @Bean
  BackOffPolicy backOffPolicy() {
    return BackOffPolicyBuilder.newBuilder()
        .delay(this.batchProperties.getBackoffInitialDelay().toMillis())
        .multiplier(this.batchProperties.getBackoffMultiplier())
        .build();
  }

  @ConditionalOnMissingBean
  @Bean
  RetryPolicy retryPolicy() {
    CompositeRetryPolicy retryPolicy = new CompositeRetryPolicy();
    retryPolicy.setPolicies(ArrayUtils.toArray(this.noRetryPolicy(), this.daoRetryPolicy()));
    return retryPolicy;
  }

  private RetryPolicy noRetryPolicy() {
    Map<Class<? extends Throwable>, Boolean> exceptionClassifiers =
        this.skippedExceptions().stream().collect(Collectors.toMap(ex -> ex, ex -> Boolean.FALSE));
    return new SimpleRetryPolicy(this.batchProperties.getMaxRetries(), exceptionClassifiers, false);
  }

  private RetryPolicy daoRetryPolicy() {
    return new SimpleRetryPolicy(
        this.batchProperties.getMaxRetries(),
        Map.of(
            TransientDataAccessException.class,
            true,
            RecoverableDataAccessException.class,
            true,
            NonTransientDataAccessException.class,
            false,
            EmptyResultDataAccessException.class,
            false),
        false);
  }

  // TODO: May need to Implement retry policy for Kafka,
  // need to check if required as Kafka client internally retries,
  // So may not be required to retry explicitly
  // If required create new retry policy (similar to above method) for accrual accounting job as
  // configure in respective step

  // If want to skip for all kind of exceptions, return new AlwaysSkipItemSkipPolicy
  // Skipped exceptions must also be specified in noRollback in Step configuration
  @ConditionalOnMissingBean
  @Bean
  SkipPolicy skipPolicy() {
    Map<Class<? extends Throwable>, Boolean> exceptionClassifiers =
        this.skippedExceptions().stream().collect(Collectors.toMap(ex -> ex, ex -> Boolean.TRUE));
    return new LimitCheckingItemSkipPolicy(
        this.batchProperties.getSkipLimit(), exceptionClassifiers);
  }

  @ConditionalOnMissingBean
  @Bean
  List<Class<? extends Throwable>> skippedExceptions() {
    return List.of(ConstraintViolationException.class, SkipRecordException.class);
  }

  @ConditionalOnMissingBean
  @Bean
  JobExecutionListener jobExecutionListener() {
    return new LoggingJobListener();
  }

  @ConditionalOnMissingBean
  @Bean
  StepExecutionListener stepExecutionListener() {
    return new LoggingStepListener();
  }
}

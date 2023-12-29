package com.ksoot.spring.batch.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Valid
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

  /**
   * Default: 100, Number of items that are processed in a single transaction by a chunk-oriented
   * step.
   */
  @Min(1)
  @Max(10000)
  private int chunkSize = 100;

  /**
   * Default: 10, Maximum number of items to skip as per configured Skip policy, exceeding which
   * fails the job.
   */
  @Min(1)
  private int skipLimit = 10;

  /**
   * Default: 3, Maximum number of retry attempts as configured Retry policy, exceeding which fails
   * the job.
   */
  @Min(1)
  @Max(10)
  private int maxRetries = 3;

  /**
   * Default: false, Time duration (in java.time.Duration format) to wait before the first retry
   * attempt is made after a failure.
   */
  @NotNull private Duration backoffInitialDelay = Duration.ofSeconds(3);

  /** Default: 3, Factor by which the delay between consecutive retries is multiplied. */
  @Min(1)
  @Max(5)
  private int backoffMultiplier = 2;

  /** Default: 100, Number of records to be read in each page by Paging Item readers. */
  @Min(1)
  @Max(10000)
  private int pageSize = 100;

  /**
   * Default: 8, Number of partitions that will be used to process the data concurrently. Should be
   * optimized as per available machine resources.
   */
  @Min(1)
  @Max(128)
  private int partitionSize = 8;

  /**
   * Default: 100, Minimum number of records to trigger partitioning otherwise it could be counter
   * productive to do partitioning.
   */
  @Min(1)
  private int triggerPartitioningThreshold = 100;

  /**
   * Bean name of the Task Executor to be used for executing the jobs. By default <code>
   * SyncTaskExecutor</code> is used. Set to <code>applicationTaskExecutor</code> to use <code>
   * SimpleAsyncTaskExecutor</code> provided by Spring. Or use any other custom <code>TaskExecutor
   * </code> and set the bean name here.
   */
  private String taskExecutor;

  /** Default: run_id_sequence, Run Id database sequence name. */
  @NotEmpty private String runIdSequence = "run_id_sequence";
}

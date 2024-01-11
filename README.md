# Spring Batch common components

[**Spring Batch**](https://docs.spring.io/spring-batch/reference/index.html) is a battle tested Java framework that makes it easy to write batch applications.
Batch applications involve reliably and efficiently processing large volumes of data to and
from various data sources (files, databases, messaging middleware, and so on).
Spring Batch is great at doing this and provides the necessary foundation to meet the stringent requirements of batch applications.
It provides mechanisms for common tasks such as **task orchestration**, **partitioning**, and **restart**.

![String Batch Architecture](https://github.com/officiallysingh/spring-batch-commons/blob/main/Spring_Batch.jpg)

## Introduction
Spring batch jobs may require boilerplate code to be written, which is extracted out in this library to promote reusability.
Common components of a Spring batch job are defined as Beans and can be reused across multiple jobs. 
**See usage** in Spring Batch Job implemented as [**`Spring Cloud Task`**](https://github.com/officiallysingh/spring-boot-batch-cloud-task) 
and [**`Spring Rest service`**](https://github.com/officiallysingh/spring-boot-batch-web).

## Features
* Provides common components and utility classes to easily create Spring batch jobs.
* Provides opinionated default configurations for Spring batch jobs.
* Supports partitioning of jobs to process data concurrently.
* Autoconfigures fault tolerance with intelligent defaults to retry and recover for transient failure.
* The records are processed in chunks, if the job fails midway,
  it can be restarted from the last failed chunk without re processing already processed records.
* Supports force restarting already completed jobs.
* Supports skipping records in case of exceptions.
* Supports logging of job and step execution events.

## Classes
Following are the classes provided by this library.
* [`BatchConguration`](src/main/java/com/ksoot/spring/batch/common/BatchConfiguration.java) 
Extends [`DefaultBatchConfiguration`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/configuration/support/DefaultBatchConfiguration.html) 
and defines default configuration for Spring batch jobs. It is autoconfigured by Spring boot.
* [`AbstractJobExecutor`](src/main/java/com/ksoot/spring/batch/common/AbstractJobExecutor.java) 
Extendable by consumer application Job executor to execute job with **Run Id Incrementer** to force restart the job in case it was successfully completed in last execution.
* [`AbstractPartitioner`](src/main/java/com/ksoot/spring/batch/common/AbstractPartitioner.java) Provides common implementation for partitioning Spring batch jobs. 
Consumer applications need to extend this class and provide implementation for **`partitioningList`** method to return `List` of partitioning candidate `String`s.
* [`JobConfigurationSupport`](src/main/java/com/ksoot/spring/batch/common/JobConfigurationSupport.java) 
Extendable by consumer application to define new Simple and Partitioned jobs with default configurations. 
The defaults can be overridden per job by consumer applications by overriding respective methods. 
Or default can be overridden globally in consumer application by defining new bean for respective component.
* [`LoggingJobListener`](src/main/java/com/ksoot/spring/batch/common/LoggingJobListener.java) 
Provides default implementation for Spring batch job listener, which does nothing but logging only.
* [`LoggingStepListener`](src/main/java/com/ksoot/spring/batch/common/LoggingStepListener.java) 
Provides default implementation for Spring batch step listeners, which does nothing but logging only.
* [`MongoAggregationPagingItemReader`](src/main/java/com/ksoot/spring/batch/common/MongoAggregationPagingItemReader.java) 
Custom Mongo Paging Item reader using aggregation pipeline and pagination.
* [`MongoUpsertItemWriter`](src/main/java/com/ksoot/spring/batch/common/MongoUpsertItemWriter.java) 
Custom Mongo Item writer for upsert operation.
* [`ListFlattenerKafkaItemWriter`](src/main/java/com/ksoot/spring/batch/common/ListFlattenerKafkaItemWriter.java) 
Custom Kafka writer to write a `List` of items to kafka. 
Can be used in cases where the last `Processor` return a List of items, instead of a single item.
* [`StepStatus`](src/main/java/com/ksoot/spring/batch/common/StepStatus.java) 
Utility Class to define custom Step status, can be enhanced to add more statuses.
* [`SkipRecordException`](src/main/java/com/ksoot/spring/batch/common/SkipRecordException.java) 
Custom exception to represent skipped records in Spring batch jobs. Default implementation of `SkipPolicy` includes this exception.
* [`BatchProperties`](src/main/java/com/ksoot/spring/batch/common/BatchProperties.java) 
Spring boot configuration property class to read batch properties from `application.properties` or `application.yml` file.

## Autoconfigured Components
Following are the components, autoconfigured as Beans by Spring boot with opinionated default behaviour.
The defaults can be customized by configurations and custom implementations in consumer application.

* [`JobParametersIncrementer`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/JobParametersIncrementer.html) 
to generate unique run id for each job execution in case of force restarting already successfully completed jobs.
Each Job execution is uniquely identified by combination of its `identifying` parameters.
If a job is restarted with same identifying parameters, Spring batch will throw `JobInstanceAlreadyCompleteException`. So to force restart the job,
[`AbstractJobExecutor#execute`](https://github.com/officiallysingh/spring-batch-commons/blob/04c4a7232f5e36ace5168c498fa96690615799f8/src/main/java/com/ksoot/spring/batch/common/AbstractJobExecutor.java#L22)
method adds a unique `run.id` to the job execution parameters if `forceRestart` argument is `true`.
It can be overridden by defining new `JobParametersIncrementer` bean in consumer application.
It requires a database sequence named `run_id_sequence` to generate unique run id which can be overridden 
by setting `batch.run-id-sequence` property in `application.properties` or `application.yml` file. 

> [!IMPORTANT]
Already running job can not be restarted, as Spring batch does not allow that. 
Though this behaviour can also be overridden but not recommended.

```java
@ConditionalOnMissingBean
@Bean
JobParametersIncrementer jobParametersIncrementer(
  final DataSource dataSource, final BatchProperties batchProperties) {
    return new DataFieldMaxValueJobParametersIncrementer(
        new PostgresSequenceMaxValueIncrementer(dataSource, batchProperties.getRunIdSequence()));
}
```
```sql
CREATE SEQUENCE IF NOT EXISTS run_id_sequence START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
```

* [`BackOffPolicy`](https://www.javadoc.io/static/org.springframework.retry/spring-retry/2.0.5/org/springframework/retry/backoff/BackOffPolicy.html)
to define back off policy for retrying failed steps. Default is [`ExponentialBackOffPolicy`](https://www.javadoc.io/static/org.springframework.retry/spring-retry/2.0.5/org/springframework/retry/backoff/ExponentialBackOffPolicy.html)
Backoff delay and multiplier can be customized by setting `batch.backoff-initial-delay` and `batch.backoff-multiplier` properties in `application.properties` or `application.yml` file.
It can be overridden by defining new `BackOffPolicy` bean in consumer application.
```java
@ConditionalOnMissingBean
@Bean
BackOffPolicy backOffPolicy(final BatchProperties batchProperties) {
    return BackOffPolicyBuilder.newBuilder()
        .delay(batchProperties.getBackoffInitialDelay().toMillis())
        .multiplier(batchProperties.getBackoffMultiplier())
        .build();
}
```

* [`RetryPolicy`](https://docs.spring.io/spring-retry/docs/api/current/org/springframework/retry/RetryPolicy.html)
to define retry policy for retrying failed steps. By default, it retries for `TransientDataAccessException` and `RecoverableDataAccessException` exceptions for JPA and Mongo DB.
It works in conjunction with `BackOffPolicy`.
It can be overridden by defining new `RetryPolicy` bean in consumer application 
and customized by setting `batch.retry-max-attempts` property in `application.properties` or `application.yml` file.
```java
@ConditionalOnMissingBean
@Bean
RetryPolicy retryPolicy(final BatchProperties batchProperties) {
    CompositeRetryPolicy retryPolicy = new CompositeRetryPolicy();
    retryPolicy.setPolicies(
        ArrayUtils.toArray(
            this.noRetryPolicy(batchProperties), this.daoRetryPolicy(batchProperties)));
    return retryPolicy;
}
```

* [`SkipPolicy`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/step/skip/SkipPolicy.html)
to define skip policy for skipping records in case of exceptions. By default, it skips `ConstraintViolationException` and `SkipRecordException`.
It can be customized by setting `batch.skip-limit` property in `application.properties` or `application.yml` file.
It can be defined as [AlwaysSkipItemSkipPolicy](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/step/skip/AlwaysSkipItemSkipPolicy.html) 
to skip all records in case of any exception. 
Skipped exceptions must also be specified in noRollback in Step configuration which is handled by this library automatically. 
It can be overridden by defining new `SkipPolicy` bean in consumer application. Similarly `skippedExceptions` can also be overridden. 
```java
@ConditionalOnMissingBean
@Bean
SkipPolicy skipPolicy(final BatchProperties batchProperties) {
    Map<Class<? extends Throwable>, Boolean> exceptionClassifiers =
        this.skippedExceptions().stream().collect(Collectors.toMap(ex -> ex, ex -> Boolean.TRUE));
    return new LimitCheckingItemSkipPolicy(batchProperties.getSkipLimit(), exceptionClassifiers);
}

@ConditionalOnMissingBean
@Bean
List<Class<? extends Throwable>> skippedExceptions() {
    return List.of(ConstraintViolationException.class, SkipRecordException.class);
}
```

* [`JobExecutionListener`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/JobExecutionListener.html) 
default implementation as [`LoggingJobListener`](src/main/java/com/ksoot/spring/batch/common/LoggingJobListener.java)
which does nothing but logging only. It can be overridden by defining new `JobExecutionListener` bean in consumer application.
```java
@ConditionalOnMissingBean
@Bean
JobExecutionListener jobExecutionListener() {
    return new LoggingJobListener();
}
```

* [`StepExecutionListener`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/StepExecutionListener.html) 
default implementation as [`LoggingStepListener`](src/main/java/com/ksoot/spring/batch/common/LoggingStepListener.java) 
which does nothing but logging only. It can be overridden by defining new `StepExecutionListener` bean in consumer application.
```java
@ConditionalOnMissingBean
@Bean
StepExecutionListener stepExecutionListener() {
    return new LoggingStepListener();
}
```

## Configurations
Following are the configuration properties to customize default Spring batch behaviour.
```yaml
batch:
  chunk-size: 100
  skip-limit: 10
  max-retries: 3
  backoff-initial-delay: PT3S
  backoff-multiplier: 2
  page-size: 300
  partition-size: 16
  trigger-partitioning-threshold: 100
#  task-executor: applicationTaskExecutor
#  run-id-sequence: run_id_sequence
```

* **`batch.chunk-size`** : Number of items that are processed in a single transaction by a chunk-oriented step, Default: 100.
* **`batch.skip-limit`** : Maximum number of items to skip as per configured Skip policy, exceeding which fails the job, Default: 10.
* **`batch.max-retries`** : Maximum number of retry attempts as configured Retry policy, exceeding which fails the job, Default: 3.
* **`batch.backoff-initial-delay`** : Time duration (in java.time.Duration format) to wait before the first retry attempt is made after a failure, Default: false.
* **`batch.backoff-multiplier`** : Factor by which the delay between consecutive retries is multiplied, Default: 3.
* **`batch.page-size`** : Number of records to be read in each page by Paging Item readers, Default: 100.
* **`batch.partition-size`** : Number of partitions that will be used to process the data concurrently. 
Should be optimized as per available machine resources, Default: 8.
* **`batch.trigger-partitioning-threshold`** : Minimum number of records to trigger partitioning otherwise 
it could be counter productive to do partitioning, Default: 100.
* **`batch.task-executor`** : Bean name of the Task Executor to be used for executing the jobs. By default `SyncTaskExecutor` is used. 
Set to `applicationTaskExecutor` to use `SimpleAsyncTaskExecutor` provided by Spring. 
Or use any other custom `TaskExecutor` and set the bean name here. Don't set this property in Spring cloud task but Spring Rest applications.
* **`batch.run-id-sequence`** : Run Id database sequence name, Default: `run_id_sequence`.

> [!IMPORTANT]
To take benefit from Java 21 Virtual threads with Spring boot 3.2 define a [**`VirtualThreadTaskExecutor`**](https://spring.io/blog/2023/11/23/spring-batch-5-1-ga-5-0-4-and-4-3-10-available-now/#virtual-threads-support) and configure the name as `batch.task-executor`.

## Usage

### Installation
Built on Java 21, Spring boot 3.2.0+ and Spring batch 5.1.0+. For java version 17, build from source by changing the java version as follows.
[**`pom.xml`**](pom.xml)
```xml
<properties>
    <java.version>17</java.version>
</properties>
```

> **Current version: 1.0**

Add the `spring-batch-commons` jar to application dependencies. 

Maven
```xml
<dependency>
    <groupId>io.github.officiallysingh</groupId>
    <artifactId>spring-batch-commons</artifactId>
    <version>1.0</version>
</dependency>
```
Gradle
```groovy
implementation 'io.github.officiallysingh:spring-batch-commons:1.0'
```

### Define Jobs
Define jobs as Beans by extending [`JobConfigurationSupport`](src/main/java/com/ksoot/spring/batch/common/JobConfigurationSupport.java) class.
Default configurations can be overridden for a particular `Job` by overriding respective methods from `JobConfigurationSupport` 
such as `retryPolicy`, `skipPolicy` etc. 
To override default beans globally, define new bean with same name in consumer application.
Refer to example [`StatementJobConfiguration`](https://github.com/officiallysingh/spring-boot-batch-cloud-task/blob/main/src/main/java/com/ksoot/batch/job/StatementJobConfiguration.java)
* Define `ItemReader`, `ItemProcessor` and `ItemWriter` beans for each job.
* To define a simple job, use `simpleJob` method in `JobConfigurationSupport` and return a `Job` bean.
```java
@Bean
Job statementJob(
    final ItemReader<DailyTransaction> transactionReader,
    final ItemProcessor<DailyTransaction, Statement> statementProcessor,
    final ItemWriter<Statement> statementWriter) {
  return newSimpleJob(
      AppConstants.STATEMENT_JOB_NAME,
      transactionReader,
      statementProcessor,
      statementWriter);
}
```

* To define a partitioned job, use `partitionedJob` method in `JobConfigurationSupport` and return a `Job` bean.
```java
@Bean
Job statementJob(
    @Qualifier("statementJobPartitioner") final AccountsPartitioner statementJobPartitioner,
    final ItemReader<DailyTransaction> transactionReader,
    final ItemProcessor<DailyTransaction, Statement> statementProcessor,
    final ItemWriter<Statement> statementWriter)
    throws Exception {
  return newPartitionedJob(
      AppConstants.STATEMENT_JOB_NAME,
      statementJobPartitioner,
      transactionReader,
      statementProcessor,
      statementWriter);
}
```

* Partitioned jobs also require a partitioner bean to define partitioning strategy. 
Define a `Partitioner` bean by extending [`AbstractPartitioner`](src/main/java/com/ksoot/spring/batch/common/AbstractPartitioner.java)
and overriding `partitioningList` method to return `List` of partitioning candidate `String`s.
Refer to example [`AccountsPartitioner`](https://github.com/officiallysingh/spring-boot-batch-cloud-task/blob/main/src/main/java/com/ksoot/batch/job/AccountsPartitioner.java).
> [!NOTE]
> Multiple partitions are created only when total numbers of records returned by `partitioningList` method are greater than `batch.trigger-partitioning-threshold` property.
Otherwise, all records are processed in a single partition.
* Define a Job executor bean by extending [`AbstractJobExecutor`](src/main/java/com/ksoot/spring/batch/common/AbstractJobExecutor.java) to execute the job. 
Refer to example [`StatementJobExecutor`](https://github.com/officiallysingh/spring-boot-batch-cloud-task/blob/main/src/main/java/com/ksoot/batch/job/StatementJobExecutor.java).
* Define a [`SkipListener`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/SkipListener.html) bean to handle skipped records. 
You may want to save skipped records in a separate collection or table and retry later.
Refer to example [`StatementJobSkipListener`](https://github.com/officiallysingh/spring-boot-batch-cloud-task/blob/main/src/main/java/com/ksoot/batch/job/StatementJobSkipListener.java).

> [!IMPORTANT]
Any component needing access to `stepExecutionContext` must be defined as `@StepScope` bean 
and to access `jobParameters` or `jobExecutionContext` must be defined as `@JobScope` bean

## Author
[**Rajveer Singh**](https://www.linkedin.com/in/rajveer-singh-589b3950/), In case you find any issues or need any support, please email me at raj14.1984@gmail.com

## References
* Refer to Spring Batch Job implemented as Spring Cloud Task [**`spring-boot-batch-cloud-task`**](https://github.com/officiallysingh/spring-boot-batch-cloud-task).
* Refer to Spring Batch Job implemented as Spring Rest application [**`spring-boot-batch-web`**](https://github.com/officiallysingh/spring-boot-batch-web).
* For exception handling refer to [**`spring-boot-problem-handler`**](https://github.com/officiallysingh/spring-boot-problem-handler).
* For Spring Data MongoDB Auditing refer to [**`spring-boot-mongodb-auditing`**](https://github.com/officiallysingh/spring-boot-mongodb-auditing).

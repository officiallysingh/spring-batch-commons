# Spring Batch common components

## Introduction
Being a Spring component, Spring batch is also built on same philosophy of reusability and extensibility.
Required components of a Spring batch job can be defined as Beans and can be reused across multiple jobs. 
Also default components can be overridden with custom implementations.

## Classes
Spring batch jobs require a lot of boilerplate code to be written, which is extracted out in this library to promote reusability.
This library provides following default for various Spring batch components, which can be overridden with custom implementations in consumer application.
Following are the classes provided by this library
* [`BatchConguration`](src/main/java/com/example/springbatch/commons/configuration/BatchConfiguration.java) 
Extends [`DefaultBatchConfiguration`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/configuration/support/DefaultBatchConfiguration.html) 
and defines default configuration for Spring batch jobs. It is auto-configured by Spring boot.
* [`AbstractJobExecutor`](src/main/java/com/example/springbatch/commons/executor/AbstractJobExecutor.java) 
Extendable by consumer application Job executor to execute job with **Run Id Incrementer** to force restart the job in case it was successfully completed in last execution.
* [`AbstractPartitioner`](src/main/java/com/example/springbatch/commons/partitioner/AbstractPartitioner.java) Provides common implementation for partitioning Spring batch jobs. 
Consumer applications need to extend this class and provide implementation for **`partitioningList`** method to return `List` of partitioning candidate `String`s.
* [`JobConfigurationSupport`](src/main/java/com/example/springbatch/commons/configuration/JobConfigurationSupport.java) 
Extendable by consumer application to define new Simple and Partitioned jobs with default configurations. 
The defaults can be overridden per job by consumer applications by overriding respective methods. 
Or default can be overridden globally in consumer application by defining new bean for respective component.
* [`LoggingJobListener`](src/main/java/com/example/springbatch/commons/listener/LoggingJobListener.java) 
Provides default implementation for Spring batch job listener, which does nothing but logging only.
* [`LoggingStepListener`](src/main/java/com/example/springbatch/commons/listener/LoggingStepListener.java) 
Provides default implementation for Spring batch step listeners, which does nothing but logging only.
* [`MongoAggregationPagingItemReader`](src/main/java/com/example/springbatch/commons/reader/MongoAggregationPagingItemReader.java) 
Custom Mongo Paging Item reader using aggregation pipeline and pagination.
* [`MongoUpsertItemWriter`](src/main/java/com/example/springbatch/commons/writer/MongoUpsertItemWriter.java) 
Custom Mongo Item writer for upsert operation.
* [`ListFlattenerKafkaItemWriter`](src/main/java/com/example/springbatch/commons/writer/ListFlattenerKafkaItemWriter.java) 
Custom Kafka writer to write a `List` of items to kafka. 
* Can be used in cases where the last `Processor` return a List of items, instead of a single item.
* [`StepStatus`](src/main/java/com/example/springbatch/commons/util/StepStatus.java) 
Utility Class to define custom Step status, can be enhanced to add more statuses.
* [`SkipRecordException`](src/main/java/com/example/springbatch/commons/exception/SkipRecordException.java) 
Custom exception to represent skipped records in Spring batch jobs. Default implementation of `SkipPolicy` includes this exception.
* [`BatchProperties`](src/main/java/com/example/springbatch/commons/util/BatchProperties.java) 
Spring boot configuration property class to read batch properties from `application.properties` or `application.yml` file.

## Auto-configured Components
Following are the components auto-configured as Beans by Spring boot.
* [`JobParametersIncrementer`](https://docs.spring.io/spring-batch/docs/current/api/org/springframework/batch/core/JobParametersIncrementer.html) 
to generate unique run id for each job execution in case of force restarting already successfully completed jobs.
Each Job execution is uniquely identified by combination of its `identifying` parameters.
If a job is restarted with same identifying parameters, Spring batch will throw `JobInstanceAlreadyCompleteException`. So to force restart the job,
[`AbstractJobExecutor#execute`](https://github.com/officiallysingh/spring-batch-commons/blob/04c4a7232f5e36ace5168c498fa96690615799f8/src/main/java/com/ksoot/spring/batch/common/AbstractJobExecutor.java#L22)
method adds a unique `run.id` to the job execution parameters if `forceRestart` argument is `true`.
It requires a database sequence named `run_id_sequence` to generate unique run id.
Sequence name can be overridden by setting `batch.run-id-sequence` property in `application.properties` or `application.yml` file.
> [!IMPORTANT]
You still can not restart already running job, as Spring batch does not allow that. 
Though this behaviour can also be overridden buu not recommended.
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
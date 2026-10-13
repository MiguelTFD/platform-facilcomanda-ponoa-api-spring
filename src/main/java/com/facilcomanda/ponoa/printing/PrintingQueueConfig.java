package com.facilcomanda.ponoa.printing;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrintingQueueConfig {

  public static final String PRINTING_EXCHANGE = "printing.exchange";
  public static final String PRINTING_DLX = "printing.dlx";
  public static final String PRINTING_DLQ = "printing.dlq";
  public static final String PRINTING_JOBS_QUEUE = "printing.jobs";

  @Bean
  public TopicExchange printingExchange() {
    return new TopicExchange(PRINTING_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange printingDeadLetterExchange() {
    return new DirectExchange(PRINTING_DLX, true, false);
  }

  @Bean
  public Queue printingDeadLetterQueue() {
    return QueueBuilder.durable(PRINTING_DLQ).build();
  }

  @Bean
  public Binding deadLetterBinding(
      Queue printingDeadLetterQueue, DirectExchange printingDeadLetterExchange) {
    return BindingBuilder.bind(printingDeadLetterQueue)
        .to(printingDeadLetterExchange)
        .with(PRINTING_DLQ);
  }

  @Bean
  public Queue printingJobsQueue() {
    return QueueBuilder.durable(PRINTING_JOBS_QUEUE)
        .deadLetterExchange(PRINTING_DLX)
        .deadLetterRoutingKey(PRINTING_DLQ)
        .build();
  }

  @Bean
  public Binding printingJobsBinding(Queue printingJobsQueue, TopicExchange printingExchange) {
    return BindingBuilder.bind(printingJobsQueue)
        .to(printingExchange)
        .with("printing.branch.#");
  }
}

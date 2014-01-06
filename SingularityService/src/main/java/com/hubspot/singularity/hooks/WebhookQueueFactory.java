package com.hubspot.singularity.hooks;

import com.google.inject.name.Named;

public interface WebhookQueueFactory {
  
  @Named("DistributedQueue") WebhookQueue createDistributedQueue(String queueName);
  @Named("DistributedPriorityQueue") WebhookQueue createDistributedPriorityQueue(String queueName);
}

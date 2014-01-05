package com.hubspot.singularity.hooks;

public interface WebhookQueueFactory {
  
  WebhookQueue create(String queueName);
  
}

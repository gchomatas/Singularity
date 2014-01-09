package com.hubspot.singularity.hooks;

public interface WebhookQueueFactory {
  ZooKeeperPriorityQueue createDistributedPriorityQueue(String queueName);
}

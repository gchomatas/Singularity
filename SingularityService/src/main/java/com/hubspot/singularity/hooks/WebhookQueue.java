package com.hubspot.singularity.hooks;

public interface WebhookQueue {
  
  public void start() throws Exception;
  public void close() throws Exception;
  void put(WebhookQueuedJob job) throws Exception;
}

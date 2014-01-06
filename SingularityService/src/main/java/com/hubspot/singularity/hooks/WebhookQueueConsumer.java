package com.hubspot.singularity.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Response;

public class WebhookQueueConsumer implements QueueConsumer<WebhookQueuedJob> {

  private final static Logger LOG = LoggerFactory.getLogger(WebhookQueueConsumer.class);
  
  private AtomicInteger webhookRequestFailures = new AtomicInteger(0);
  
  private final AsyncHttpClient asyncHttpClient;
  private final AsyncCompletionHandler<Response> handler;
  
  private final ObjectMapper objectMapper;
  
  @Inject
  public WebhookQueueConsumer(ObjectMapper objectMapper) { 
    this.objectMapper = objectMapper;
    
    Builder builder = new AsyncHttpClientConfig.Builder();
    // TODO add configuration parameters to singularity configuration
    builder
      .setConnectionTimeoutInMs(30 * 1000); // default is 60 * 1000

    asyncHttpClient = new AsyncHttpClient(builder.build());
    
    // Async handler is not really required since we do a blocking request
    // but we leave it for the moment.
    handler = new AsyncCompletionHandler<Response>() {

      @Override
      public Response onCompleted(Response response) throws Exception {
        return response;
      }

      @Override
      public void onThrowable(Throwable t) {
        LOG.warn("Exception while posting to a web hook", t);
        Throwables.propagate(t);
      }
    };
  }
  
	@Override
	public void stateChanged(CuratorFramework curator, ConnectionState connectionState) {
	  LOG.warn(String.format("Zookeeper queue state changed to: %s", connectionState.toString()));
	}
	
	@Override
	public void consumeMessage(WebhookQueuedJob job) throws Exception {
	  LOG.debug(String.format("I am queue consumer thread: %d. Posting to web hook: '%s' a status update about task with id '%s'. The new task status is: '%s'", 
	      Thread.currentThread().getId(), job.getHookURI(), job.getTaskUpdate().getTask().getTaskId(), job.getTaskUpdate().getState().toString()));
	  
    try {
      // We should block until response is back or request fails / timeouts because
      // on failure we should throw an exception to force message to remain in queue and re-consumed
      Response response = asyncHttpClient.preparePost(job.getHookURI())
        .setBody(job.getTaskUpdate().getAsBytes(objectMapper))
        .addHeader("Content-Type", "application/json")
        .execute(handler).get();
      
      if (!response.hasResponseStatus()) {
        throw new WebhookException("No Response status from web hook server");
      }
      
      if (response.getStatusCode() >= 200 && response.getStatusCode() <= 299) {
        // reset failures counter
        webhookRequestFailures.getAndSet(0);
      }
      else if (response.getStatusCode() >= 400) {
        throw new WebhookException(String.format("Web hook: '%s' returned the error code: '%s'.", job.getHookURI(), response.getStatusCode()));
      }
      
    }
    catch (Throwable t) {
      int failures = webhookRequestFailures.incrementAndGet();
      int retryDelayInMs = getRetryDelayInMs(failures);
      LOG.warn(String.format("Web hook: '%s' is unavailable. Number of failures so far is: '%d'. We will wait for: '%d' seconds and then will requeue the task update job for task: '%s - %s'.",
          job.getHookURI(), failures, (retryDelayInMs / 1000), job.getTaskUpdate().getTask().getTaskId(), job.getTaskUpdate().getState().toString()));
      
      Thread.sleep(retryDelayInMs, 0);
      
      // re-throw the exception so that job will be re-queued
      Throwables.propagate(t);
    }
		
	}
	
	// Double the retry time for the first 12 retries and then retry every hour
	// TODO: configure through singularity configuration file
	private int getRetryDelayInMs(int failures) {
	  if (failures > 12) {
	    return 60 * 60 * 1000;
	  }
	  else {
	    return (1 << failures) * 1000;
	  }
	}
  
  @SuppressWarnings("serial")
  public class WebhookException extends Exception {
    
    public WebhookException(String message) {
      super(message);
    }
  }
  
}

package com.hubspot.singularity.hooks;

import java.io.IOException;

import org.apache.curator.framework.recipes.queue.QueueSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

public class WebhookSerializer implements QueueSerializer<WebhookQueuedJob> {
	
	ObjectMapper objectMapper;
	
	@Inject
	public WebhookSerializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}
	
    public WebhookQueuedJob deserialize(byte[] buffer) {   
    	try {
			return WebhookQueuedJob.fromBytes(buffer, objectMapper);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
    }

    public byte[] serialize(WebhookQueuedJob job) {
            return job.getAsBytes(objectMapper);
    }


}

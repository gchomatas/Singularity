package com.hubspot.singularity.hooks;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.singularity.SingularityJsonObject;
import com.hubspot.singularity.SingularityTaskUpdate;

public class WebhookQueuedJob extends SingularityJsonObject {
	private final String hookURI;
	private final SingularityTaskUpdate taskUpdate;
	
	@JsonCreator
	public WebhookQueuedJob(@JsonProperty("hookURI") String hookURI, @JsonProperty("taskUpdate") SingularityTaskUpdate taskUpdate) {
		this.hookURI = hookURI;
		this.taskUpdate = taskUpdate;
	}
	
	public String getHookURI() {
		return hookURI;
	}
	
	public SingularityTaskUpdate getTaskUpdate() {
		return taskUpdate;
	}
	
	public static WebhookQueuedJob fromBytes(byte[] bytes, ObjectMapper objectMapper) throws JsonParseException, JsonMappingException, IOException {
	    return objectMapper.readValue(bytes, WebhookQueuedJob.class);
	  }
}

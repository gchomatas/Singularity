package com.hubspot.singularity.resources;

import static com.google.common.base.Preconditions.*;
import static io.dropwizard.testing.FixtureHelpers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import io.dropwizard.jackson.Jackson;

import java.util.List;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import com.hubspot.singularity.hooks.WebhookManager;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;

@RunWith(JukitoRunner.class)
public class WebhookResourceTest {
  
  public static class Module extends JukitoModule {
    protected void configureTest() {
    }
    
    private static ObjectMapper createObjectMapper() {
      return Jackson.newObjectMapper()
          .setSerializationInclusion(Include.NON_NULL)
          .registerModule(new ProtobufModule());
    }
    
    public static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    @Provides
    @Singleton
    public ObjectMapper getObjectMapper() {
      return OBJECT_MAPPER;
    }
    
    @Provides
    @Singleton
    public WebhookManager getWebhookManager() {
      WebhookManager webhookManager = mock(WebhookManager.class);
      return webhookManager;
    }
  }
  
  private ResourceTest resourceTest;
  private WebhookResource webhookResource;
  private final List<String> webhooks = Lists.newArrayList("http://domain1.com/webhook1","http://domain2.com/webhook2");
  private final String testWebhook = "http://domain1.com/webhook1";
  
  @Before
  public void setup(WebhookResource webhookResource) throws Exception {
    checkNotNull(webhookResource, "web hook resource should not be null");
    this.webhookResource = webhookResource;
    resourceTest = ResourceTest.builder().addResource(webhookResource).build();
    resourceTest.getJerseyTest().setUp();
  }
      
  @After
  public void teardown() throws Exception {
    if (resourceTest != null) {
      resourceTest.getJerseyTest().tearDown();
    }
  }
  
  @Test
  public void addWebhooks() throws Exception {
    ClientResponse clientResponse = resourceTest.client().resource("/webhooks").header("Content-Type", "application/json").post(ClientResponse.class, fixture("fixtures/webhooks.json"));
    assertEquals("NO CONTENT (204) status is returned", Status.NO_CONTENT, clientResponse.getClientResponseStatus());
    verify(webhookResource.getWebhookManager(), times(2)).addHook(anyString());
    verify(webhookResource.getWebhookManager()).addHook("http://domain1.com/webhook1");
    verify(webhookResource.getWebhookManager()).addHook("http://domain2.com/webhook2");
  }
  
  @Test
  public void getWebhooks() throws Exception {
    ClientResponse clientResponse = resourceTest.client().resource("/webhooks").header("Content-Type", "application/json").get(ClientResponse.class);
    assertEquals("OK (200) status is returned", Status.OK, clientResponse.getClientResponseStatus());
    verify(webhookResource.getWebhookManager()).getWebhooks();
  }
  
  @Test
  public void removeHook() throws Exception {
    ClientResponse clientResponse = resourceTest.client().resource("/webhooks/" + testWebhook).header("Content-Type", "application/json").delete(ClientResponse.class);
    assertEquals("NO CONTENT (204) status is returned", Status.NO_CONTENT, clientResponse.getClientResponseStatus());
    verify(webhookResource.getWebhookManager()).removeHook(testWebhook);
  }
  
  @Test
  public void serializesToJSON(ObjectMapper mapper) throws Exception {
    checkNotNull(mapper, "mapper should not be null");
    assertEquals("A List of webhook URLs (strings) is serialized to a JSON array of strings", fixture("fixtures/webhooks.json"), mapper.writeValueAsString(webhooks));
  }

  @Test
  public void deserializesFromJSON(ObjectMapper mapper) throws Exception {
    assertEquals("A JSON array of strings can be deserialized to a List of strings (webhook URLs)", webhooks, mapper.readValue(fixture("fixtures/webhooks.json"), List.class));
  }
}

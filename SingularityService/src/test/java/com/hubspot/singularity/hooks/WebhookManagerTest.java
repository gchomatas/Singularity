package com.hubspot.singularity.hooks;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.hubspot.mesos.JavaUtils;

@RunWith(JukitoRunner.class)
public class WebhookManagerTest {
  
  public static class Module extends JukitoModule {
    
    private final List<String> webhooks = Lists.newArrayList("http://domain1.com/webhook1","http://domain2.com/webhook2");
    
    protected void configureTest() {
      bind(WebhookManager.class).in(Scopes.SINGLETON);
    }

    @Singleton
    @Provides
    public WebhookSerializer provideWebhookSerializer() {
      return mock(WebhookSerializer.class);
    }
    
    @Singleton
    @Provides
    public WebhookQueueFactory provideWebhookQueueFactory() {
      WebhookQueueFactory webhookQueueFactory = mock(WebhookQueueFactory.class);
      when(webhookQueueFactory.createDistributedPriorityQueue(anyString())).thenAnswer(new Answer<ZooKeeperPriorityQueue>() {
        public ZooKeeperPriorityQueue answer(InvocationOnMock invocation) throws Throwable {
          return mock(ZooKeeperPriorityQueue.class);
        }
     });
      return webhookQueueFactory;
    }
    
    @Singleton
    @Provides
    public CuratorFramework provideCurator() throws Exception {
      CuratorFramework curator = mock(CuratorFramework.class);
      GetChildrenBuilder getChildrenBuilder = mock(GetChildrenBuilder.class);
      when(getChildrenBuilder.forPath(anyString())).thenReturn(webhooks);
      when(curator.getChildren()).thenReturn(getChildrenBuilder);
      return curator;
    }
    
    @Provides
    @Singleton
    public LeaderLatch provideLeaderLatch() {
      return mock(LeaderLatch.class);
    }
  }
  
  private static final String webhook1 = "http://domain1.com/webhook1";
  private static final String webhook2 = "http://domain2.com/webhook2";
  private static final String encodedWebhook1 = JavaUtils.urlEncode(webhook1);
  private static final String encodedWebhook2 = JavaUtils.urlEncode(webhook2);
  
  @Inject
  private WebhookManager webhookManager;
  
  @Test
  public void isLeader_first_time() throws Exception {
    webhookManager.isLeader();
    Field webhookQueuesField = webhookManager.getClass().getDeclaredField("webhookQueues");
    webhookQueuesField.setAccessible(true);
    @SuppressWarnings("unchecked")
    final Map<String, ZooKeeperPriorityQueue> webhookQueues = (Map<String, ZooKeeperPriorityQueue>) webhookQueuesField.get(webhookManager);
    assertTrue("The Queues Map contains a queue for webhook '" + webhook1 + "'", webhookQueues.containsKey(encodedWebhook1));
    assertTrue("The Queues Map contains a queue for webhook '" + webhook2 + "'", webhookQueues.containsKey(encodedWebhook2));
    
  }
  
  @Test
  public void notLeader_after_being_leader() throws Exception {
    final Map<String, ZooKeeperPriorityQueue> webhookQueues = getPrivatefieldWebhookQueues(webhookManager);
    webhookManager.isLeader();
    ZooKeeperPriorityQueue queue1 = webhookQueues.get(encodedWebhook1);
    ZooKeeperPriorityQueue queue2 = webhookQueues.get(encodedWebhook2);
    webhookManager.notLeader();
    verify(queue1).close();
    verify(queue2).close();
    assertTrue("The Webhook Queues Map is empty", webhookQueues.isEmpty());
  }
  
  @SuppressWarnings("unchecked")
  private Map<String, ZooKeeperPriorityQueue> getPrivatefieldWebhookQueues(WebhookManager webhookManager) throws Exception {
    Field webhookQueuesField = webhookManager.getClass().getDeclaredField("webhookQueues");
    webhookQueuesField.setAccessible(true);
    return (Map<String, ZooKeeperPriorityQueue>) webhookQueuesField.get(webhookManager);
  }
  
}

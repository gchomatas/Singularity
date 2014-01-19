package com.hubspot.singularity.hooks;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.hubspot.mesos.JavaUtils;
import com.hubspot.singularity.SingularityTaskUpdate;
import com.hubspot.singularity.data.CuratorManager;

public class WebhookManager extends CuratorManager {

  private final static Logger LOG = LoggerFactory.getLogger(WebhookManager.class);

  private static final String HOOK_ROOT_PATH = "/hooks";
  private static final String HOOK_PATH_FORMAT = HOOK_ROOT_PATH + "/%s";

  private WebhookQueueFactory webhookQueueFactory;
  private Map<String, ZooKeeperPriorityQueue> webhookQueues = Maps.newConcurrentMap();

  @Inject
  public WebhookManager(CuratorFramework curator, WebhookQueueFactory webhookQueueFactory) {
    super(curator);

    this.webhookQueueFactory = webhookQueueFactory;

    // Initialize hook queues for existing web hooks
    try {
      for (String webhook : getWebhooks()) {
        final String webhookQueueName = JavaUtils.urlEncode(webhook);
        ZooKeeperPriorityQueue queue = webhookQueueFactory.createDistributedPriorityQueue(webhookQueueName);
        webhookQueues.put(webhookQueueName, queue);
      }
    } catch (Throwable t) {
      Throwables.propagate(t);
    }

  }

  public void notify(SingularityTaskUpdate taskUpdate) {
    for (String hookURI : getWebhooks()) {
      LOG.trace(String.format("Queuing task update job for hook '%s'. Task id is: %s, task status is: %s", 
          hookURI, taskUpdate.getTask().getTaskId(), taskUpdate.getState().toString()));

      try {
        final String webhookQueueName = JavaUtils.urlEncode(hookURI);
        ZooKeeperPriorityQueue queue = webhookQueues.get(webhookQueueName);
        WebhookQueuedJob job = new WebhookQueuedJob(hookURI, taskUpdate);
        queue.put(job);
      } catch (Exception e) {
        LOG.warn(String.format("Exception while preparing to queue task update job for hook: %s", hookURI), e);
      }
    }
  }

  public List<String> getWebhooks() {
    return loadHooks();
  }

  private List<String> loadHooks() {
    try {
      final List<String> hooks = curator.getChildren().forPath(HOOK_ROOT_PATH);
      final List<String> decodedHooks = Lists.newArrayListWithCapacity(hooks.size());
      for (String hook : hooks) {
        decodedHooks.add(JavaUtils.urlDecode(hook));
      }
      return decodedHooks;
    } catch (NoNodeException nee) {
      return Collections.emptyList();
    } catch (Exception e) {
      // TODO fix this - throw a curator exception.
      throw Throwables.propagate(e);
    }
  }

  public void addHook(String uri) {
    try {
      final String path = getHookPath(uri);
      if (!exists(path)) {
        create(path);
        final String webhookQueueName = JavaUtils.urlEncode(uri);
        ZooKeeperPriorityQueue queue = webhookQueueFactory.createDistributedPriorityQueue(webhookQueueName);
        webhookQueues.put(webhookQueueName, queue);
      } else {
        LOG.warn(String.format("Web hook: '%s' already exists", uri));
      }
    } catch (Exception e) {
      LOG.warn(String.format("Failed to add web hook: %s", uri), e);
      Throwables.propagate(e);
    }
  }

  public void removeHook(String uri) {
    try {
      final String path = getHookPath(uri);
      if (exists(path)) {
        delete(path);

        final String webhookQueueName = JavaUtils.urlEncode(uri);
        ZooKeeperPriorityQueue queue = webhookQueues.get(webhookQueueName);
        queue.close();
        webhookQueues.remove(webhookQueueName);
      } else {
        LOG.warn(String.format("Web hook: '%s' does not exists", uri));
      }
    } catch (Exception e) {
      LOG.warn(String.format("Failed to remove web hook: %s", uri), e);
      Throwables.propagate(e);
    }
  }

  private String getHookPath(String uri) {
    return String.format(HOOK_PATH_FORMAT, JavaUtils.urlEncode(uri));
  }

}

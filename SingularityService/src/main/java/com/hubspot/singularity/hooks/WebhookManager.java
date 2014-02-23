package com.hubspot.singularity.hooks;

import io.dropwizard.lifecycle.Managed;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
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

public class WebhookManager extends CuratorManager implements Managed, LeaderLatchListener {

  private static final Logger log = LoggerFactory.getLogger(WebhookManager.class);

  private static final String HOOK_ROOT_PATH = "/hooks";
  private static final String HOOK_PATH_FORMAT = HOOK_ROOT_PATH + "/%s";

  private final WebhookQueueFactory webhookQueueFactory;
  private final Map<String, ZooKeeperPriorityQueue> webhookQueues = Maps.newConcurrentMap();

  @Inject
  public WebhookManager(CuratorFramework curator, WebhookQueueFactory webhookQueueFactory, LeaderLatch leaderLatch) {
    super(curator);

    this.webhookQueueFactory = webhookQueueFactory;
    leaderLatch.addListener(this);
  }

  @Override
  public void start() throws Exception {
    log.info("Starting Web hooks manager...");
  }

  @Override
  public void stop() throws Exception {
    log.info("Gracefully Stopping Web hooks manager...");
    closeWebhookQueues();
  }

  /**
   * Only one singularity instance, the master, consumes the web hook queues. We
   * need this because we want queue consumption to be perfectly ordered so that
   * task updates arrive at web hook URLs properly ordered. Despite the fact
   * that each queue has a central lock, the lock is utilized per consumer
   * instance and does not block multiple instances from simultaneously
   * consuming the queue, which can result in unordered processing. So if we are
   * the leader we start our queues in order to start consuming messages. The
   * Singularity instance that is not a leader anymore will stop consuming
   * messages (see bellow {@link WebhookManager#notLeader()})
   */
  @Override
  public void isLeader() {
    log.debug("We are the master. Starting queues for all web hooks to begin consumption of already queued messages");
    startWebhookQueues();
  }

  /**
   * If we are not the leader anymore we close our queues to prevent further
   * consumption from this singularity instance. Notice that we only close our
   * queue connections, we do not remove the queues from Zookeeper so that the
   * current leader can consume them.
   */
  @Override
  public void notLeader() {
    log.debug("We are not the master any more. Closing queues for all web hook queues to prevent any further consumption of queued messages");
    closeWebhookQueues();
  }

  public void notify(SingularityTaskUpdate taskUpdate) {
    for (String hookURI : getWebhooks()) {
      log.trace("Queuing task update job for hook '{}'. Task id is: {}, task status is: {}", hookURI, taskUpdate.getTask().getTaskId(), taskUpdate.getState().toString());

      try {
        ZooKeeperPriorityQueue queue = getWebhookQueue(hookURI);
        WebhookQueuedJob job = new WebhookQueuedJob(hookURI, taskUpdate);
        queue.put(job);
      } catch (Throwable t) {
        log.error("Exception while queuing task update job for hook: '{}'", hookURI);
        Throwables.propagate(t);
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
      } else {
        log.warn("Web hook: '{}' already exists", uri);
      }
    } catch (Exception e) {
      log.error("Failed to add web hook: {}", uri);
      Throwables.propagate(e);
    }
  }

  /**
   * We assume here that when a web hook is removed we are not any more
   * interested to deliver the remaining queued task updates to the removed web
   * hook. So if we have queues started (we are the master) we close our open
   * queues and then completely remove them from Zookeeper. if we are not the
   * master we should still remove the queue and queue lock paths from Zookeeper
   * so that queue consumption by the master will stop.
   * 
   * @param uri
   *          the URI of the hook to remove
   */
  public void removeHook(String uri) {
    try {
      final String path = getHookPath(uri);
      if (exists(path)) {
        delete(path);

        final String webhookQueueName = JavaUtils.urlEncode(uri);
        if (webhookQueues.containsKey(webhookQueueName)) {
          ZooKeeperPriorityQueue queue = webhookQueues.get(webhookQueueName);
          queue.close();
          queue.remove();
          webhookQueues.remove(webhookQueueName);
        } else {
          delete(ZooKeeperPriorityQueue.getQueuePath(webhookQueueName));
          delete(ZooKeeperPriorityQueue.getQueueLockPath(webhookQueueName));
        }

      } else {
        // TODO: may be better to throw an exception since the user 
        // tries to remove a non existent hook and should not get back a 200 which denotes success   
        log.warn("Web hook: '{}' does not exist", uri);
      }
    } catch (Exception e) {
      log.warn("Failed to remove web hook: {}", uri);
      Throwables.propagate(e);
    }
  }

  private String getHookPath(String uri) {
    return String.format(HOOK_PATH_FORMAT, JavaUtils.urlEncode(uri));
  }

  /**
   * Initialize hook queues for all existing web hooks
   */
  private void startWebhookQueues() {
    try {
      for (String webhook : getWebhooks()) {
        getWebhookQueue(webhook);
      }
    } catch (Throwable t) {
      log.error("An error occured during initialization of queues for existing web hooks");
      Throwables.propagate(t);
    }
  }

  /**
   * close hook queues for all existing web hooks
   */
  private void closeWebhookQueues() {
    try {
      for (String webhook : getWebhooks()) {
        String webhookQueueName = JavaUtils.urlEncode(webhook);
        if (webhookQueues.containsKey(webhookQueueName)) {
          ZooKeeperPriorityQueue queue = webhookQueues.get(webhookQueueName);
          queue.close();
          webhookQueues.remove(webhookQueueName);
        }
      }
    } catch (Throwable t) {
      log.error("An error occured while closing queues all for existing web hooks");
      Throwables.propagate(t);
    }
  }

  private ZooKeeperPriorityQueue getWebhookQueue(String uri) {
    final String webhookQueueName = JavaUtils.urlEncode(uri);
    if (webhookQueues.containsKey(webhookQueueName)) {
      return webhookQueues.get(webhookQueueName);
    } else {
      ZooKeeperPriorityQueue queue = webhookQueueFactory.createDistributedPriorityQueue(webhookQueueName);
      webhookQueues.put(webhookQueueName, queue);
      return queue;
    }
  }

}

package com.hubspot.singularity.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedPriorityQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.hubspot.singularity.data.CuratorManager;

public class ZooKeeperPriorityQueue extends CuratorManager {

  private static final String QUEUE_ROOT_PATH = "/hook-queues";
  private static final String QUEUE_PATH_FORMAT = QUEUE_ROOT_PATH + "/%s";
  private static final String QUEUE_LOCK_ROOT_PATH = "/hook-queue-locks";
  private static final String QUEUE_LOCK_PATH_FORMAT = QUEUE_LOCK_ROOT_PATH + "/%s";

  private final DistributedPriorityQueue<WebhookQueuedJob> queue;
  private final String queueName;
  private AtomicInteger priority = new AtomicInteger(0);

  @Inject
  public ZooKeeperPriorityQueue(CuratorFramework curator, WebhookQueueConsumer consumer, WebhookSerializer serializer, @Assisted String queueName) {
    super(curator);
    this.queueName = queueName;
    String queuePath = getQueuePath(queueName);
    String queueLockPath = getQueueLockPath(queueName);
    QueueBuilder<WebhookQueuedJob> builder = QueueBuilder.builder(curator, consumer, serializer, queuePath);
    queue = builder.lockPath(queueLockPath).buildPriorityQueue(1);
    try {
      start();
    } catch (Throwable t) {
      Throwables.propagate(t);
    }

  }

  public void start() throws Exception {
    queue.start();
  }

  public void close() throws Exception {
    queue.close();
    delete(getQueuePath(queueName));
    delete(getQueueLockPath(queueName));
  }

  public void put(WebhookQueuedJob job) throws Exception {
    queue.put(job, priority.getAndIncrement());
  }

  private String getQueuePath(String queueName) {
    return String.format(QUEUE_PATH_FORMAT, queueName);
  }

  private String getQueueLockPath(String queueName) {
    return String.format(QUEUE_LOCK_PATH_FORMAT, queueName);
  }

}

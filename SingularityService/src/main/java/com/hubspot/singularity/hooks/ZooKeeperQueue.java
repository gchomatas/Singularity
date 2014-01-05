package com.hubspot.singularity.hooks;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.hubspot.mesos.Resources;
import com.hubspot.singularity.SingularityPendingRequestId.PendingType;
import com.hubspot.singularity.data.CuratorManager;
import com.hubspot.singularity.SingularityPendingTaskId;
import com.hubspot.singularity.SingularityRequest;
import com.hubspot.singularity.SingularityRequestBuilder;
import com.hubspot.singularity.SingularityTask;
import com.hubspot.singularity.SingularityTaskId;
import com.hubspot.singularity.SingularityTaskRequest;
import com.hubspot.singularity.SingularityTaskUpdate;

public class ZooKeeperQueue extends CuratorManager implements WebhookQueue {
  
  private static final String QUEUE_ROOT_PATH = "/hook-queues";
  private static final String QUEUE_PATH_FORMAT = QUEUE_ROOT_PATH + "/%s";
  private static final String QUEUE_LOCK_ROOT_PATH = "/hook-queue-locks";
  private static final String QUEUE_LOCK_PATH_FORMAT = QUEUE_LOCK_ROOT_PATH + "/%s";
  
  private final DistributedQueue<WebhookQueuedJob> queue;
  private final String queueName;
  
  @Inject
  public ZooKeeperQueue(CuratorFramework curator, WebhookQueueConsumer consumer, WebhookSerializer serializer, @Assisted String queueName) {
    super(curator);
    this.queueName = queueName;
    String queuePath = getQueuePath(queueName);
    String queueLockPath = getQueueLockPath(queueName);
    QueueBuilder<WebhookQueuedJob> builder = QueueBuilder.builder(curator, consumer, serializer, queuePath);
    queue = builder
        .lockPath(queueLockPath)
        .buildQueue();
    try {
      start();
    }
    catch (Throwable t) {
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
    queue.put(job);
  }

  
  private String getQueuePath(String queueName) {
    return String.format(QUEUE_PATH_FORMAT, queueName);
  }
  
  private String getQueueLockPath(String queueName) {
    return String.format(QUEUE_LOCK_PATH_FORMAT, queueName);
  }
  
}

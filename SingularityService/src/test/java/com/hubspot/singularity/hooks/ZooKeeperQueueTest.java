package com.hubspot.singularity.hooks;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;

import com.hubspot.mesos.Resources;
import com.hubspot.singularity.SingularityPendingTaskId;
import com.hubspot.singularity.SingularityRequest;
import com.hubspot.singularity.SingularityRequestBuilder;
import com.hubspot.singularity.SingularityTask;
import com.hubspot.singularity.SingularityTaskId;
import com.hubspot.singularity.SingularityTaskRequest;
import com.hubspot.singularity.SingularityTaskUpdate;
import com.hubspot.singularity.SingularityPendingRequestId.PendingType;

public class ZooKeeperQueueTest {
  
  public void queueMessages() throws Exception {
    for (int i = 0; i < 10; i++) {
      SingularityRequest singularityRequest = new SingularityRequestBuilder()
          .setCommand("sleep 600").setDaemon(false).setId("request0")
          .setInstances(5).setName("TestRequest")
          .setMaxFailuresBeforePausing(10).setNumRetriesOnFailure(2)
          .setResources(new Resources(1, 128, 1)).build();
      SingularityPendingTaskId singularityPendingTaskId = new SingularityPendingTaskId(
          "request0", 0, 0, PendingType.REGULAR);
      SingularityTaskRequest singularityTaskRequest = new SingularityTaskRequest(
          singularityRequest, singularityPendingTaskId);
      SingularityTaskId singularityTaskId = new SingularityTaskId("request0",
          0, 0, "myhost", "myrack");
      Offer offer = Offer.getDefaultInstance();
      TaskInfo taskInfo = TaskInfo.getDefaultInstance();
      SingularityTask singularityTask = new SingularityTask(
          singularityTaskRequest, singularityTaskId, offer, taskInfo);
      SingularityTaskUpdate singularityTaskUpdate = new SingularityTaskUpdate(
          singularityTask, TaskState.TASK_STARTING);
      WebhookQueuedJob job = new WebhookQueuedJob("testWork [" + i + "]",
          singularityTaskUpdate);
      // queue.put(job);
      System.out.println("Queued [" + i + "]");
    }
  }
}

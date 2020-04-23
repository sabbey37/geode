/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.apache.geode.redis;

import static java.lang.String.valueOf;
import static org.apache.geode.distributed.ConfigurationProperties.MAX_WAIT_TIME_RECONNECT;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_PORT;
import static org.apache.geode.test.awaitility.GeodeAwaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.test.awaitility.GeodeAwaitility;
import org.apache.geode.test.dunit.SerializableCallable;
import org.apache.geode.test.dunit.SerializableRunnable;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.rules.ExecutorServiceRule;
import org.apache.geode.test.junit.rules.GfshCommandRule;

public class PubSubClientFailureTest {

  public static final String CHANNEL_NAME = "salutations";

  @ClassRule
  public static ClusterStartupRule cluster = new ClusterStartupRule(4).withLogFile();

  @ClassRule
  public static GfshCommandRule gfsh = new GfshCommandRule();

  @ClassRule
  public static ExecutorServiceRule executor = new ExecutorServiceRule();

  private static int[] ports;

  static final String LOCAL_HOST = "127.0.0.1";

  static Properties locatorProperties;
  static Properties serverProperty;

  static MemberVM locatorVM;
  static MemberVM serverVM;

  static MemberVM publisherVM;
  static MemberVM subsciberVM;

  static final int INDEX_OF_LOCATOR_VM = 0;
  static final int INDEX_OF_SERVER_VM = 1;
  static final int INDEX_OF_PUBLISHER_VM = 2;
  static final int INDEX_OF_SUBSCRIBER_VM = 3;

  private static class ConcurrentPublishOperation extends SerializableCallable<Object> {

    private final String hostname;
    private final int port;
    private final String
        message =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec hendrerit luctus enim, quis suscipit massa lobortis a. Vestibulum ullamcorper nec felis sit amet consequat. Praesent interdum erat ut turpis scelerisque consequat. Etiam ornare, tortor non accumsan blandit, neque nibh ullamcorper diam, vitae blandit leo erat in metus. Phasellus feugiat felis erat, sed pretium justo ullamcorper vel. Aliquam ultrices tellus elit, nec tincidunt libero consectetur sit amet. Cras a quam pulvinar, finibus orci sed, pharetra ipsum. Nam nibh dui, mollis vel rutrum eu, pretium ut eros. Donec luctus odio neque, ut molestie purus posuere quis.\n"
            + "Morbi rutrum lectus a dignissim consectetur. Vivamus luctus, est vel luctus venenatis, sapien mi elementum ipsum, sed scelerisque metus enim vitae nisi. In pulvinar porta odio, ut lobortis libero semper vel. Donec elementum placerat nisl vestibulum lobortis. Nam sit amet sodales lacus. Vivamus pellentesque consequat velit. Integer non lacus eget dolor erat curae.";
    private final long publishCount;

    public ConcurrentPublishOperation(String hostname, int port, long publishCount) {
      this.hostname = hostname;
      this.port = port;
      this.publishCount = publishCount;
    }

    @Override
    public Object call() {
      Jedis jedis = new Jedis(hostname, port, 10000000);
      long subscribersPublishedTo = 0;
      for (int i = 0; i < publishCount; i++) {
        subscribersPublishedTo += jedis.publish("hello", message.substring(0, new Random().nextInt(message.length())));
      }
      return subscribersPublishedTo;
    }
  }

  private static class ConcurrenSubscriptionOperation extends SerializableRunnable {

    private final String hostname;
    private final int port;

    public ConcurrenSubscriptionOperation(String hostname, int port) {
      this.hostname = hostname;
      this.port = port;
    }

    @Override
    public void run() {
      ArrayList<Jedis> jedisList = new ArrayList<>();
      for (int i = 0; i < 1; i++) {
        Jedis jedis = new Jedis(hostname, port, 10000000);
        jedis.subscribe(new JedisPubSub() {
          @Override
          public void onMessage(String channel, String message) {
            super.onMessage(channel, message);
          }
        }, "hello");
        jedisList.add(jedis);
      }
      while (true) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    ports = AvailablePortHelper.getRandomAvailableTCPPorts(1);

    locatorProperties = new Properties();
    serverProperty = new Properties();

    locatorProperties.setProperty(MAX_WAIT_TIME_RECONNECT, "15000");

    serverProperty.setProperty(REDIS_PORT, valueOf(ports[0]));
    serverProperty.setProperty(REDIS_BIND_ADDRESS, LOCAL_HOST);

    locatorVM = cluster.startLocatorVM(INDEX_OF_LOCATOR_VM, locatorProperties);
    serverVM = cluster.startServerVM(INDEX_OF_SERVER_VM, serverProperty, locatorVM.getPort());

    publisherVM = cluster.startLocatorVM(INDEX_OF_PUBLISHER_VM, locatorProperties);
    subsciberVM = cluster.startLocatorVM(INDEX_OF_SUBSCRIBER_VM, locatorProperties);

    gfsh.connectAndVerify(locatorVM);
  }

  @AfterClass
  public static void tearDown() {
    locatorVM.stop();
    serverVM.stop();
  }

  @Test
  public void recreateStuckThreadIssue() throws InterruptedException {
    for (int i = 0; i < 10; i++) {
      subsciberVM.invokeAsync(new ConcurrenSubscriptionOperation(LOCAL_HOST, ports[0]));
    }

    GeodeAwaitility.await().until(
        () -> (Long) publisherVM.invoke(new ConcurrentPublishOperation(LOCAL_HOST, ports[0], 1))
            > 5L);

    Thread publisherThread = new Thread(() -> {
      publisherVM.invoke(new ConcurrentPublishOperation(LOCAL_HOST, ports[0], 50));
    });

    Thread publisherThread2 = new Thread(() -> {
      publisherVM.invoke(new ConcurrentPublishOperation(LOCAL_HOST, ports[0], 100));
    });

    publisherThread.start();
    publisherThread2.start();

    publisherThread.join();
    cluster.crashVM(INDEX_OF_SUBSCRIBER_VM);

    publisherThread2.join();
  }
}
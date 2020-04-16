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

import static org.apache.geode.distributed.ConfigurationProperties.MAX_WAIT_TIME_RECONNECT;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.test.awaitility.GeodeAwaitility;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.rules.ExecutorServiceRule;

public class PubSubDUnitTest {

  public static final String CHANNEL_NAME = "salutations";

  @ClassRule
  public static ClusterStartupRule cluster = new ClusterStartupRule(4);

  @ClassRule
  public static ExecutorServiceRule executor = new ExecutorServiceRule();

  private static int[] ports;

  static final String LOCAL_HOST = "127.0.0.1";
  static final int SET_SIZE = 1000;
  static Jedis subscriber1;
  static Jedis subscriber2;
  static Jedis publisher;

  static Properties locatorProperties;
  static Properties serverProperties1;
  static Properties serverProperties2;
  static Properties serverProperties3;

  static MemberVM oldServer1;
  static MemberVM oldServer2;

  static MemberVM locator;
  static MemberVM server1;
  static MemberVM server2;
  static MemberVM server3;

  @BeforeClass
  public static void beforeClass() {
    locatorProperties = new Properties();
    serverProperties1 = new Properties();
    serverProperties2 = new Properties();
    serverProperties3 = new Properties();

    locatorProperties.setProperty(MAX_WAIT_TIME_RECONNECT, "15000");

    serverProperties1.setProperty(REDIS_PORT, "6371");
    serverProperties1.setProperty(REDIS_BIND_ADDRESS, LOCAL_HOST);

    serverProperties2.setProperty(REDIS_PORT, "6372");
    serverProperties2.setProperty(REDIS_BIND_ADDRESS, LOCAL_HOST);

    serverProperties3.setProperty(REDIS_PORT, "6373");
    serverProperties3.setProperty(REDIS_BIND_ADDRESS, LOCAL_HOST);

    ports = AvailablePortHelper.getRandomAvailableTCPPorts(2);
    Properties redisProps = new Properties();
    redisProps.put(ConfigurationProperties.REDIS_BIND_ADDRESS, "localhost");

    MemberVM oldLocator = cluster.startLocatorVM(0);

    redisProps.put(ConfigurationProperties.REDIS_PORT, Integer.toString(ports[0]));
    oldServer1 = cluster.startServerVM(1, redisProps, oldLocator.getPort());
    redisProps.put(ConfigurationProperties.REDIS_PORT, Integer.toString(ports[1]));
    oldServer2 = cluster.startServerVM(2, redisProps, oldLocator.getPort());

    locator = cluster.startLocatorVM(0, locatorProperties);
    server1 = cluster.startServerVM(1, serverProperties1, locator.getPort());
    server2 = cluster.startServerVM(2, serverProperties2, locator.getPort());
    server3 = cluster.startServerVM(3, serverProperties3, locator.getPort());

    subscriber1 = new Jedis(LOCAL_HOST, 6371);
    subscriber2 = new Jedis(LOCAL_HOST, 6372);
    publisher = new Jedis(LOCAL_HOST, 6373);
  }

  @Before
  public void testSetup() {
    subscriber1.flushAll();
  }

  @AfterClass
  public static void tearDown() {
    subscriber1.disconnect();
    subscriber2.disconnect();
    publisher.disconnect();

    server1.stop();
    server2.stop();
    server3.stop();
  }

  @Test
  public void shouldContinueToFunction_whenOneSubscriberShutsDownGracefully_givenTwoSubscribers() {

    Runnable subscriberRunnable = () -> subscriber1.subscribe();
  }

  @Test
  public void testSubscribePublishUsingDifferentServers() throws Exception {
    Jedis subscriber1 = new Jedis("localhost", ports[0]);
    Jedis subscriber2 = new Jedis("localhost", ports[1]);
    Jedis publisher = new Jedis("localhost", ports[1]);

    CountDownLatch latch = new CountDownLatch(2);
    MockSubscriber mockSubscriber1 = new MockSubscriber(latch);
    MockSubscriber mockSubscriber2 = new MockSubscriber(latch);

    Future<Void> subscriber1Future = executor.submit(
        () -> subscriber1.subscribe(mockSubscriber1, CHANNEL_NAME));
    Future<Void> subscriber2Future = executor.submit(
        () -> subscriber2.subscribe(mockSubscriber2, CHANNEL_NAME));

    assertThat(latch.await(30, TimeUnit.SECONDS))
        .as("channel subscription was not received")
        .isTrue();

    Long result = publisher.publish(CHANNEL_NAME, "hello");
    assertThat(result).isEqualTo(2);

    mockSubscriber1.unsubscribe(CHANNEL_NAME);
    mockSubscriber2.unsubscribe(CHANNEL_NAME);

    GeodeAwaitility.await().untilAsserted(subscriber1Future::get);
    GeodeAwaitility.await().untilAsserted(subscriber2Future::get);
  }

  @Test
  public void testConcurrentPubSub() throws Exception {
    int CLIENT_COUNT = 10;
    int ITERATIONS = 1000;

    Jedis subscriber1 = new Jedis("localhost", ports[0]);
    Jedis subscriber2 = new Jedis("localhost", ports[1]);

    CountDownLatch latch = new CountDownLatch(2);
    MockSubscriber mockSubscriber1 = new MockSubscriber(latch);
    MockSubscriber mockSubscriber2 = new MockSubscriber(latch);

    Future<Void> subscriber1Future = executor.submit(
        () -> subscriber1.subscribe(mockSubscriber1, CHANNEL_NAME));
    Future<Void> subscriber2Future = executor.submit(
        () -> subscriber2.subscribe(mockSubscriber2, CHANNEL_NAME));

    assertThat(latch.await(30, TimeUnit.SECONDS))
        .as("channel subscription was not received")
        .isTrue();

    List<Future<Void>> futures = new LinkedList<>();
    for (int i = 0; i < CLIENT_COUNT; i++) {
      Jedis client = new Jedis("localhost", ports[i % 2]);

      Callable<Void> callable = () -> {
        for (int j = 0; j < ITERATIONS; j++) {
          client.publish(CHANNEL_NAME, "hello");
        }
        return null;
      };

      futures.add(executor.submit(callable));
    }

    for (Future<Void> future : futures) {
      GeodeAwaitility.await().untilAsserted(future::get);
    }

    mockSubscriber1.unsubscribe(CHANNEL_NAME);
    mockSubscriber2.unsubscribe(CHANNEL_NAME);

    GeodeAwaitility.await().untilAsserted(subscriber1Future::get);
    GeodeAwaitility.await().untilAsserted(subscriber2Future::get);

    assertThat(mockSubscriber1.getReceivedMessages().size()).isEqualTo(CLIENT_COUNT * ITERATIONS);
    assertThat(mockSubscriber2.getReceivedMessages().size()).isEqualTo(CLIENT_COUNT * ITERATIONS);
  }



}

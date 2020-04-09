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
 */

package org.apache.geode.redis.general;

import static org.apache.geode.distributed.ConfigurationProperties.LOCATORS;
import static org.apache.geode.distributed.ConfigurationProperties.LOG_LEVEL;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.redis.GeodeRedisServer;

public class PersistIntegrationTest {

  public static Jedis jedis;
  public static Jedis jedis2;
  public static int REDIS_CLIENT_TIMEOUT = 10000000;
  private static GeodeRedisServer server;
  private static GemFireCache cache;
  private static int ITERATION_COUNT = 5000;

  @BeforeClass
  public static void setUp() {
    CacheFactory cf = new CacheFactory();
    cf.set(LOG_LEVEL, "error");
    cf.set(MCAST_PORT, "0");
    cf.set(LOCATORS, "");
    cache = cf.create();
    int port = AvailablePortHelper.getRandomAvailableTCPPort();
    server = new GeodeRedisServer("localhost", port);

    server.start();
    jedis = new Jedis("localhost", port, REDIS_CLIENT_TIMEOUT);
    jedis2 = new Jedis("localhost", port, REDIS_CLIENT_TIMEOUT);
  }

  @After
  public void flushAll() {
    jedis.flushAll();
  }

  @AfterClass
  public static void tearDown() {
    jedis.close();
    cache.close();
    server.shutdown();
  }

  @Test
  public void shouldPersistKey_givenKeyWithStringValue() {
    String key = "key";
    String value = "value";
    jedis.set(key, value);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldReturnZero_givenKeyDoesNotExist() {
    assertThat(jedis.persist("key")).isEqualTo(0L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithSetValue() {
    String key = "key";
    String value = "value";

    jedis.sadd(key, value);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithSortedSetValue() {
    String key = "key";
    double score = 2.0;
    String member = "member";

    jedis.zadd(key, score, member);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithHashValue() {
    String key = "key";
    String field = "field";
    String value = "value";

    jedis.hset(key, field, value);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithGeoValue() {
    String key = "sicily";
    double latitude = 13.361389;
    double longitude = 38.115556;
    String member = "Palermo Catina";

    jedis.geoadd(key, latitude, longitude, member);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithHyperLogLogValue() {
    String key = "crawled:127.0.0.2";
    String value = "www.insideTheHouse.com";

    jedis.pfadd(key, value);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithListValue() {
    String key = "list";
    String value = "value";

    jedis.lpush(key, value);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKey_givenKeyWithBitMapValue() {
    String key = "key";
    long offset = 1L;
    String value = "0";

    jedis.setbit(key, offset, value);
    jedis.expire(key, 20);

    assertThat(jedis.persist(key)).isEqualTo(1L);
    assertThat(jedis.ttl(key)).isEqualTo(-1L);
  }

  @Test
  public void shouldPersistKeysConcurrently() throws InterruptedException {
    doABunchOfSetEXsWithBlockingQueue(jedis);

    AtomicLong persistedFromThread1 = new AtomicLong(0);
    AtomicLong persistedFromThread2 = new AtomicLong(0);

    Runnable runnable1 = () -> doABunchOfPersistsWithBlockingQueue(persistedFromThread1, jedis);
    Runnable runnable2 = () -> doABunchOfPersistsWithBlockingQueue(persistedFromThread2, jedis2);

    Thread thread1 = new Thread(runnable1);
    Thread thread2 = new Thread(runnable2);

    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();

    assertThat(persistedFromThread1.get() + persistedFromThread2.get()).isEqualTo(ITERATION_COUNT);
  }

  private void doABunchOfSetEXsWithBlockingQueue(Jedis jedis) {
    for (int i = 0; i < ITERATION_COUNT; i++) {
      SetParams setParams = new SetParams();
      setParams.ex(600);

      jedis.set("key" + i, "value" + i, setParams);
    }
  }

  private void doABunchOfPersistsWithBlockingQueue(AtomicLong atomicLong, Jedis jedis) {
    for (int i = 0; i < ITERATION_COUNT; i++) {
      String key = "key" + i;
      atomicLong.addAndGet(jedis.persist(key));
    }
  }
}

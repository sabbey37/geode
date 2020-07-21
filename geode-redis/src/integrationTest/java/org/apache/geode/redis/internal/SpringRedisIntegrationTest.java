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
package org.apache.geode.redis.internal;

import java.io.Serializable;
import java.util.ArrayList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.Jedis;

import org.apache.geode.test.junit.categories.RedisTest;

@Category({RedisTest.class})
public class SpringRedisIntegrationTest {
  static RedisTemplate redisTemplate;
  static Jedis jedis;

//  @ClassRule
//  public static GeodeRedisServerRule server = new GeodeRedisServerRule();

  @BeforeClass
  public static void setUp() {
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
    configuration.setHostName("localhost");
    configuration.setPort(6379);
    JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(configuration);

//    jedis = new Jedis("localhost", 6379, 10000000);

    redisTemplate = new RedisTemplate();
    redisTemplate.setConnectionFactory(jedisConnectionFactory);
  }

  @After
  public void flushAll() {
    redisTemplate.getConnectionFactory().getConnection().flushAll();
  }

  @AfterClass
  public static void tearDown() {
  }

  @Test
  public void testRedisSerializer() {
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.afterPropertiesSet();

    ValueOperations valueOperations = redisTemplate.opsForValue();
  }

  public SpringRedisIntegrationTest() {
    super();
  }

  @Test
  public void abc () {
    new Container(new StringSerializer()).somethingWrapper(14L);


  }

  class Container {
    Serializer something;
    public Container (Serializer serializer){
      something = serializer;
    }
    void somethingWrapper(Object abc) {
      something.doSomething(abc);
    }
  }

  private class Serializer<K extends Object> {
    public void doSomething(K abc) {
      System.out.println(abc);
    }
  }

  private class StringSerializer extends Serializer<String> {
    @Override
    public void doSomething(String abc) {
    }
  }
  private class LongSerializer extends Serializer<Long> {
    @Override
    public void doSomething(Long abc) {
      super.doSomething(abc);
    }
  }
}

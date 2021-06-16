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
package org.apache.geode.modules.session.catalina;

import java.util.Set;

import org.apache.catalina.Session;

import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.modules.session.catalina.internal.DeltaSessionStatistics;

public interface SessionCache<T> {

  void initialize();

  String getDefaultRegionAttributesId();

  boolean getDefaultEnableLocalCache();

  String getOperatingRegionName();

  void putSession(Session session);

  T getSession(String sessionId);

  void destroySession(String sessionId);

  void touchSessions(Set<String> sessionIds);

  DeltaSessionStatistics getStatistics();

  GemFireCache getCache();

  Region<String, T> getSessionRegion();

  Region<String, T> getOperatingRegion();

  boolean isPeerToPeer();

  boolean isClientServer();

  Set<String> keySet();

  int size();

  boolean isBackingCacheAvailable();
}

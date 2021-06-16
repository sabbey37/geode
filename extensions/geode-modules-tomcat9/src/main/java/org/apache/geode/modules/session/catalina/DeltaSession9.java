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

import javax.servlet.http.HttpSession;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSessionFacade;


@SuppressWarnings("serial")
public class DeltaSession9 extends DeltaSession {

  /**
   * Construct a new <code>Session</code> associated with no <code>Manager</code>. The
   * <code>Manager</code> will be assigned later using {@link #setOwner(Object)}.
   */
  @SuppressWarnings("unused")
  public DeltaSession9() {
    super();
  }

  /**
   * Construct a new Session associated with the specified Manager.
   *
   * @param manager The manager with which this Session is associated
   */
  DeltaSession9(Manager manager) {
    super(manager);
  }

  public HttpSession getHttpSession(DeltaSession session) {
    return (HttpSession) session;
  }

  /**
   * Return the <code>HttpSession</code> for which this object is the facade.
   */
  @Override
  public HttpSession getSession() {
    if (facade == null) {
      if (isPackageProtectionEnabled()) {
        final DeltaSession fsession = this;
        facade = getNewFacade(fsession);
      } else {
        facade = new StandardSessionFacade(this);
      }
    }
    return facade;
  }
}

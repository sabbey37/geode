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
package org.apache.geode.internal.cache.tier.sockets;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.geode.DataSerializer;
import org.apache.geode.internal.cache.EnumListenerEvent;
import org.apache.geode.internal.cache.EventID;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.tier.MessageType;
import org.apache.geode.internal.cache.versions.VersionSource;
import org.apache.geode.internal.serialization.DeserializationContext;
import org.apache.geode.internal.serialization.SerializationContext;

public class ClientTombstoneMessage extends ClientUpdateMessageImpl {

  enum TOperation {
    GC, GC_PR
  }

  private Object removalInformation;
  private TOperation op;

  /** GC operation for non-partitioned regions */
  public static ClientTombstoneMessage gc(LocalRegion region,
      Map<VersionSource<?>, Long> regionGCVersions, EventID eventId) {
    return new ClientTombstoneMessage(TOperation.GC, region, regionGCVersions, eventId);
  }

  /** GC operation for partitioned regions */
  public static ClientTombstoneMessage gc(LocalRegion region, Set<Object> removedKeys,
      EventID eventId) {
    return new ClientTombstoneMessage(TOperation.GC_PR, region, removedKeys, eventId);
  }

  private ClientTombstoneMessage(TOperation op, LocalRegion region, Object removalInformation,
      EventID eventId) {
    super(EnumListenerEvent.AFTER_TOMBSTONE_EXPIRATION, null, eventId);
    this.op = op;
    this.removalInformation = removalInformation;
    setRegionName(region.getFullPath()); // fix for bug #45962 - tombstone message must have the
                                         // region name
  }

  /**
   * default constructor
   */
  public ClientTombstoneMessage() {}

  @Override
  public boolean shouldBeConflated() {
    return false;
  }

  /*
   * Returns a <code>Message</code> generated from the fields of this
   * <code>ClientTombstoneMessage</code>.
   */
  @Override
  protected Message getMessage(CacheClientProxy proxy, byte[] latestValue) throws IOException {
    // The format:
    // part 0: operation (gc=0)
    // part 1: region name
    // part 2: operation ordinal
    // part 3: regionGCVersions
    // Last part: event ID
    final Message message = new Message(4, proxy.getVersion());
    // Set message type
    message.setMessageType(MessageType.TOMBSTONE_OPERATION);
    message.addStringPart(getRegionName(), true);
    message.addIntPart(op.ordinal());
    message.addObjPart(removalInformation);
    message.addObjPart(getEventId());
    return message;
  }

  @Override
  public int getDSFID() {
    return CLIENT_TOMBSTONE_MESSAGE;
  }

  @Override
  public void toData(DataOutput out,
      SerializationContext context) throws IOException {

    out.writeByte(op.ordinal());
    out.writeByte(_operation.getEventCode());
    DataSerializer.writeString(getRegionName(), out);
    DataSerializer.writeObject(removalInformation, out);
    DataSerializer.writeObject(_membershipId, out);
    DataSerializer.writeObject(_eventIdentifier, out);
  }

  @Override
  public void fromData(DataInput in,
      DeserializationContext context) throws IOException, ClassNotFoundException {
    // note: does not call super.fromData() since there are no keys, etc.
    // The message class hierarchy should be revised to have a more abstract
    // top-level class.
    op = TOperation.values()[in.readByte()];
    _operation = EnumListenerEvent.getEnumListenerEvent(in.readByte());
    setRegionName(DataSerializer.readString(in));
    removalInformation = DataSerializer.readObject(in);
    _membershipId = ClientProxyMembershipID.readCanonicalized(in);
    _eventIdentifier = DataSerializer.readObject(in);
  }

  @Override
  public Object getKeyToConflate() {
    return null;
  }

  @Override
  public String getRegionToConflate() {
    return null;
  }

  @Override
  public Object getValueToConflate() {
    return null;
  }

  @Override
  public void setLatestValue(Object value) {}

  @Override
  public boolean isClientInterested(ClientProxyMembershipID clientId) {
    return true;
  }

  @Override
  public boolean needsNoAuthorizationCheck() {
    return true;
  }

  @Override
  public String toString() {
    return "ClientTombstoneMessage[op=" + op + ";region="
        + getRegionName() + ";removalInfo=" + removalInformation
        + ";memberId=" + getMembershipId() + ";eventId=" + getEventId()
        + "]";
  }
}

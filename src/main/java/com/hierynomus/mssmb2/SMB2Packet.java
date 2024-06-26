/*
 * Copyright (C)2016 - SMBJ Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hierynomus.mssmb2;

import static com.hierynomus.protocol.commons.EnumWithValue.EnumUtils.isSet;

import com.hierynomus.mserref.NtStatus;
import com.hierynomus.protocol.commons.buffer.Buffer;
import com.hierynomus.smb.SMBBuffer;
import com.hierynomus.smb.SMBPacket;

public class SMB2Packet extends SMBPacket<SMB2PacketData, SMB2PacketHeader> {

    public static final int SINGLE_CREDIT_PAYLOAD_SIZE = 64 * 1024;
    protected int structureSize;
    private SMB2Error error;

    public SMB2Packet() {
        super(new SMB2PacketHeader());
    }

    protected SMB2Packet(int structureSize, SMB2Dialect dialect, SMB2MessageCommandCode messageType) {
        this(structureSize, dialect, messageType, 0, 0);
    }

    protected SMB2Packet(int structureSize, SMB2Dialect dialect, SMB2MessageCommandCode messageType, long sessionId) {
        this(structureSize, dialect, messageType, sessionId, 0);
    }

    protected SMB2Packet(int structureSize, SMB2Dialect dialect, SMB2MessageCommandCode messageType, long sessionId, long treeId) {
        super(new SMB2PacketHeader());
        this.structureSize = structureSize;
        header.setDialect(dialect);
        header.setMessageType(messageType);
        header.setSessionId(sessionId);
        header.setTreeId(treeId);
    }

    public long getSequenceNumber() {
        return header.getMessageId();
    }

    public int getStructureSize() {
        return structureSize;
    }

    /**
     * The buffer from which this packet is read if it was a received packet
     *
     * @return The buffer
     */
    public SMBBuffer getBuffer() {
        return buffer;
    }


    public void write(SMBBuffer buffer) {
        this.buffer = buffer; // Keep track of the buffer
        header.writeTo(buffer);
        writeTo(buffer);
        header.setMessageEndPosition(buffer.wpos());
    }

    /**
     * Write the message fields into the buffer, as specified in the [MS-SMB2] specification.
     *
     * @param buffer
     */
    protected void writeTo(SMBBuffer buffer) {
        throw new UnsupportedOperationException("Should be implemented by specific message type");
    }

    protected final void read(SMB2PacketData packetData) throws Buffer.BufferException {
        this.buffer = packetData.getDataBuffer(); // remember the buffer we read it from
        this.header = packetData.getHeader();
        readMessage(buffer);
        buffer.rpos(this.header.getMessageEndPosition());
    }

    final void readError(SMB2PacketData packetData) throws Buffer.BufferException {
        this.buffer = packetData.getDataBuffer(); // remember the buffer we read it from
        this.header = packetData.getHeader();
        this.error = new SMB2Error().read(header, buffer);
        buffer.rpos(this.header.getMessageEndPosition());
    }

    /**
     * Read the packet body, this should be implemented by the various packet types.
     *
     * @param buffer
     * @throws Buffer.BufferException
     */
    protected void readMessage(SMBBuffer buffer) throws Buffer.BufferException {
        throw new UnsupportedOperationException("Should be implemented by specific message type");
    }

    /**
     * Whether this packet contains a success response or an error response
     * @return {@code true} if the packet does not contain {@link SMB2Error error} data
     */
    public final boolean isSuccess() {
        return this.error == null;
    }

    /**
     * Check whether this packet is an intermediate ASYNC response
     */
    public boolean isIntermediateAsyncResponse() {
        return isSet(header.getFlags(), SMB2MessageFlag.SMB2_FLAGS_ASYNC_COMMAND) && header.getStatusCode() == NtStatus.STATUS_PENDING.getValue();
    }

    /**
     * Returns the maximum payload size of this packet. Normally this is the {@link #SINGLE_CREDIT_PAYLOAD_SIZE}.
     * Can be overridden in subclasses to support multi-credit messages.
     *
     * @return
     */
    public int getMaxPayloadSize() {
        return SINGLE_CREDIT_PAYLOAD_SIZE;
    }

    public int getCreditsAssigned() {
        return getHeader().getCreditCharge();
    }

    public void setCreditsAssigned(int creditsAssigned) {
        getHeader().setCreditCharge(creditsAssigned);
    }

    public SMB2Error getError() {
        return error;
    }

    public void setError(SMB2Error error) {
        this.error = error;
    }

    /**
     * Method that can be overridden by Packet Wrappers to ensure that the original (typed) packet is obtainable.
     *
     * @return this
     */
    public SMB2Packet getPacket() {
        return this;
    }

    @Override
    public String toString() {
        return header.getMessage() + " with message id << " + header.getMessageId() + " >>";
    }
}

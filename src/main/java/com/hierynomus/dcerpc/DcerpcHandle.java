/* jcifs msrpc client library in Java
 * Copyright (C) 2006  "Michael B. Allen" <jcifs at samba dot org>
 *                   "Eric Glass" <jcifs at samba dot org>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.hierynomus.dcerpc;

import java.io.IOException;

import jcifs.dcerpc.DcerpcConstants;
import jcifs.dcerpc.DcerpcException;
import jcifs.dcerpc.ndr.NdrBuffer;

public abstract class DcerpcHandle implements DcerpcConstants {

    protected DcerpcBinding binding;
    protected int max_xmit = 4280;
    protected int max_recv = max_xmit;
    protected int state = 0;
    private static int call_id = 1;

    public synchronized void bind() throws DcerpcException, IOException {
        try {
            state = 1;

            DcerpcMessage bindMsg = new DcerpcBind(binding, this);
            byte[] stub = new byte[max_xmit];
            NdrBuffer buf = new NdrBuffer(stub, 0);
            bindMsg.flags = DcerpcConstants.DCERPC_FIRST_FRAG | DcerpcConstants.DCERPC_LAST_FRAG;
            bindMsg.call_id = call_id++;
            bindMsg.encode(buf);
            bindMsg.alloc_hint = buf.getLength();
            doRpcSend(stub, 0, buf.getLength());

            byte[] readBuf = doRpcRecv(null);
            buf = new NdrBuffer(readBuf, 0);
            bindMsg.decode(buf);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            state = 0;
            throw ioe;
        }
    }

    public void sendrecv(DcerpcMessage msg) throws DcerpcException, IOException {
        byte[] stub;
        NdrBuffer buf;
        DcerpcException de;

        if (state == 0) {
            bind();
        }

        stub = new byte[max_xmit];
        try {
            buf = new NdrBuffer(stub, 0);

            msg.flags = DcerpcConstants.DCERPC_FIRST_FRAG | DcerpcConstants.DCERPC_LAST_FRAG;
            msg.call_id = call_id++;
            msg.encode(buf);
            byte[] out = new byte[buf.getLength()];
            System.arraycopy(stub, 0, out, 0, buf.getLength());
            stub = doRpc(out);
            buf = new NdrBuffer(stub, 0);
            msg.decode(buf);
        } finally {
        }

        if ((de = msg.getResult()) != null)
            throw de;
    }

    public String toString() {
        return binding.toString();
    }

    protected abstract byte[] doRpc(byte[] buf) throws IOException;

    protected abstract void doRpcSend(byte[] buf, int offset, int length) throws IOException;

    protected abstract byte[] doRpcRecv(byte[] buf) throws IOException;

    public abstract void close() throws IOException;
}

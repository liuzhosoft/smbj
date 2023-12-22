/* jcifs msrpc client library in Java
 * Copyright (C) 2006  "Michael B. Allen" <jcifs at samba dot org>
 *                     "Eric Glass" <jcifs at samba dot org>
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

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2FileId;
import com.hierynomus.mssmb2.SMB2ImpersonationLevel;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.NamedPipe;
import com.hierynomus.smbj.share.PipeShare;

import java.io.IOException;
import java.util.EnumSet;

import jcifs.dcerpc.DcerpcException;

public class DcerpcPipeHandle extends DcerpcHandle {

    NamedPipe pipe;
    SMB2FileId pipeId;

    public DcerpcPipeHandle(String server, Session session)
            throws DcerpcException, TransportException, SMBApiException {
        binding = new DcerpcBinding("ncacn_np", server);
        binding.setOption("endpoint", "\\PIPE\\srvsvc");
        PipeShare pipeShare = (PipeShare) session.connectShare("IPC$");

        EnumSet<AccessMask> accessMask = EnumSet.of(AccessMask.SYNCHRONIZE, AccessMask.READ_CONTROL, AccessMask.FILE_WRITE_ATTRIBUTES, AccessMask.FILE_READ_ATTRIBUTES,
                AccessMask.FILE_WRITE_EA, AccessMask.FILE_READ_EA, AccessMask.FILE_APPEND_DATA, AccessMask.FILE_WRITE_DATA, AccessMask.FILE_READ_DATA);
        SMB2CreateDisposition createDisposition = SMB2CreateDisposition.FILE_OPEN;

        pipe = pipeShare.open("srvsvc",
                SMB2ImpersonationLevel.Impersonation,
                accessMask,
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_DELETE, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_READ),
                createDisposition,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_OPEN_NO_RECALL));
        pipeId = pipe.getFileId();
    }

    protected byte[] doRpc(byte[] inputData) throws IOException {
        return pipe.transact(inputData);
    }

    public void close() throws IOException {
        state = 0;
        pipe.closeSilently();
    }

    @Override
    protected void doRpcSend(byte[] buf, int offset, int length) throws IOException {
        pipe.write(buf, offset, length);
    }

    @Override
    protected byte[] doRpcRecv(byte[] buf) throws IOException {
        return pipe.read();
    }
}

package com.liuzho.file.explorer.module.smbj;

import com.hierynomus.dcerpc.DcerpcPipeHandle;
import com.hierynomus.dcerpc.MsrpcShareEnum;
import com.hierynomus.smbj.session.Session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.FileEntry;
import jcifs.smb.SmbFile;

public class SmbjHelper {

    public static List<SmbShare> listShare(Session session, String host) throws IOException {
        MsrpcShareEnum msrpcShareEnum = new MsrpcShareEnum(host);
        DcerpcPipeHandle dcerpcPipeHandle = new DcerpcPipeHandle(host, session);
        dcerpcPipeHandle.sendrecv(msrpcShareEnum);
        if (msrpcShareEnum.retval != 0) {
            throw new IOException("rpc ret != 0");
        }
        FileEntry[] entries = msrpcShareEnum.getEntries();
        List<SmbShare> result = new ArrayList<>();
        for (FileEntry entry : entries) {
            if (entry.getType() == SmbFile.TYPE_SHARE) {
                result.add(new SmbShare(entry.lastModified(), entry.length(), entry.getName()));
            }
        }

        return result;
    }
}

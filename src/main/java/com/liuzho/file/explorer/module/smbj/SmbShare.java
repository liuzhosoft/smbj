package com.liuzho.file.explorer.module.smbj;

public class SmbShare {
    public final long lastModified;
    public final long length;
    public final String name;

    SmbShare(long lastModified, long length, String name) {
        this.lastModified = lastModified;
        this.length = length;
        this.name = name;
    }
}

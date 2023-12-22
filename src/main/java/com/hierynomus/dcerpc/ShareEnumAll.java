package com.hierynomus.dcerpc;

import jcifs.dcerpc.msrpc.srvsvc;
import jcifs.dcerpc.ndr.NdrBuffer;
import jcifs.dcerpc.ndr.NdrException;
import jcifs.dcerpc.ndr.NdrObject;

public class ShareEnumAll extends DcerpcMessage {

    public int getOpnum() {
        return 0x0f;
    }

    public int retval;
    public String servername;
    public int level;
    public NdrObject info;
    public int prefmaxlen;
    public int totalentries;
    public int resume_handle;

    public ShareEnumAll(String servername,
                        int level,
                        NdrObject info,
                        int prefmaxlen,
                        int totalentries,
                        int resume_handle) {
        this.servername = servername;
        this.level = level;
        this.info = info;
        this.prefmaxlen = prefmaxlen;
        this.totalentries = totalentries;
        this.resume_handle = resume_handle;
    }

    public void encode_in(NdrBuffer _dst) throws NdrException {
        _dst.enc_ndr_referent(servername, 1);
        if (servername != null) {
            _dst.enc_ndr_string(servername);

        }
        _dst.enc_ndr_long(level);
        int _descr = level;
        _dst.enc_ndr_long(_descr);
        _dst.enc_ndr_referent(info, 1);
        if (info != null) {
            _dst = _dst.deferred;
            info.encode(_dst);

        }
        _dst.enc_ndr_long(prefmaxlen);
        _dst.enc_ndr_long(resume_handle);
    }

    public void decode_out(NdrBuffer _src) throws NdrException {
        level = (int) _src.dec_ndr_long();
        _src.dec_ndr_long(); /* union discriminant */
        int _infop = _src.dec_ndr_long();
        if (_infop != 0) {
            if (info == null) { /* YOYOYO */
                info = new srvsvc.ShareInfoCtr0();
            }
            _src = _src.deferred;
            info.decode(_src);

        }
        totalentries = (int) _src.dec_ndr_long();
        resume_handle = (int) _src.dec_ndr_long();
        retval = (int) _src.dec_ndr_long();
    }
}
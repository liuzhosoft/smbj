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
package com.hierynomus.smbj.auth;

import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_128;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_ALWAYS_SIGN;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_ANONYMOUS;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_KEY_EXCH;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_OEM_DOMAIN_SUPPLIED;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_OEM_WORKSTATION_SUPPLIED;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_SEAL;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_SIGN;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_TARGET_INFO;
import static com.hierynomus.ntlm.messages.NtlmNegotiateFlag.NTLMSSP_REQUEST_TARGET;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hierynomus.asn1.types.primitive.ASN1ObjectIdentifier;
import com.hierynomus.msdtyp.FileTime;
import com.hierynomus.ntlm.NtlmConfig;
import com.hierynomus.ntlm.NtlmException;
import com.hierynomus.ntlm.av.AvId;
import com.hierynomus.ntlm.av.AvPairFlags;
import com.hierynomus.ntlm.av.AvPairString;
import com.hierynomus.ntlm.av.AvPairTimestamp;
import com.hierynomus.ntlm.functions.ComputedNtlmV2Response;
import com.hierynomus.ntlm.functions.NtlmFunctions;
import com.hierynomus.ntlm.functions.NtlmV2Functions;
import com.hierynomus.ntlm.messages.NtlmAuthenticate;
import com.hierynomus.ntlm.messages.NtlmChallenge;
import com.hierynomus.ntlm.messages.NtlmNegotiate;
import com.hierynomus.ntlm.messages.NtlmNegotiateFlag;
import com.hierynomus.ntlm.messages.TargetInfo;
import com.hierynomus.protocol.commons.ByteArrayUtils;
import com.hierynomus.protocol.commons.buffer.Buffer;
import com.hierynomus.protocol.commons.buffer.Endian;
import com.hierynomus.security.SecurityProvider;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.common.SMBRuntimeException;
import com.hierynomus.smbj.connection.ConnectionContext;
import com.hierynomus.spnego.NegTokenInit;
import com.hierynomus.spnego.NegTokenTarg;
import com.hierynomus.spnego.SpnegoException;
import com.hierynomus.spnego.SpnegoToken;
import com.hierynomus.utils.Strings;

public class NtlmAuthenticator implements Authenticator {
    enum State { NEGOTIATE, AUTHENTICATE, COMPLETE; };

    private static final Logger logger = LoggerFactory.getLogger(NtlmAuthenticator.class);

    // The OID for NTLMSSP
    private static final ASN1ObjectIdentifier NTLMSSP = new ASN1ObjectIdentifier("1.3.6.1.4.1.311.2.2.10");
    private SecurityProvider securityProvider;
    private Random random;
    private NtlmV2Functions functions;
    private NtlmConfig config;

    // Context buildup
    private State state;
    private Set<NtlmNegotiateFlag> negotiateFlags;
    private byte[] negotiateMessage;

    public static class Factory implements com.hierynomus.protocol.commons.Factory.Named<Authenticator> {
        @Override
        public String getName() {
            return NTLMSSP.getValue();
        }

        @Override
        public NtlmAuthenticator create() {
            return new NtlmAuthenticator();
        }
    }

    @Override
    public AuthenticateResponse authenticate(final AuthenticationContext context, final byte[] gssToken,
            ConnectionContext connectionContext) throws IOException {
        try {
            if (this.state == State.COMPLETE) {
                return null;
            } else if (this.state == State.NEGOTIATE) {
                logger.debug("Initialized Authentication of {} using NTLM", context.getUsername());
                this.state = State.AUTHENTICATE;
                return doNegotiate(context, gssToken);
            } else {
                logger.debug("Received token: {}", ByteArrayUtils.printHex(gssToken));
                NegTokenTarg negTokenTarg = new NegTokenTarg().read(gssToken);
                NtlmChallenge serverNtlmChallenge = new NtlmChallenge();
                try {
                    serverNtlmChallenge.read(new Buffer.PlainBuffer(negTokenTarg.getResponseToken(), Endian.LE));
                } catch (Buffer.BufferException e) {
                    throw new IOException(e);
                }
                logger.trace("Received NTLM challenge: {}", serverNtlmChallenge);
                logger.debug("Received NTLM challenge from: {}", serverNtlmChallenge.getTargetName());

                // Only keep the negotiate flags that the server indicated it supports
                this.negotiateFlags.removeIf(ntlmNegotiateFlag -> !serverNtlmChallenge.getNegotiateFlags().contains(ntlmNegotiateFlag));

                if (!this.negotiateFlags.contains(NTLMSSP_NEGOTIATE_128)) {
                    throw new NtlmException("Server does not support 128-bit encryption");
                }

                AuthenticateResponse resp = doAuthenticate(context, serverNtlmChallenge, negTokenTarg.getResponseToken());
                this.state = State.COMPLETE;
                return resp;
            }
        } catch (SpnegoException spne) {
            throw new SMBRuntimeException(spne);
        }
    }

    private AuthenticateResponse doNegotiate(AuthenticationContext context, byte[] gssToken) throws SpnegoException {
        AuthenticateResponse response = new AuthenticateResponse();
        this.negotiateFlags = EnumSet.of(NTLMSSP_NEGOTIATE_128, NTLMSSP_REQUEST_TARGET,
                NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY);
        if (!config.isOmitVersion() && config.getWindowsVersion() != null) {
            this.negotiateFlags.add(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_VERSION);
        }

        if (!context.isAnonymous()) {
            this.negotiateFlags.add(NTLMSSP_NEGOTIATE_SIGN);
            this.negotiateFlags.add(NTLMSSP_NEGOTIATE_ALWAYS_SIGN);
            this.negotiateFlags.add(NTLMSSP_NEGOTIATE_KEY_EXCH);
        } else if (context.isGuest()) {
            this.negotiateFlags.add(NTLMSSP_NEGOTIATE_KEY_EXCH);
        } else {
            this.negotiateFlags.add(NTLMSSP_NEGOTIATE_ANONYMOUS);
        }

        if (!this.negotiateFlags.contains(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_VERSION)) {
            if (Strings.isNotBlank(context.getDomain())) {
                this.negotiateFlags.add(NTLMSSP_NEGOTIATE_OEM_DOMAIN_SUPPLIED);
            }

            if (Strings.isNotBlank(config.getWorkstationName())) {
                this.negotiateFlags.add(NTLMSSP_NEGOTIATE_OEM_WORKSTATION_SUPPLIED);
            }
        }

        NtlmNegotiate negotiateMessage = new NtlmNegotiate(negotiateFlags, context.getDomain(), config.getWorkstationName(), config.getWindowsVersion(), config.isOmitVersion());
        logger.trace("Sending NTLM negotiate message: {}", this.negotiateMessage);
        response.setNegToken(negTokenInit(negotiateMessage));
        response.setNegotiateFlags(negotiateFlags);
        return response;
    }

    private AuthenticateResponse doAuthenticate(AuthenticationContext context, NtlmChallenge serverNtlmChallenge, byte[] ntlmChallengeBytes) throws SpnegoException {
        AuthenticateResponse response = new AuthenticateResponse();
        response.setWindowsVersion(serverNtlmChallenge.getVersion());
        if (serverNtlmChallenge.getTargetInfo() != null && serverNtlmChallenge.getTargetInfo().hasAvPair(AvId.MsvAvNbComputerName)) {
            response.setNetBiosName((String) serverNtlmChallenge.getTargetInfo().getAvPair(AvId.MsvAvNbComputerName).getValue());
        }

        // [MS-NLMP] 3.2.2 -- Special case for anonymous authentication
        if (context.isAnonymous()) {
            NtlmAuthenticate msg = new NtlmAuthenticate(null, null, context.getUsername(), context.getDomain(),
                config.getWorkstationName(), null, negotiateFlags, config.getWindowsVersion());
            response.setNegToken(negTokenTarg(msg));
            return response;
        }

        // Ensure we set TARGET_INFO
        negotiateFlags.add(NTLMSSP_NEGOTIATE_TARGET_INFO);
        TargetInfo clientTargetInfo = createClientTargetInfo(serverNtlmChallenge);

        long time = FileTime.now().getWindowsTimeStamp();
        if (clientTargetInfo != null && clientTargetInfo.hasAvPair(AvId.MsvAvTimestamp)) {
            time = ((AvPairTimestamp) clientTargetInfo.getAvPair(AvId.MsvAvTimestamp)).getValue().getWindowsTimeStamp();
        }
        ComputedNtlmV2Response computedNtlmV2Response = functions.computeResponse(context.getUsername(), context.getDomain(), context.getPassword(), serverNtlmChallenge, time, clientTargetInfo);

        byte[] sessionBaseKey = computedNtlmV2Response.getSessionBaseKey();
        byte[] ntResponse = computedNtlmV2Response.getNtResponse();
        byte[] lmResponse = new byte[0]; // computedNtlmV2Response.getLmResponse(); // Z(24)

        byte[] encryptedRandomSessionKey;
        byte[] exportedSessionKey;
        byte[] keyExchangeKey = functions.kxKey(sessionBaseKey, computedNtlmV2Response.getLmResponse(), serverNtlmChallenge.getServerChallenge());
        Set<NtlmNegotiateFlag> serverFlags = serverNtlmChallenge.getNegotiateFlags();
        if (serverFlags.contains(NTLMSSP_NEGOTIATE_KEY_EXCH) && (serverFlags.contains(NTLMSSP_NEGOTIATE_SEAL) || serverFlags.contains(NTLMSSP_NEGOTIATE_SIGN) || serverFlags.contains(NTLMSSP_NEGOTIATE_ALWAYS_SIGN))) {
            exportedSessionKey = new byte[16];
            random.nextBytes(exportedSessionKey);
            encryptedRandomSessionKey = NtlmFunctions.rc4k(securityProvider, keyExchangeKey, exportedSessionKey);
        } else {
            exportedSessionKey = keyExchangeKey;
            encryptedRandomSessionKey = exportedSessionKey; // TODO
        }

        // If NTLM v2 is used, KeyExchangeKey MUST be set to the given 128-bit
        // SessionBaseKey value.

        NtlmAuthenticate msg = new NtlmAuthenticate(lmResponse, ntResponse, context.getUsername(), context.getDomain(), config.getWorkstationName(), encryptedRandomSessionKey, serverFlags, config.getWindowsVersion());
        // MIC (16 bytes) provided if in AvPairType is key MsvAvFlags with value &
        // 0x00000002 is true
        AvPairFlags pair = clientTargetInfo != null ? clientTargetInfo.getAvPair(AvId.MsvAvFlags) : null;
        if (pair != null && (pair.getValue() & 0x00000002) > 0) {
            // Calculate MIC
            msg.setMic(new byte[16]);
            // TODO correct hash should be tested
            Buffer.PlainBuffer micBuffer = new Buffer.PlainBuffer(Endian.LE);
            msg.write(micBuffer); // authentificateMessage

            byte[] mic = NtlmFunctions.hmac_md5(securityProvider, exportedSessionKey, negotiateMessage, ntlmChallengeBytes, micBuffer.getCompactData());
            msg.setMic(mic);
        }
        response.setSessionKey(exportedSessionKey);
        logger.trace("Sending NTLM authenticate message: {}", msg);
        response.setNegToken(negTokenTarg(msg));
        response.setNegotiateFlags(negotiateFlags);
        return response;
    }

    private TargetInfo createClientTargetInfo(NtlmChallenge serverNtlmChallenge) {
        if (serverNtlmChallenge.getTargetInfo() == null) {
            return null;
        }

        TargetInfo clientTargetInfo = serverNtlmChallenge.getTargetInfo().copy();

        // MIC (16 bytes) provided if MsAvTimestamp is present
        if (config.isIntegrityEnabled() && serverNtlmChallenge.getTargetInfo().hasAvPair(AvId.MsvAvTimestamp)) {
            // Set MsAvFlags bit 0x2 to indicate that the client is providing a MIC
            long flags = 0x02L;
            if (clientTargetInfo.hasAvPair(AvId.MsvAvFlags)) {
                flags |= (long) clientTargetInfo.getAvPair(AvId.MsvAvFlags).getValue();
            }

            clientTargetInfo.putAvPair(new AvPairFlags(flags));
        }

        // Should be clientSuppliedeTargetName
        if (serverNtlmChallenge.getNegotiateFlags().contains(NTLMSSP_REQUEST_TARGET)) {
            AvPairString dnsComputerName = clientTargetInfo.getAvPair(AvId.MsvAvDnsComputerName);
            if (dnsComputerName != null) {
                String targetName = String.format("cifs/%s", dnsComputerName.getValue());
                clientTargetInfo.putAvPair(new AvPairString(AvId.MsvAvTargetName, targetName));
            }
        } else {
            clientTargetInfo.putAvPair(new AvPairString(AvId.MsvAvTargetName, ""));
        }

        // TODO MS-NLMP 3.1.5.1.2 page 46
        // clientTargetInfo.putAvPair(new AvPairChannelBindings(new byte[16]));
        // clientTargetInfo.putAvPair(new AvPairSingleHost(new byte[8], config.getMachineID()));

        return clientTargetInfo;
    }

    private SpnegoToken negTokenInit(NtlmNegotiate ntlmNegotiate) throws SpnegoException {
        NegTokenInit negTokenInit = new NegTokenInit();
        negTokenInit.addSupportedMech(NTLMSSP);
        Buffer.PlainBuffer ntlmBuffer = new Buffer.PlainBuffer(Endian.LE);
        ntlmNegotiate.write(ntlmBuffer);
        this.negotiateMessage = ntlmBuffer.getCompactData();
        negTokenInit.setMechToken(this.negotiateMessage);
        return negTokenInit;
    }

    private SpnegoToken negTokenTarg(NtlmAuthenticate resp) throws SpnegoException {
        NegTokenTarg targ = new NegTokenTarg();
        Buffer.PlainBuffer ntlmBuffer = new Buffer.PlainBuffer(Endian.LE);
        resp.write(ntlmBuffer);
        targ.setResponseToken(ntlmBuffer.getCompactData());
        return targ;
    }

    @Override
    public void init(SmbConfig config) {
        this.securityProvider = config.getSecurityProvider();
        this.random = config.getRandomProvider();
        this.config = config.getNtlmConfig();
        this.state = State.NEGOTIATE;
        this.negotiateFlags = new HashSet<>();
        this.functions = new NtlmV2Functions(random, securityProvider);
    }

    @Override
    public boolean supports(AuthenticationContext context) {
        return context.getClass().equals(AuthenticationContext.class);
    }

}

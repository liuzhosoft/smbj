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
package com.hierynomus.security.bc;

import java.util.Objects;

import com.hierynomus.security.AEADBlockCipher;
import com.hierynomus.security.Cipher;
import com.hierynomus.security.DerivationFunction;
import com.hierynomus.security.Mac;
import com.hierynomus.security.MessageDigest;
import com.hierynomus.security.SecurityProvider;
import com.hierynomus.security.mac.HmacT64;

/**
 * Generic BouncyCastle abstraction, in order to use Bouncy Castle directly when available.
 * This prevents the need to use strong cryptography extensions which are needed if BC is used
 * via JCE.
 */
public class BCSecurityProvider implements SecurityProvider {
    @Override
    public MessageDigest getDigest(String name) {
        return new BCMessageDigest(name);
    }

    @Override
    public Mac getMac(String name) {
        if (Objects.equals(name, "HMACT64")) {
            return new HmacT64(getDigest("MD5"));
        }
        return new BCMac(name);
    }

    @Override
    public Cipher getCipher(String name) {
        return BCCipherFactory.create(name);
    }

    @Override
    public AEADBlockCipher getAEADBlockCipher(String name) {
        return BCAEADCipherFactory.create(name);
    }

    @Override
    public DerivationFunction getDerivationFunction(String name) {
        return BCDerivationFunctionFactory.create(name);
    }
}

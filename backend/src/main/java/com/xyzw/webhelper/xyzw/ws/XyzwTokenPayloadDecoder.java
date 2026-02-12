package com.xyzw.webhelper.xyzw.ws;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class XyzwTokenPayloadDecoder {
    private XyzwTokenPayloadDecoder() {
    }

    public static Map<String, Object> decodeFromBase64Token(String base64Token) {
        if (base64Token == null || base64Token.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Token.trim());
        } catch (IllegalArgumentException ex) {
            return Collections.emptyMap();
        }
        return decodeFromTokenBytes(raw);
    }

    public static Map<String, Object> decodeFromTokenBytes(byte[] tokenBytes) {
        if (tokenBytes == null || tokenBytes.length == 0) {
            return Collections.emptyMap();
        }
        try {
            Crypto crypto = new Crypto();
            BonCodec codec = new BonCodec();
            byte[] plain = crypto.decryptAuto(tokenBytes);
            Object packetObj = codec.decode(plain);
            if (!(packetObj instanceof Map)) {
                return Collections.emptyMap();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> packetMap = (Map<String, Object>) packetObj;

            Object bodyObj = packetMap.get("body");
            if (bodyObj instanceof byte[]) {
                Object decodedBody = codec.decode((byte[]) bodyObj);
                if (decodedBody instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bodyMap = (Map<String, Object>) decodedBody;
                    return new LinkedHashMap<String, Object>(bodyMap);
                }
            }
            if (bodyObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bodyMap = (Map<String, Object>) bodyObj;
                return new LinkedHashMap<String, Object>(bodyMap);
            }
            if (packetMap.containsKey("roleToken")) {
                return new LinkedHashMap<String, Object>(packetMap);
            }
            return Collections.emptyMap();
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }
}

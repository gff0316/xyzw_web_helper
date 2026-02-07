package com.xyzw.webhelper.xyzw.ws;


import net.jpountz.lz4.LZ4FrameInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

final class Crypto {
    private static final Logger logger = LoggerFactory.getLogger(Crypto.class);
    private static final int HEADER_P = 112; // 'p'
    private static final int HEADER_L = 108; // 'l'
    private static final int HEADER_X = 120; // 'x'
    private static final int HEADER_T = 116; // 't'

    private final Random random = new Random();

    Crypto() {
    }

    byte[] encryptX(byte[] input) {
        int rnd = random.nextInt();
        byte[] out = new byte[input.length + 4];
        out[0] = (byte) (rnd & 0xff);
        out[1] = (byte) ((rnd >>> 8) & 0xff);
        out[2] = (byte) ((rnd >>> 16) & 0xff);
        out[3] = (byte) ((rnd >>> 24) & 0xff);
        System.arraycopy(input, 0, out, 4, input.length);

        int r = 2 + random.nextInt(248);
        for (int i = out.length - 1; i >= 0; i--) {
            out[i] ^= (byte) r;
        }

        out[0] = (byte) HEADER_P;
        out[1] = (byte) HEADER_X;
        out[2] = (byte) ((out[2] & 0b10101010)
            | (((r >> 7) & 1) << 6)
            | (((r >> 6) & 1) << 4)
            | (((r >> 5) & 1) << 2)
            | ((r >> 4) & 1));
        out[3] = (byte) ((out[3] & 0b10101010)
            | (((r >> 3) & 1) << 6)
            | (((r >> 2) & 1) << 4)
            | (((r >> 1) & 1) << 2)
            | (r & 1));
        return out;
    }

    byte[] decryptAuto(byte[] input) {
        if (input == null || input.length < 4) {
            return input == null ? new byte[0] : input;
        }
        int b0 = input[0] & 0xff;
        int b1 = input[1] & 0xff;
        if (b0 == HEADER_P && b1 == HEADER_L) {
            return decryptLx(input);
        }
        if (b0 == HEADER_P && b1 == HEADER_X) {
            return decryptX(input);
        }
        if (b0 == HEADER_P && b1 == HEADER_T) {
            return input;
        }
        return input;
    }

    private byte[] decryptX(byte[] input) {
        int b2 = input[2] & 0xff;
        int b3 = input[3] & 0xff;
        int t =
            (((b2 >> 6) & 1) << 7)
                | (((b2 >> 4) & 1) << 6)
                | (((b2 >> 2) & 1) << 5)
                | ((b2 & 1) << 4)
                | (((b3 >> 6) & 1) << 3)
                | (((b3 >> 4) & 1) << 2)
                | (((b3 >> 2) & 1) << 1)
                | (b3 & 1);
        byte[] out = input.clone();
        for (int i = out.length - 1; i >= 4; i--) {
            out[i] ^= (byte) t;
        }
        byte[] trimmed = new byte[out.length - 4];
        System.arraycopy(out, 4, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private byte[] decryptLx(byte[] input) {
        int b2 = input[2] & 0xff;
        int b3 = input[3] & 0xff;
        int t =
            (((b2 >> 6) & 1) << 7)
                | (((b2 >> 4) & 1) << 6)
                | (((b2 >> 2) & 1) << 5)
                | ((b2 & 1) << 4)
                | (((b3 >> 6) & 1) << 3)
                | (((b3 >> 4) & 1) << 2)
                | (((b3 >> 2) & 1) << 1)
                | (b3 & 1);
        byte[] out = input.clone();
        int limit = Math.min(100, out.length);
        for (int i = limit - 1; i >= 2; i--) {
            out[i] ^= (byte) t;
        }
        out[0] = 4;
        out[1] = 34;
        out[2] = 77;
        out[3] = 24;

        try (LZ4FrameInputStream lz4 = new LZ4FrameInputStream(new ByteArrayInputStream(out));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = lz4.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            logger.warn("LZ4 解密失败，返回空数据", ex);
            return new byte[0];
        }
    }
}

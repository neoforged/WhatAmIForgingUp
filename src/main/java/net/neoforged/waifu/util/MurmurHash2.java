package net.neoforged.waifu.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MurmurHash2 {

    public static byte[] normalizeByteArray(byte[] data) {
        List<Byte> newArray = new ArrayList<>();
        for (byte b : data) {
            if (!isWhitespaceCharacter(b)) {
                newArray.add(b);
            }
        }
        byte[] result = new byte[newArray.size()];
        for (int i = 0; i < newArray.size(); i++) {
            result[i] = newArray.get(i);
        }
        return result;
    }

    private static boolean isWhitespaceCharacter(byte b) {
        return b == 9 || b == 10 || b == 13 || b == 32;
    }

    public static long hash(String data) {
        return hash(data.getBytes(StandardCharsets.UTF_8));
    }

    public static long hash(byte[] data) {
        return hash(data, 1);
    }

    private static final int m = 0x5bd1e995;
    private static final int r = 24;

    public static long hash(byte[] data, int seed) {
        int length = data.length;
        if (length == 0) {
            return 0;
        }

        int h = seed ^ length;
        int currentIndex = 0;

        while (length >= 4) {
            int k = (data[currentIndex++] & 0xFF) |
                    ((data[currentIndex++] & 0xFF) << 8) |
                    ((data[currentIndex++] & 0xFF) << 16) |
                    ((data[currentIndex++] & 0xFF) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
            length -= 4;
        }

        switch (length) {
            case 3:
                h ^= (data[currentIndex++] & 0xFF) |
                        ((data[currentIndex++] & 0xFF) << 8);
                h ^= (data[currentIndex] & 0xFF) << 16;
                h *= m;
                break;
            case 2:
                h ^= (data[currentIndex++] & 0xFF) |
                        ((data[currentIndex] & 0xFF) << 8);
                h *= m;
                break;
            case 1:
                h ^= data[currentIndex] & 0xFF;
                h *= m;
                break;
            default:
                break;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h & 0xFFFFFFFFL;
    }
}

package mapwriter.region;

import lombok.val;

import net.minecraft.world.chunk.NibbleArray;

import java.util.Arrays;

public class Util {
    public static int fetchSafeNibble(byte[][] storage, int subchunk, int offset) {
        int packed = fetchSafe(storage, subchunk, offset >> 1);
        int selector = offset & 1;
        return (packed >> (selector * 4)) & 0xF;
    }

    public static int fetchSafe(byte[][] storage, int subchunk, int offset) {
        if (storage == null)
            return 0;
        val arr = storage[subchunk];
        if (arr == null)
            return 0;
        if (arr.length > offset)
            return arr[offset];
        return 0;
    }

    public static byte[] nullSafeCopy(byte[] arr) {
        if (arr == null)
            return null;
        return Arrays.copyOf(arr, arr.length);
    }

    public static short[] nullSafeCopy(short[] arr) {
        if (arr == null)
            return null;
        return Arrays.copyOf(arr, arr.length);
    }

    public static byte[] nullSafeCopy(NibbleArray nib) {
        if (nib == null)
            return null;
        val arr = nib.data;
        return nullSafeCopy(arr);
    }
}

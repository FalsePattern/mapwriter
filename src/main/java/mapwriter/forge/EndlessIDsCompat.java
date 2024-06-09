package mapwriter.forge;

import com.falsepattern.endlessids.constants.ExtendedConstants;

import cpw.mods.fml.common.Loader;

public class EndlessIDsCompat {
    private static boolean biomes;
    private static boolean blocks;
    public static void init() {
        biomes = Loader.isModLoaded("endlessids_biome");
        blocks = Loader.isModLoaded("endlessids_blockitem");
    }

    public static boolean biomes() {
        return biomes;
    }

    public static boolean blocks() {
        return blocks;
    }

    public static int blockCount() {
        return ExtendedConstants.blockIDCount;
    }

    public static int metaCount() {
        return (1 << ExtendedConstants.bitsPerMetadata);
    }

    public static int biomeCount() {
        return ExtendedConstants.biomeIDCount;
    }
}

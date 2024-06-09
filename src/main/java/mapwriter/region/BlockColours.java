package mapwriter.region;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import lombok.val;
import lombok.var;
import mapwriter.forge.EndlessIDsCompat;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

public class BlockColours {

    public static int blockCount() {
        return EndlessIDsCompat.blocks() ? EndlessIDsCompat.blockCount() : 4096;
    }

    public static int metaCount() {
        return EndlessIDsCompat.blocks() ? EndlessIDsCompat.metaCount() : 16;
    }

    public static int biomeCount() {
        return EndlessIDsCompat.biomes() ? EndlessIDsCompat.biomeCount() : 256;
    }
    private final TLongIntMap bcArray = new TLongIntHashMap(1024, 0.5F, 0, 0);
    private final int[] waterMultiplierArray = new int[biomeCount()];
    private final int[] grassMultiplierArray = new int[biomeCount()];
    private final int[] foliageMultiplierArray = new int[biomeCount()];

    public enum BlockType {
        NORMAL,
        GRASS,
        LEAVES,
        FOLIAGE,
        WATER,
        OPAQUE
    }

    private final TLongObjectMap<BlockType> blockTypeArray = new TLongObjectHashMap<>(1024, 0.5F, 0);

    public BlockColours() {
        Arrays.fill(this.waterMultiplierArray, 0xffffff);
        Arrays.fill(this.grassMultiplierArray, 0xffffff);
        Arrays.fill(this.foliageMultiplierArray, 0xffffff);
    }

    public int getColour(long blockAndMeta) {
        return bcArray.get(blockAndMeta);
    }

    public void setColour(long blockAndMeta, int colour) {
        bcArray.put(blockAndMeta, colour);
    }

    public int getColour(int blockID, int meta) {
        return getColour((blockID & 0xFFFFFFFFL) | ((meta & 0xFFFFFFFFL) << 32));
    }

    public void setColour(int blockID, int meta, int colour) {
        setColour((blockID & 0xFFFFFFFFL) | ((meta & 0xFFFFFFFFL) << 32), colour);
    }

    private int getGrassColourMultiplier(int biome) {
        return biome >= 0 && biome < this.grassMultiplierArray.length ?
                this.grassMultiplierArray[biome] : 0xffffff;
    }

    private int getWaterColourMultiplier(int biome) {
        return biome >= 0 && biome < this.waterMultiplierArray.length ?
                this.waterMultiplierArray[biome] : 0xffffff;
    }

    private int getFoliageColourMultiplier(int biome) {
        return biome >= 0 && biome < this.foliageMultiplierArray.length ?
                this.foliageMultiplierArray[biome] : 0xffffff;
    }

    public int getBiomeColour(long blockAndMeta, int biome) {

        var type = blockTypeArray.get(blockAndMeta);
        if (type == null)
            type = BlockType.NORMAL;

        int colourMultiplier;
        switch (type) {
            case GRASS:
                colourMultiplier = getGrassColourMultiplier(biome);
                break;
            case LEAVES:
            case FOLIAGE:
                colourMultiplier = getFoliageColourMultiplier(biome);
                break;
            case WATER:
                colourMultiplier = getWaterColourMultiplier(biome);
                break;
            default:
                colourMultiplier = 0xffffff;
                break;
        }
        return colourMultiplier;
    }

    public void setBiomeWaterShading(int biomeID, int colour) {
        this.waterMultiplierArray[biomeID & (biomeCount() - 1)] = colour;
    }

    public void setBiomeGrassShading(int biomeID, int colour) {
        this.grassMultiplierArray[biomeID & (biomeCount() - 1)] = colour;
    }

    public void setBiomeFoliageShading(int biomeID, int colour) {
        this.foliageMultiplierArray[biomeID & (biomeCount() - 1)] = colour;
    }

    private static BlockType getBlockTypeFromString(String typeString) {
        BlockType blockType = BlockType.NORMAL;
        if (typeString.equalsIgnoreCase("grass")) {
            blockType = BlockType.GRASS;
        } else if (typeString.equalsIgnoreCase("leaves")) {
            blockType = BlockType.LEAVES;
        } else if (typeString.equalsIgnoreCase("foliage")) {
            blockType = BlockType.FOLIAGE;
        } else if (typeString.equalsIgnoreCase("water")) {
            blockType = BlockType.WATER;
        } else if (typeString.equalsIgnoreCase("opaque")) {
            blockType = BlockType.OPAQUE;
        } else {
            RegionManager.logWarning("unknown block type '%s'", typeString);
        }
        return blockType;
    }

    private static String getBlockTypeAsString(BlockType blockType) {
        String s = "normal";
        switch (blockType) {
            case NORMAL:
                s = "normal";
                break;
            case GRASS:
                s = "grass";
                break;
            case LEAVES:
                s = "leaves";
                break;
            case FOLIAGE:
                s = "foliage";
                break;
            case WATER:
                s = "water";
                break;
            case OPAQUE:
                s = "opaque";
                break;
        }
        return s;
    }

    public BlockType getBlockType(long blockAndMeta) {
        val type = blockTypeArray.get(blockAndMeta);
        return type == null ? BlockType.NORMAL : type;
    }

    public BlockType getBlockType(int blockID, int meta) {
        return getBlockType((blockID & 0xFFFFFFFFL) | ((meta & 0xFFFFFFFFL) << 32));
    }

    public void setBlockType(int blockID, int meta, BlockType type) {
        if (type == null)
            type = BlockType.NORMAL;
        blockTypeArray.put((blockID & 0xFFFFFFFFL) | ((meta & 0xFFFFFFFFL) << 32), type);
    }

    public static int getColourFromString(String s) {
        return (int) (Long.parseLong(s, 16) & 0xffffffffL);
    }

    //
    // Methods for loading block colours from file:
    //

    // read biome colour multiplier values.
    // line format is:
    //   biome <biomeId> <waterMultiplier> <grassMultiplier> <foliageMultiplier>
    // accepts "*" wildcard for biome id (meaning for all biomes).
    private void loadBiomeLine(String[] split) {
        try {
            int startBiomeId = 0;
            int endBiomeId = 256; //hardcoded for legacy compat
            if (!split[1].equals("*")) {
                startBiomeId = Integer.parseInt(split[1]);
                endBiomeId = startBiomeId + 1;
            }

            if ((startBiomeId >= 0) && (startBiomeId < biomeCount())) {
                int waterMultiplier = getColourFromString(split[2]) & 0xffffff;
                int grassMultiplier = getColourFromString(split[3]) & 0xffffff;
                int foliageMultiplier = getColourFromString(split[4]) & 0xffffff;

                for (int biomeId = startBiomeId; biomeId < endBiomeId; biomeId++) {
                    this.setBiomeWaterShading(biomeId, waterMultiplier);
                    this.setBiomeGrassShading(biomeId, grassMultiplier);
                    this.setBiomeFoliageShading(biomeId, foliageMultiplier);
                }
            } else {
                RegionManager.logWarning("biome ID '%d' out of range", startBiomeId);
            }

        } catch (NumberFormatException e) {
            RegionManager.logWarning("invalid biome colour line '%s %s %s %s %s'", split[0], split[1], split[2], split[3], split[4]);
        }
    }

    // read block colour values.
    // line format is:
    //   block <blockId> <blockMeta> <colour>
    // the biome id, meta value, and colour code are in hex.
    // accepts "*" wildcard for biome id and meta (meaning for all blocks and/or meta values).
    private void loadBlockLine(String[] split, boolean isBlockColourLine) {
        try {
            int startBlockId = 0;
            int endBlockId = 4096; //hardcoded for legacy compat
            if (!split[1].equals("*")) {
                startBlockId = Integer.parseInt(split[1]);
                endBlockId = startBlockId + 1;
            }

            int startBlockMeta = 0;
            int endBlockMeta = 16; //hardcoded for legacy compat
            if (!split[2].equals("*")) {
                startBlockMeta = Integer.parseInt(split[2]);
                endBlockMeta = startBlockMeta + 1;
            }

            if ((startBlockId >= 0) && (startBlockId < blockCount()) && (startBlockMeta >= 0) && (startBlockMeta < metaCount())) {
                if (isBlockColourLine) {
                    // block colour line
                    int colour = getColourFromString(split[3]);

                    for (int blockId = startBlockId; blockId < endBlockId; blockId++) {
                        for (int blockMeta = startBlockMeta; blockMeta < endBlockMeta; blockMeta++) {
                            this.setColour(blockId, blockMeta, colour);
                        }
                    }
                } else {
                    // block type line
                    BlockType type = getBlockTypeFromString(split[3]);

                    for (int blockId = startBlockId; blockId < endBlockId; blockId++) {
                        for (int blockMeta = startBlockMeta; blockMeta < endBlockMeta; blockMeta++) {
                            this.setBlockType(blockId, blockMeta, type);
                        }
                    }
                }
            }

        } catch (NumberFormatException e) {
            RegionManager.logWarning("invalid block colour line '%s %s %s %s'", split[0], split[1], split[2], split[3]);
        }
    }

    public void loadFromFile(File f) {
        try (Scanner fin = new Scanner(f)) {
            while (fin.hasNextLine()) {
                // get next line and remove comments (part of line after #)
                String line = fin.nextLine().split("#")[0].trim();
                if (line.length() > 0) {
                    String[] lineSplit = line.split(" ");
                    if (lineSplit[0].equals("biome") && (lineSplit.length == 5)) {
                        this.loadBiomeLine(lineSplit);
                    } else if (lineSplit[0].equals("block") && (lineSplit.length == 4)) {
                        this.loadBlockLine(lineSplit, true);
                    } else if (lineSplit[0].equals("blocktype") && (lineSplit.length == 4)) {
                        this.loadBlockLine(lineSplit, false);
                    } else {
                        RegionManager.logWarning("invalid map colour line '%s'", line);
                    }
                }
            }
        } catch (IOException e) {
            RegionManager.logError("loading block colours: no such file '%s'", f);
        }
    }

    //
    // Methods for saving block colours to file.
    //

    // save biome colour multipliers to a file.
    public void saveBiomes(Writer fout) throws IOException {
        fout.write("biome * ffffff ffffff ffffff\n");

        for (int biomeId = 0; biomeId < biomeCount(); biomeId++) {
            int waterMultiplier = this.getWaterColourMultiplier(biomeId) & 0xffffff;
            int grassMultiplier = this.getGrassColourMultiplier(biomeId) & 0xffffff;
            int foliageMultiplier = this.getFoliageColourMultiplier(biomeId) & 0xffffff;

            // don't add lines that are covered by the default.
            if ((waterMultiplier != 0xffffff) || (grassMultiplier != 0xffffff) || (foliageMultiplier != 0xffffff)) {
                fout.write(String.format("biome %d %06x %06x %06x\n", biomeId, waterMultiplier, grassMultiplier, foliageMultiplier));
            }
        }
    }

    private static String getMostOccurringKey(Map<String, Integer> map, String defaultItem) {
        // find the most commonly occurring key in a hash map.
        // only return a key if there is more than 1.
        int maxCount = 1;
        String mostOccurringKey = defaultItem;
        for (Entry<String, Integer> entry : map.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();

            if (count > maxCount) {
                maxCount = count;
                mostOccurringKey = key;
            }
        }

        return mostOccurringKey;
    }

    // to use the least number of lines possible find the most commonly occurring
    // item for the 16 different meta values of a block.
    // an 'item' is either a block colour or a block type.
    // the most commonly occurring item is then used as the wildcard entry for
    // the block, and all non matching items added afterwards.
    private static void writeMinimalBlockLines(Writer fout, String lineStart, TIntObjectMap<String> items, String defaultItem) throws IOException {
        try {
            items.forEachEntry((i, value) -> {
                if (!value.equals(defaultItem)) {
                    try {
                        fout.write(String.format("%s %d %s\n", lineStart, i, value));
                    } catch (IOException e) {
                        throw new BoxedException(e);
                    }
                }
                return true;
            });
        } catch (BoxedException e) {
            throw e.cause;
        }
    }

    private static class BoxedException extends RuntimeException {
        final IOException cause;
        public BoxedException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }

    public void saveBlocks(Writer fout) throws IOException {
        fout.write("block * * 00000000\n");

        TIntObjectMap<TIntObjectMap<String>> blocks = new TIntObjectHashMap<>();

        bcArray.forEachEntry((packedID, value) -> {
            int blockID = (int) (packedID & 0xFFFFFFFFL);
            TIntObjectMap<String> colours;
            if (blocks.containsKey(blockID))
                colours = blocks.get(blockID);
            else {
                colours = new TIntObjectHashMap<>();
                blocks.put(blockID, colours);
            }
            int meta = (int) ((packedID >> 32) & 0xFFFFFFFFL);
            colours.put(meta, String.format("%08x", value));
            return true;
        });

        try {
            blocks.forEachEntry((blockId, colours) -> {
                // write a minimal representation to the file
                String lineStart = String.format("block %d", blockId);
                try {
                    writeMinimalBlockLines(fout, lineStart, colours, "00000000");
                } catch (IOException e) {
                    throw new BoxedException(e);
                }
                return true;
            });
        } catch (BoxedException e) {
            throw e.cause;
        }
    }

    public void saveBlockTypes(Writer fout) throws IOException {
        TIntObjectMap<TIntObjectMap<String>> blocks = new TIntObjectHashMap<>();

        blockTypeArray.forEachEntry((packedID, value) -> {
            int blockID = (int) (packedID & 0xFFFFFFFFL);
            TIntObjectMap<String> blockTypes;
            if (blocks.containsKey(blockID))
                blockTypes = blocks.get(blockID);
            else {
                blockTypes = new TIntObjectHashMap<>();
                blocks.put(blockID, blockTypes);
            }
            int meta = (int) ((packedID >> 32) & 0xFFFFFFFFL);

            blockTypes.put(meta, getBlockTypeAsString(value));
            return true;
        });


        try {
            blocks.forEachEntry((blockId, blockTypes) -> {
                // write a minimal representation to the file
                String lineStart = String.format("blocktype %d", blockId);
                try {
                    writeMinimalBlockLines(fout, lineStart, blockTypes, "normal");
                } catch (IOException e) {
                    throw new BoxedException(e);
                }
                return true;
            });
        } catch (BoxedException e) {
            throw e.cause;
        }
    }

    // save block colours and biome colour multipliers to a file.
    public void saveToFile(File f) {
        try (Writer fout = Files.newBufferedWriter(f.toPath())) {
            this.saveBiomes(fout);
            this.saveBlockTypes(fout);
            this.saveBlocks(fout);
        } catch (IOException e) {
            RegionManager.logError("saving block colours: could not write to '%s'", f);
        }
    }

    public static void writeOverridesFile(File f) {
        try (Writer fout = Files.newBufferedWriter(f.toPath())) {
            fout.write(
                    "block 37 * 60ffff00      # make dandelions more yellow\n" +
                            "block 38 * 60ff0000      # make roses more red\n" +
                            "blocktype 2 * grass      # grass block\n" +
                            "blocktype 8 * water      # still water block\n" +
                            "blocktype 9 * water      # flowing water block\n" +
                            "blocktype 18 * leaves    # leaves block\n" +
                            "blocktype 18 1 opaque    # pine leaves (not biome colorized)\n" +
                            "blocktype 18 2 opaque    # birch leaves (not biome colorized)\n" +
                            "blocktype 31 * grass     # tall grass block\n" +
                            "blocktype 106 * foliage  # vines block\n" +
                            "blocktype 169 * grass    # biomes o plenty holy grass\n" +
                            "blocktype 1920 * grass   # biomes o plenty plant\n" +
                            "blocktype 1923 * opaque  # biomes o plenty leaves 1\n" +
                            "blocktype 1924 * opaque  # biomes o plenty leaves 2\n" +
                            "blocktype 1925 * foliage # biomes o plenty foliage\n" +
                            "blocktype 1926 * opaque  # biomes o plenty fruit leaf block\n" +
                            "blocktype 1932 * foliage # biomes o plenty tree moss\n" +
                            "blocktype 1962 * leaves  # biomes o plenty colorized leaves\n" +
                            "blocktype 2164 * leaves  # twilight forest leaves\n" +
                            "blocktype 2177 * leaves  # twilight forest magic leaves\n" +
                            "blocktype 2204 * leaves  # extrabiomesXL green leaves\n" +
                            "blocktype 2200 * opaque  # extrabiomesXL autumn leaves\n" +
                            "blocktype 3257 * opaque  # natura berry bush\n" +
                            "blocktype 3272 * opaque  # natura darkwood leaves\n" +
                            "blocktype 3259 * leaves  # natura flora leaves\n" +
                            "blocktype 3278 * opaque  # natura rare leaves\n" +
                            "blocktype 3258 * opaque  # natura sakura leaves\n"
            );
        } catch (IOException e) {
            RegionManager.logError("saving block overrides: could not write to '%s'", f);
        }
    }
}

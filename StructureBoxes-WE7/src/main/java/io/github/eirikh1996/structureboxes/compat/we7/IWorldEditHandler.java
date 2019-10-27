package io.github.eirikh1996.structureboxes.compat.we7;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.PasteBuilder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import io.github.eirikh1996.structureboxes.Direction;
import io.github.eirikh1996.structureboxes.SBMain;
import io.github.eirikh1996.structureboxes.StructureManager;
import io.github.eirikh1996.structureboxes.WorldEditHandler;
import io.github.eirikh1996.structureboxes.utils.CollectionUtils;
import io.github.eirikh1996.structureboxes.utils.Location;
import io.github.eirikh1996.structureboxes.utils.WorldEditLocation;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static java.lang.Math.PI;

public class IWorldEditHandler extends WorldEditHandler {
    private final File weDataFolder;
    private final SBMain sbMain;
    public IWorldEditHandler(File weDataFolder, SBMain sbMain){
        this.weDataFolder = weDataFolder;
        this.sbMain = sbMain;
    }
    @Override
    public Clipboard loadClipboardFromSchematic(World world, String schematicName) {
        File weConfig = new File(weDataFolder, "config.yml");
        Yaml yaml = new Yaml();
        final Map<String, Object> data;
        try {
            data = (Map<String, Object>) yaml.load(new FileInputStream(weConfig));
        } catch (FileNotFoundException e) {
            throw new WorldEditConfigException("Failed to load WorldEdit config", e);
        }
        String schematicDirectory = ((Map<String, String>) data.get("saving")).get("dir");
        String path = weDataFolder.getAbsolutePath() + "/" + schematicDirectory + "/" + schematicName + ".schematic";
        File schematicFile = new File(path);
        if (!schematicFile.exists()){
            path = weDataFolder.getAbsolutePath() + "/" + schematicDirectory + "/" + schematicName + ".schem";
            schematicFile = new File(path);
        }
        if (!schematicFile.exists()){
            return null;
        }
        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        try {
            ClipboardReader reader = format.getReader(new FileInputStream(schematicFile));
            clipboard = reader.read();
        } catch (IOException e) {
            clipboard = null;
            e.printStackTrace();
        }
        return clipboard;
    }

    @Override
    public Direction getClipboardFacingFromOrigin(Clipboard clipboard, Location location) {
        BlockVector3 centerpoint = clipboard.getMinimumPoint().add(clipboard.getDimensions().divide(2));
        BlockVector3 distance = centerpoint.subtract(clipboard.getOrigin());
        if (Math.abs(distance.getBlockX()) > Math.abs(distance.getBlockZ())){
            if (distance.getBlockX() > 0){
                return Direction.EAST;
            } else {
                return Direction.WEST;
            }
        } else {
            if (distance.getBlockZ() > 0){
                return Direction.SOUTH;
            } else {
                return Direction.NORTH;
            }
        }
    }


    @Override
    public boolean pasteClipboard(UUID playerID, String schematicName, Clipboard clipboard, double angle, WorldEditLocation pasteLoc) {
        World world = pasteLoc.getWorld();
        ClipboardHolder holder = new ClipboardHolder(clipboard);
        AffineTransform transform = new AffineTransform();
        transform = transform.rotateY(angle);
        holder.setTransform(transform);
        PasteBuilder builder = holder.createPaste(world);
        builder.ignoreAirBlocks(true);
        BlockVector3 to = BlockVector3.at(pasteLoc.getX(), pasteLoc.getY(), pasteLoc.getZ());
        builder.to(to);
        final ArrayList<Location> structureLocs = new ArrayList<Location>();
        int minX = clipboard.getMinimumPoint().getBlockX();
        int minY = clipboard.getMinimumPoint().getBlockY();
        int minZ = clipboard.getMinimumPoint().getBlockZ();
        int xLength = clipboard.getDimensions().getBlockX();
        int yLength = clipboard.getDimensions().getBlockY();
        int zLength = clipboard.getDimensions().getBlockZ();
        BlockVector3 offset = clipboard.getMinimumPoint().subtract(clipboard.getOrigin());
        Location minPoint = new Location(pasteLoc.getWorldID(), to.add(offset).getBlockX(), to.add(offset).getBlockY(), to.add(offset).getBlockZ());
        final double theta = -(angle * (PI / 180.0));

        for (int x = 0 ; x <= xLength ; x++){
            for (int y = 0 ; y <= yLength ; y++){
                for (int z = 0 ; z <= zLength ; z++){
                    BaseBlock baseBlock = clipboard.getFullBlock(BlockVector3.at(minX + x, minY + y, minZ + z));
                    if (baseBlock.getBlockType().getId().equalsIgnoreCase("minecraft:air") || baseBlock.getBlockType().getId().equalsIgnoreCase("minecraft:cave_air") || baseBlock.getBlockType().getId().equalsIgnoreCase("minecraft:void_air")){
                        continue;
                    }
                    Location loc = minPoint.add(x, y, z);

                    structureLocs.add(loc.rotate(theta, pasteLoc.toSBloc()));
                }
            }
        }
        final ArrayList<Location> exterior = CollectionUtils.exterior(structureLocs);
        final ArrayList<Location> invertedStructure = CollectionUtils.invert(structureLocs);
        final ArrayList<Location> interior = CollectionUtils.filter(invertedStructure, exterior);
        sbMain.getLogger().info(String.valueOf(structureLocs.size()));
        final boolean freeSpace = sbMain.isFreeSpace(playerID, schematicName, structureLocs);
        if (!freeSpace){

            return false;
        }
        StructureManager.getInstance().addStructure(structureLocs);
        try {
            Operation op = builder.build();
            Operations.complete(op);
            sbMain.clearInterior(interior);
        } catch (WorldEditException e) {
            e.printStackTrace();
            return false;
        }
        structurePlayerMap.put(playerID, structureLocs);
        return true;
    }

    @Override
    public int getStructureSize(Clipboard clipboard) {
        int count = 0;
        int minX = clipboard.getMinimumPoint().getBlockX();
        int minY = clipboard.getMinimumPoint().getBlockY();
        int minZ = clipboard.getMinimumPoint().getBlockZ();
        for (int x = 0 ; x <= clipboard.getDimensions().getBlockX(); x++){
            for (int y = 0 ; y <= clipboard.getDimensions().getBlockY(); y++){
                for (int z = 0 ; z <= clipboard.getDimensions().getBlockZ(); z++){
                    BlockVector3 pos = BlockVector3.at(minX + x, minY + y, minZ + z);
                    if (clipboard.getBlock(pos).getBlockType().getId().equalsIgnoreCase("minecraft:air")||clipboard.getBlock(pos).getBlockType().getId().equalsIgnoreCase("minecraft:cave_air")||clipboard.getBlock(pos).getBlockType().getId().equalsIgnoreCase("minecraft:void_air")){
                        continue;
                    }
                    count++;
                }
            }
        }
        return count;
    }



    private static final class WorldEditConfigException extends RuntimeException{
        public WorldEditConfigException(String message, Throwable cause){
            super(message, cause);
        }
    }
}
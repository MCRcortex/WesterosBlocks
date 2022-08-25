package com.westeroscraft.westerosblocks.modelexport;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.westeroscraft.westerosblocks.WesterosBlockDef;
import com.westeroscraft.westerosblocks.WesterosBlocks;

import net.minecraft.world.level.block.Block;

public class FurnaceBlockModelExport extends ModelExport {
    // Template objects for Gson export of block models
    public static class ModelObjectCubeAll {
        public String parent = "minecraft:block/orientable";    // Use 'orientable' model for single texture
        public TextureOrient textures = new TextureOrient();
    }
    public static class TextureOrient {
        public String top;
        public String front;
        public String side;
    }
    public static class ModelObject {
    	public String parent;
    }

    public FurnaceBlockModelExport(Block blk, WesterosBlockDef def, File dest) {
        super(blk, def, dest);
        addNLSString("block." + WesterosBlocks.MOD_ID + "." + def.blockName, def.label);
    }
    
    private static class ModelRec {
    	String cond;
    	int y;
    	String ext;
    	ModelRec(String cond, String ext, int y) {
    		this.cond = cond;
    		this.y = y;
    		this.ext = ext;
    	}
    };
    private static final ModelRec[] MODELS = {
        new ModelRec("facing=north,lit=true", "lit", 0),
        new ModelRec("facing=south,lit=true", "lit", 180),
        new ModelRec("facing=west,lit=true", "lit", 270),
        new ModelRec("facing=east,lit=true", "lit", 90),
        new ModelRec("facing=north,lit=false", "base", 0),
        new ModelRec("facing=south,lit=false", "base", 180),
        new ModelRec("facing=west,lit=false", "base", 270),
        new ModelRec("facing=east,lit=false", "base", 90)
    };
    @Override
    public void doBlockStateExport() throws IOException {
        StateObject so = new StateObject();
        // Add records for our models
        for (ModelRec rec : MODELS) {
        	Variant var = new Variant();
        	var.model = WesterosBlocks.MOD_ID + ":block/generated/" + getModelName(rec.ext, 0);
        	if (rec.y != 0) var.y = rec.y;
        	so.addVariant(rec.cond, var, null);
        }
        this.writeBlockStateFile(def.blockName, so);
    }

    @Override
    public void doModelExports() throws IOException {
        boolean isTinted = def.isTinted();
        ModelObjectCubeAll mod = new ModelObjectCubeAll();
        mod.textures.top = getTextureID(def.getTextureByIndex(1)); 
        mod.textures.side = getTextureID(def.getTextureByIndex(2)); 
        mod.textures.front = getTextureID(def.getTextureByIndex(3)); // ON
        if (isTinted) mod.parent = WesterosBlocks.MOD_ID + ":block/tinted/orientable";
        this.writeBlockModelFile(getModelName("lit", 0), mod);
            
        mod = new ModelObjectCubeAll();
        mod.textures.top = getTextureID(def.getTextureByIndex(1)); 
        mod.textures.side = getTextureID(def.getTextureByIndex(2)); 
        mod.textures.front = getTextureID(def.getTextureByIndex(4)); // Off
        if (isTinted) mod.parent = WesterosBlocks.MOD_ID + ":block/tinted/orientable";
        this.writeBlockModelFile(getModelName("base", 0), mod);
        // Build simple item model that refers to block model
        ModelObject mo = new ModelObject();
        mo.parent = WesterosBlocks.MOD_ID + ":block/generated/" + getModelName("base", 0);
        this.writeItemModelFile(def.blockName, mo);
        // Handle tint resources
        if (isTinted) {
        	String tintres = def.getBlockColorMapResource();
            if (tintres != null) {
                ModelExport.addTintingOverride(def.blockName, "", tintres);
            }
        }
    }
    @Override
    public void doWorldConverterMigrate() throws IOException {
    	String oldID = def.getLegacyBlockName();
    	if (oldID == null) return;
    	String oldVariant = def.getLegacyBlockVariant();
    	addWorldConverterComment(def.legacyBlockID + "(" + def.label + ")");
    	// BUild old variant map
    	Map<String, String> oldstate = new HashMap<String, String>();
    	Map<String, String> newstate = new HashMap<String, String>();
    	oldstate.put("variant", oldVariant);
    	for (String facing : FACING) {
        	oldstate.put("facing", facing);
        	newstate.put("facing", facing);
    		for (String lit : BOOLEAN) {
    	    	oldstate.put("lit", lit);
    	    	newstate.put("lit", lit);
    	        addWorldConverterRecord(oldID, oldstate, def.getBlockName(), newstate);    			
    		}
    	}
    }

}

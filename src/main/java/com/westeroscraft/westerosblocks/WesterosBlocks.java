package com.westeroscraft.westerosblocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.westeroscraft.westerosblocks.commands.PTimeCommand;
import com.westeroscraft.westerosblocks.commands.PWeatherCommand;
import com.westeroscraft.westerosblocks.modelexport.ModelExport;
import com.westeroscraft.westerosblocks.modelexport.ModelExportFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import net.minecraftforge.network.NetworkRegistry;

import com.westeroscraft.westerosblocks.network.ClientMessageHandler;
import com.westeroscraft.westerosblocks.network.PWeatherMessage;
import com.westeroscraft.westerosblocks.network.PTimeMessage;
import com.westeroscraft.westerosblocks.network.ServerMessageHandler;
import net.minecraftforge.network.NetworkDirection;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(WesterosBlocks.MOD_ID)
public class WesterosBlocks {
	public static final String MOD_ID = "westerosblocks";

	// Directly reference a log4j logger.
	public static final Logger log = LogManager.getLogger();

	public static WesterosBlockConfig customConfig;

	// Says where the client and server 'proxy' code is loaded.
	public static Proxy proxy = DistExecutor.safeRunForDist(() -> ClientProxy::new, () -> Proxy::new);

	public static WesterosBlockDef[] customBlockDefs;

	public static HashMap<String, Block> customBlocksByName;

	public static Block[] customBlocks = new Block[0];

	public static Path modConfigPath;
	
	public static WesterosBlockColorMap[] colorMaps;

	public WesterosBlocks() {
		// Register the doClientStuff method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
		// Register the setup method for load complete
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete);
		// Register the doClientStuff method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetupEvent);
		// Register the setup method for tile entities
		WesterosBlockDef.TILE_ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());

		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);

		Path configPath = FMLPaths.CONFIGDIR.get();

		modConfigPath = Paths.get(configPath.toAbsolutePath().toString(), MOD_ID);

		// Create the config folder
		try {
			Files.createDirectory(modConfigPath);
		} catch (FileAlreadyExistsException e) {
			// Do nothing
		} catch (IOException e) {
			log.error("Failed to create westerosblocks config directory", e);
		}

		ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC,
				MOD_ID + "/" + MOD_ID + ".toml");
	}

	private void doClientStuff(final FMLClientSetupEvent event) {
		// do something that can only be done on the client
		log.info("Got game settings {}", event.description());
	}

	@SubscribeEvent
	public void onRegisterCommandEvent(RegisterCommandsEvent event) {
	    CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();
		PTimeCommand.register(commandDispatcher);
		PWeatherCommand.register(commandDispatcher);
	}
	
	private void loadComplete(final FMLLoadCompleteEvent event) // PostRegistrationEven
	{
		// Initialize with standard block IDs
		if (Config.blockDevMode.get()) {
			log.info("Block dev mode enabled : block export processing will be done to " + modConfigPath + "/assets/"
					+ MOD_ID);
		}
		// Do blocks state export here
		if (Config.blockDevMode.get()) {
			// Clean up old export
			deleteDirectory(new File(modConfigPath.toFile(), "assets"));
			deleteDirectory(new File(modConfigPath.toFile(), "data"));
			
			for (int i = 0; i < customBlockDefs.length; i++) {
				if (customBlockDefs[i] == null)
					continue;
				Block blk = customBlocksByName.get(customBlockDefs[i].blockName);
				if (blk != null) {
					ModelExport exp = ModelExportFactory.forBlock(blk, customBlockDefs[i], modConfigPath.toFile());
					if (exp != null) {
						try {
							exp.doBlockStateExport();
							exp.doModelExports();
							exp.doWorldConverterMigrate();
						} catch (IOException iox) {
							log.warn(String.format("Error exporting block %s - %s", blk.getRegistryName(), iox));
						}
					}
				}
			}
			try {
				ModelExport.writeNLSFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing NLS - %s", iox));
			}
			try {
				ModelExport.writeTagDataFiles(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing tag data files - %s", iox));
			}
			try {
				ModelExport.writeWorldConverterFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing WorldConfig mapping - %s", iox));
			}
			try {
				ModelExport.writeDynmapOverridesFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing Dynmap Overrides - %s", iox));
			}
		}
		proxy.initRenderRegistry();

	}

	public static void crash(Exception x, String msg) {
		throw new ReportedException(new CrashReport(msg, x));
	}

	public static void crash(String msg) {
		crash(new Exception(), msg);
	}

	public static class Config {
		public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
		public static final ForgeConfigSpec SPEC;
		public static final ForgeConfigSpec.BooleanValue snowInTaiga;
		public static final ForgeConfigSpec.BooleanValue blockDevMode;
		public static final ForgeConfigSpec.BooleanValue publishToDynmap;

		static {
			BUILDER.comment("Module options");
			snowInTaiga = BUILDER.comment("Enable snow in taiga").define("snowInTaiga", true);
			blockDevMode = BUILDER.comment("Block development mode").define("blockDevMode", false);
			publishToDynmap = BUILDER.comment("Publish blocks to dynmap").define("publishToDynmap", true);

			SPEC = BUILDER.build();
		}
	}

	// You can use EventBusSubscriber to automatically subscribe events on the
	// contained class (this is subscribing to the MOD
	// Event bus for receiving Registry Events)
	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {
		private static boolean didInit = false;
		public static void initialize() {
			if (didInit) return;
			
			// Initialize
			WesterosBlockDef.initialize();
			WesterosBlocksCreativeTab.init();
			// If snow-in-taiga
			if (Config.snowInTaiga.get()) {
				Biome b = ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft:taiga"));
				if (b != null) {
					b.climateSettings.temperature = -0.5F; 
					b.climateSettings.precipitation = Biome.Precipitation.SNOW;
				}
				log.info("Enabled snow in TAIGA");
			}
			// Read our block definition resource
			InputStream in = WesterosBlocks.class.getResourceAsStream("/WesterosBlocks.json");
			if (in == null) {
				crash("WesterosBlocks couldn't find its block definition resource");
				return;
			}
			InputStreamReader rdr = new InputStreamReader(in);
			Gson gson = new Gson();
			try {
				customConfig = gson.fromJson(rdr, WesterosBlockConfig.class);
				customBlockDefs = customConfig.blocks;
			} catch (JsonSyntaxException iox) {
				crash(iox, "WesterosBlocks couldn't parse its block definition");
				return;
			} catch (JsonIOException iox) {
				crash(iox, "WesterosBlocks couldn't read its block definition");
				return;
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException iox) {
					}
					;
					in = null;
				}
				if (rdr != null) {
					try {
						rdr.close();
					} catch (IOException iox) {
					}
					;
					rdr = null;
				}
			}
			log.info("Loaded " + customBlockDefs.length + " block definitions");

			if (WesterosBlockDef.sanityCheck(customBlockDefs) == false) {
				crash("WesterosBlocks.json failed sanity check");
				return;
			}
		}
		@SubscribeEvent
		public static void onBlocksRegistry(final RegistryEvent.Register<Block> event) {
			log.info("onBlocksRegistryEvent");
			IForgeRegistry<Block> registry = event.getRegistry();
			// Do initialization, if needed
			initialize();

			// Construct custom block definitions
			ArrayList<Block> blklist = new ArrayList<Block>();
			customBlocksByName = new HashMap<String, Block>();
			HashMap<String, Integer> countsByType = new HashMap<String, Integer>();
			int blockcount = 0;
			for (int i = 0; i < customBlockDefs.length; i++) {
				if (customBlockDefs[i] == null)
					continue;
				Block blk = customBlockDefs[i].createBlock();
				if (blk != null) {
					blklist.add(blk);
					customBlocksByName.put(customBlockDefs[i].blockName, blk);
					// Register sound events
					customBlockDefs[i].registerSoundEvents();
					// Add to counts
					Integer cnt = countsByType.get(customBlockDefs[i].blockType);
					cnt = (cnt == null) ? 1 : (cnt+1);
					countsByType.put(customBlockDefs[i].blockType, cnt);					
					blockcount++;
				} else {
					// crash("Invalid block definition for " + customBlockDefs[i].blockName + " -
					// aborted during load()");
					// return;
				}
			}
			customBlocks = blklist.toArray(new Block[blklist.size()]);
			// Brag on block type counts
			log.info("Count of custom blocks by type:");
			for (String type : countsByType.keySet()) {
				log.info(type + ": " + countsByType.get(type) + " blocks");				
			}
			log.info("TOTAL: " + blockcount + " blocks");
			colorMaps = customConfig.colorMaps;
		}
		@SubscribeEvent
		public static void onItemsRegistry(final RegistryEvent.Register<Item> event) {
			log.info("onzItemsRegistryEvent");
		}
	}

	public static Block findBlockByName(String blkname) {
		Block blk = customBlocksByName.get(blkname);
		if (blk != null) {
			return blk;
		}
		blk = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blkname));
		return blk;
	}

	private static HashMap<String, SoundEvent> registered_sounds = new HashMap<String, SoundEvent>();

	public static SoundEvent registerSound(String soundName) {
		SoundEvent event = registered_sounds.get(soundName);
		if (event == null) {
			ResourceLocation location = new ResourceLocation(WesterosBlocks.MOD_ID, soundName);
			event = new SoundEvent(location);
			event.setRegistryName(location);
			ForgeRegistries.SOUND_EVENTS.register(event);
			registered_sounds.put(soundName, event);
		}
		return event;
	}

	public static SoundEvent getRegisteredSound(String soundName) {
		return registered_sounds.get(soundName);
	}
	
	// Network setup
	public static SimpleChannel simpleChannel;    // used to transmit your network messages
    public static final String CHANNEL = "wbchannel";
    public static final String MESSAGE_PROTOCOL_VERSION = "5.0";
    public static final ResourceLocation simpleChannelRL = new ResourceLocation("westerosblocks", CHANNEL);

    @SubscribeEvent
    public void onCommonSetupEvent(FMLCommonSetupEvent event) {
	    simpleChannel = NetworkRegistry.newSimpleChannel(simpleChannelRL, () -> MESSAGE_PROTOCOL_VERSION,
	            ClientMessageHandler::isThisProtocolAcceptedByClient,
	            ServerMessageHandler::isThisProtocolAcceptedByServer);

	    simpleChannel.registerMessage(PTimeMessage.PTIME_MSGID, PTimeMessage.class,
	    		PTimeMessage::encode, PTimeMessage::decode,
	            ClientMessageHandler::onPTimeMessageReceived,
	            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	    simpleChannel.registerMessage(PWeatherMessage.PWEATHER_MSGID, PWeatherMessage.class,
	    		PWeatherMessage::encode, PWeatherMessage::decode,
	            ClientMessageHandler::onPWeatherMessageReceived,
	            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	  }
    
    // Directory tree delete
    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}

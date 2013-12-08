package enviromine;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.registry.VillagerRegistry;
import cpw.mods.fml.relauncher.Side;

@Mod(modid = EM_Settings.ID, name = EM_Settings.Name, version = EM_Settings.Version)
@NetworkMod(clientSideRequired = true, serverSideRequired = true, channels = {EM_Settings.Channel}, packetHandler = EnviroPacketHandler.class)

public class EnviroMine
{
	public static Item badWaterBottle;
	public static Item saltWaterBottle;
	public static Item coldWaterBottle;
	
	@Instance("EM_Instance")
    public static EnviroMine instance;
	
	@SidedProxy(clientSide = EM_Settings.Proxy + ".EM_ClientProxy", serverSide = EM_Settings.Proxy + ".EM_CommonProxy")
	public static EM_CommonProxy proxy;
	
	@EventHandler
	public static void preInit(FMLPreInitializationEvent event)
	{
		File configFile = event.getSuggestedConfigurationFile();
		System.out.println("Attempting to load config: "+configFile.getAbsolutePath());
		EM_Settings.LoadConfig(configFile);
		
		badWaterBottle = new EnviroItemBadWaterBottle(EM_Settings.dirtBottleID).setMaxStackSize(1).setUnlocalizedName("dirtyWaterBottle").setCreativeTab(CreativeTabs.tabBrewing);
		saltWaterBottle = new EnviroItemSaltWaterBottle(EM_Settings.saltBottleID).setMaxStackSize(1).setUnlocalizedName("saltWaterBottle").setCreativeTab(CreativeTabs.tabBrewing);
		coldWaterBottle = new EnviroItemColdWaterBottle(EM_Settings.coldBottleID).setMaxStackSize(1).setUnlocalizedName("coldWaterBottle").setCreativeTab(CreativeTabs.tabBrewing);
		
		extendPotionList(event);
		
		VillagerRegistry.instance().registerVillageCreationHandler(new EnviroShaftCreationHandler());
		MapGenStructureIO.func_143031_a(EM_VillageMineshaft.class, "ViMS");
	}
	
	@EventHandler
	public static void init(FMLInitializationEvent event)
	{
		LanguageRegistry.addName(badWaterBottle, "Dirty Water Bottle");
		LanguageRegistry.addName(saltWaterBottle, "Salt Water Bottle");
		LanguageRegistry.addName(coldWaterBottle, "Cold Water Bottle");
		
		EnviroPotion.frostbite = (EnviroPotion)new EnviroPotion(EM_Settings.frostBitePotionID, true, 8171462).setPotionName("potion.frostbite").setIconIndex(0, 0);
		EnviroPotion.dehydration = (EnviroPotion)new EnviroPotion(EM_Settings.dehydratePotionID, true, 3035801).setPotionName("potion.dehydration").setIconIndex(1, 0);
		EnviroPotion.insanity = (EnviroPotion)new EnviroPotion(EM_Settings.insanityPotionID, true, 5578058).setPotionName("potion.insanity").setIconIndex(2, 0);
		
		LanguageRegistry.instance().addStringLocalization("potion.frostbite", "Frostbite");
		LanguageRegistry.instance().addStringLocalization("potion.dehydration", "Dehydration");
		LanguageRegistry.instance().addStringLocalization("potion.insanity", "Insanity");
		
		GameRegistry.addSmelting(badWaterBottle.itemID, new ItemStack(ItemPotion.potion.itemID, 1, 0), 0.0F);
		GameRegistry.addSmelting(saltWaterBottle.itemID, new ItemStack(ItemPotion.potion.itemID, 1, 0), 0.0F);
		GameRegistry.addShapelessRecipe(new ItemStack(coldWaterBottle, 1, 0), new ItemStack(Item.potion, 1, 0), new ItemStack(Item.snowball, 1));
		if(EM_Settings.saddleRecipe)
		{
			GameRegistry.addRecipe(new ItemStack(Item.saddle), "xxx", "x x", "y y", 'x', new ItemStack(Item.leather), 'y', new ItemStack(Item.ingotIron));
		}
		
		System.out.println("Registering Handlers for EnviroMine");
		proxy.registerTickHandlers();
		proxy.registerEventHandlers();
	}

	@EventHandler
	public static void postInit(FMLPostInitializationEvent event)
	{
	}
	
	public static void extendPotionList(FMLPreInitializationEvent event)
	{
		Potion[] potionTypes = null;

		for (Field f : Potion.class.getDeclaredFields())
		{
			f.setAccessible(true);
			
			try
			{
				if (f.getName().equals("potionTypes") || f.getName().equals("field_76425_a"))
				{
					Field modfield = Field.class.getDeclaredField("modifiers");
					modfield.setAccessible(true);
					modfield.setInt(f, f.getModifiers() & ~Modifier.FINAL);

					potionTypes = (Potion[])f.get(null);
					final Potion[] newPotionTypes = new Potion[256];
					System.arraycopy(potionTypes, 0, newPotionTypes, 0, potionTypes.length);
					f.set(null, newPotionTypes);
				}
			}
			catch (Exception e)
			{
				System.err.println("[ERROR] Failed to extend potion list for EnviroMine");
				System.err.println(e);
			}
		}
	}
}

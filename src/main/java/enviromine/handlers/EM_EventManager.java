package enviromine.handlers;

import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.registry.EntityRegistry;
import enviromine.EntityPhysicsBlock;
import enviromine.EnviroPotion;
import enviromine.core.EM_Settings;
import enviromine.core.EnviroMine;
import enviromine.trackers.EntityProperties;
import enviromine.trackers.EnviroDataTracker;
import enviromine.trackers.Hallucination;
import enviromine.trackers.ItemProperties;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeInstance;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityFallingSand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EnumStatus;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityRecordPlayer;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.event.Event.Result;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Save;
import net.minecraftforge.event.world.WorldEvent.Unload;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class EM_EventManager
{

    @ForgeSubscribe
    public void onEntityJoinWorld(EntityJoinWorldEvent event)
    {
        boolean chunkPhys = true;

        if(!event.world.isRemote)
        {
            if(EM_PhysManager.chunkDelay.containsKey("" + (MathHelper.floor_double(event.entity.posX) >> 4) + "," + (MathHelper.floor_double(event.entity.posZ) >> 4)))
            {
                chunkPhys = (EM_PhysManager.chunkDelay.get("" + (MathHelper.floor_double(event.entity.posX) >> 4) + "," + (MathHelper.floor_double(event.entity.posZ) >> 4)) < event.world.getTotalWorldTime());
            }
        }

        if(event.entity instanceof EntityLivingBase)
        {
            if(event.entity.worldObj != null)
            {
                if(event.entity.worldObj.isRemote && EnviroMine.proxy.isClient() && Minecraft.getMinecraft().isIntegratedServerRunning())
                {
                    return;
                }
            }
            if(EnviroDataTracker.isLegalType((EntityLivingBase)event.entity))
            {
                // If Not tracking mob/animals and not a player than stop
                if(!(event.entity instanceof EntityPlayer) && !EM_Settings.trackNonPlayer)
                {
                    return;
                }

                if(event.entity instanceof EntityPlayer)
                {
                    EnviroDataTracker oldTrack = EM_StatusManager.lookupTrackerFromUsername(((EntityPlayer)event.entity).username);
                    if(oldTrack != null)
                    {
                        oldTrack.trackedEntity = (EntityLivingBase)event.entity;
                        oldTrack.loadNBTTags();
                        return;
                    }
                }

                EnviroDataTracker emTrack = new EnviroDataTracker((EntityLivingBase)event.entity);
                EM_StatusManager.addToManager(emTrack);
                emTrack.loadNBTTags();
                if(!EnviroMine.proxy.isClient() || EnviroMine.proxy.isOpenToLAN())
                {
                    EM_StatusManager.syncMultiplayerTracker(emTrack);
                }
            }
        } else if(event.entity instanceof EntityFallingSand && !(event.entity instanceof EntityPhysicsBlock) && !event.world.isRemote && event.world.getTotalWorldTime() > EM_PhysManager.worldStartTime + EM_Settings.worldDelay && chunkPhys)
        {
            EntityFallingSand oldSand = (EntityFallingSand)event.entity;

            if(oldSand.blockID == 0)
            {
                return;
            }

            NBTTagCompound oldTags = new NBTTagCompound();
            oldSand.writeToNBT(oldTags);

            EntityPhysicsBlock newSand = new EntityPhysicsBlock(oldSand.worldObj, oldSand.prevPosX, oldSand.prevPosY, oldSand.prevPosZ, oldSand.blockID, oldSand.metadata, true);
            newSand.readFromNBT(oldTags);
            event.world.spawnEntityInWorld(newSand);
            event.setCanceled(true);
            event.entity.setDead();
        }
    }

    @ForgeSubscribe
    public void onLivingDeath(LivingDeathEvent event)
    {
        EnviroDataTracker tracker = EM_StatusManager.lookupTracker(event.entityLiving);
        if(tracker != null)
        {
            if(event.entityLiving instanceof EntityPlayer && event.source == null)
            {
                EntityPlayer player = EM_StatusManager.findPlayer(((EntityPlayer)event.entityLiving).username);

                if(player != null)
                {
                    tracker.trackedEntity = player;
                    tracker.loadNBTTags();
                } else
                {
                    tracker.resetData();
                    EM_StatusManager.saveAndRemoveTracker(tracker);
                }
                return;
            } else
            {
                tracker.resetData();
                EM_StatusManager.saveAndRemoveTracker(tracker);
            }
        }
    }

    String lastAttID = "";

    @ForgeSubscribe
    public void onEntityAttacked(LivingAttackEvent event)
    {
        if(event.entityLiving.worldObj.isRemote || event.entity.worldObj.isRemote)
        {
            return;
        }

        if(("" + event.entityLiving.entityId + "," + event.entityLiving.worldObj.getTotalWorldTime()).equalsIgnoreCase(lastAttID))
        {
            return;
        } else
        {
            this.lastAttID = ("" + event.entityLiving.entityId + "," + event.entityLiving.worldObj.getTotalWorldTime());
        }

        Entity attacker = event.source.getEntity();

        if(attacker != null)
        {
            EnviroDataTracker tracker = EM_StatusManager.lookupTracker(event.entityLiving);

            if(event.entityLiving instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer)event.entityLiving;

                if(player.capabilities.disableDamage || player.capabilities.isCreativeMode)
                {
                    return;
                }
            }

            if(tracker != null)
            {
                EntityProperties livingProps = null;

                if(EntityList.getEntityID(attacker) > 0)
                {
                    if(EM_Settings.livingProperties.containsKey(EntityList.getEntityID(attacker)))
                    {
                        livingProps = EM_Settings.livingProperties.get(EntityList.getEntityID(attacker));
                    }
                } else if(EntityRegistry.instance().lookupModSpawn(attacker.getClass(), false) != null)
                {
                    if(EM_Settings.livingProperties.containsKey(EntityRegistry.instance().lookupModSpawn(attacker.getClass(), false).getModEntityId() + 128))
                    {
                        livingProps = EM_Settings.livingProperties.get(EntityRegistry.instance().lookupModSpawn(attacker.getClass(), false).getModEntityId() + 128);
                    }
                }

                if(livingProps != null)
                {
                    tracker.sanity += livingProps.hitSanity;
                    tracker.airQuality += livingProps.hitAir;
                    tracker.hydration += livingProps.hitHydration;
                    tracker.bodyTemp += livingProps.hitTemp;
                } else if(attacker instanceof EntityEnderman || attacker.getEntityName().toLowerCase().contains("ender"))
                {
                    tracker.sanity -= 5F;
                } else if(attacker instanceof EntityLivingBase)
                {
                    if(((EntityLivingBase)attacker).isEntityUndead())
                    {
                        tracker.sanity -= 1F;
                    }
                }
            }
        }
    }

    @ForgeSubscribe
    public void onEntitySoundPlay(PlaySoundAtEntityEvent event)
    {
        if(event.entity != null && event.entity.getEntityData().getBoolean("EM_Hallucination"))
        {
            if(EnviroMine.proxy.isClient())
            {
                Minecraft.getMinecraft().sndManager.playSound(event.name, (float)event.entity.posX, (float)event.entity.posY, (float)event.entity.posZ, 1.0F, (event.entity.worldObj.rand.nextFloat() - event.entity.worldObj.rand.nextFloat()) * 0.2F + 1.0F);
            }
            event.setCanceled(true);
        }
    }

    @ForgeSubscribe
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        ItemStack item = event.entityPlayer.getCurrentEquippedItem();

        if(event.getResult() != Result.DENY && (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR) && item != null)
        {
            if(item.getItem() instanceof ItemBlock && !event.entityPlayer.worldObj.isRemote)
            {
                int adjCoords[] = getAdjacentBlockCoordsFromSide(event.x, event.y, event.z, event.face);
                EM_PhysManager.schedulePhysUpdate(event.entityPlayer.worldObj, adjCoords[0], adjCoords[1], adjCoords[2], true, "Normal");
            } else if(item.itemID == Item.glassBottle.itemID && !event.entityPlayer.worldObj.isRemote)
            {
                fillBottle(event.entityPlayer.worldObj, event.entityPlayer, event.x, event.y, event.z, item, event);
            } else if(item.itemID == Item.record11.itemID)
            {
                RecordEasterEgg(event.entityPlayer, event.x, event.y, event.z);
            }
        } else if(event.getResult() != Result.DENY && event.action == Action.RIGHT_CLICK_BLOCK && item == null)
        {
            if(!event.entityPlayer.worldObj.isRemote)
            {
                drinkWater(event.entityPlayer, event);
            }

        } else if(event.getResult() != Result.DENY && event.action == Action.LEFT_CLICK_BLOCK)
        {
            EM_PhysManager.schedulePhysUpdate(event.entityPlayer.worldObj, event.x, event.y, event.z, true, "Normal");
        } else if(event.getResult() != Result.DENY && event.action == Action.RIGHT_CLICK_AIR && item == null && EnviroMine.proxy.isClient())
        {
            try
            {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);

                dos.writeBytes("ID:1," + event.entityPlayer.username);

                Packet250CustomPayload packet = new Packet250CustomPayload();
                packet.channel = EM_Settings.Channel;
                packet.data = bos.toByteArray();
                packet.length = bos.size();
                PacketDispatcher.sendPacketToServer(packet);

                dos.close();
                bos.close();
            } catch (IOException e)
            {
                EnviroMine.logger.log(Level.SEVERE, "EnviroMine failed to build right-click packet!", e);
            }
        }
    }

    public void RecordEasterEgg(EntityPlayer player, int x, int y, int z)
    {
        if(player.worldObj.isRemote)
        {
            return;
        }

        MovingObjectPosition movingobjectposition = getMovingObjectPositionFromPlayer(player.worldObj, player, true);

        if(movingobjectposition == null)
        {
            return;
        } else
        {
            if(movingobjectposition.typeOfHit == EnumMovingObjectType.TILE)
            {
                int i = movingobjectposition.blockX;
                int j = movingobjectposition.blockY;
                int k = movingobjectposition.blockZ;

                if(player.worldObj.getBlockId(i, j, k) == Block.jukebox.blockID)
                {
                    TileEntityRecordPlayer recordplayer = (TileEntityRecordPlayer)player.worldObj.getBlockTileEntity(i, j, k);

                    if (recordplayer != null)
                    {
                        if(recordplayer.func_96097_a() == null)
                        {
                            EnviroDataTracker tracker = EM_StatusManager.lookupTracker(player);

                            if(tracker != null)
                            {
                                if(tracker.sanity >= 75F)
                                {
                                    tracker.sanity -= 50F;
                                }

                                player.sendChatToPlayer(ChatMessageComponent.createFromText("An eerie shiver travels down your spine"));
                            }
                        }
                    }
                }
            }
        }
    }

    public static void fillBottle(World world, EntityPlayer player, int x, int y, int z, ItemStack item, PlayerInteractEvent event)
    {
        MovingObjectPosition movingobjectposition = getMovingObjectPositionFromPlayer(world, player, true);

        if(movingobjectposition == null)
        {
            return;
        } else
        {
            if(movingobjectposition.typeOfHit == EnumMovingObjectType.TILE)
            {
                int i = movingobjectposition.blockX;
                int j = movingobjectposition.blockY;
                int k = movingobjectposition.blockZ;

                boolean isValidCauldron = (player.worldObj.getBlockId(i, j, k) == Block.cauldron.blockID && player.worldObj.getBlockMetadata(i, j, k) > 0);

                if(!world.canMineBlock(player, i, j, k))
                {
                    return;
                }

                if(!player.canPlayerEdit(i, j, k, movingobjectposition.sideHit, item))
                {
                    return;
                }

                boolean isWater;


                if(world.getBlockId(i, j, k) == Block.waterStill.blockID || world.getBlockId(i, j, k) == Block.waterMoving.blockID || world.getBlockId(i-1, j, k) == Block.waterStill.blockID || world.getBlockId(i-1, j, k) == Block.waterMoving.blockID)
                {
                    isWater = true;
                } else
                {
                    isWater = false;
                }

                if(isWater || isValidCauldron)
                {
                    Item newItem = Item.potion;

                    switch(getWaterType(world, i, j, k))
                    {
                        case 0:
                        {
                            newItem = Item.potion;
                            break;
                        }
                        case 1:
                        {
                            newItem = EnviroMine.badWaterBottle;
                            break;
                        }
                        case 2:
                        {
                            newItem = EnviroMine.saltWaterBottle;
                            break;
                        }
                        case 3:
                        {
                            newItem = EnviroMine.coldWaterBottle;
                            break;
                        }
                    }

                    if(isValidCauldron && (world.getBlockId(i, j - 1, k) == Block.fire.blockID || world.getBlockId(i, j - 1, k) == Block.lavaMoving.blockID || world.getBlockId(i, j - 1, k) == Block.lavaStill.blockID))
                    {
                        newItem = Item.potion;
                    }

                    if(isValidCauldron)
                    {
                        player.worldObj.setBlockMetadataWithNotify(i, j, k, player.worldObj.getBlockMetadata(i, j, k) - 1, 2);
                    }

                    --item.stackSize;

                    if(item.stackSize <= 0)
                    {
                        item.itemID = newItem.itemID;
                        item.stackSize = 1;
                        item.setItemDamage(0);
                    } else
                    {
                        EntityItem itemDrop = player.dropPlayerItem(new ItemStack(newItem.itemID, 1, 0));
                        itemDrop.delayBeforeCanPickup = 0;
                    }

                    event.setCanceled(true);
                }
            }

            return;
        }
    }

    public static void drinkWater(EntityPlayer entityPlayer, PlayerInteractEvent event)
    {
        if(entityPlayer.isInsideOfMaterial(Material.water))
        {
            return;
        }

        EnviroDataTracker tracker = EM_StatusManager.lookupTracker(entityPlayer);
        MovingObjectPosition mop = getMovingObjectPositionFromPlayer(entityPlayer.worldObj, entityPlayer, true);

        if(mop != null)
        {
            if(mop.typeOfHit == EnumMovingObjectType.TILE)
            {
                int i = mop.blockX;
                int j = mop.blockY;
                int k = mop.blockZ;

                int[] hitBlock = getAdjacentBlockCoordsFromSide(i, j, k, mop.sideHit);

                int x = hitBlock[0];
                int y = hitBlock[1];
                int z = hitBlock[2];

                if(entityPlayer.worldObj.getBlockMaterial(i, j, k) != Material.water && entityPlayer.worldObj.getBlockMaterial(x, y, z) == Material.water)
                {
                    i = x;
                    j = y;
                    k = z;
                }

                boolean isWater;

                if(entityPlayer.worldObj.getBlockId(i, j, k) == Block.waterMoving.blockID || entityPlayer.worldObj.getBlockId(i, j, k) == Block.waterStill.blockID)
                {
                    isWater = true;
                } else
                {
                    isWater = false;
                }

                boolean isValidCauldron = (entityPlayer.worldObj.getBlockId(i, j, k) == Block.cauldron.blockID && entityPlayer.worldObj.getBlockMetadata(i, j, k) > 0);

                if(isWater || isValidCauldron)
                {
                    if(tracker != null && tracker.hydration < 100F)
                    {
                        int type = 0;

                        if(isValidCauldron && (entityPlayer.worldObj.getBlockId(i, j - 1, k) == Block.fire.blockID || entityPlayer.worldObj.getBlockId(i, j - 1, k) == Block.lavaMoving.blockID || entityPlayer.worldObj.getBlockId(i, j - 1, k) == Block.lavaStill.blockID))
                        {
                            type = 0;
                        } else
                        {
                            type = getWaterType(entityPlayer.worldObj, i, j, k);
                        }

                        if(type == 0)
                        {
                            if(tracker.bodyTemp >= 37.05F)
                            {
                                tracker.bodyTemp -= 0.05;
                            }
                            tracker.hydrate(10F);
                        } else if(type == 1)
                        {
                            if(entityPlayer.getRNG().nextInt(2) == 0)
                            {
                                entityPlayer.addPotionEffect(new PotionEffect(Potion.hunger.id, 200));
                            }
                            if(entityPlayer.getRNG().nextInt(4) == 0)
                            {
                                entityPlayer.addPotionEffect(new PotionEffect(Potion.poison.id, 200));
                            }
                            if(tracker.bodyTemp >= 37.05)
                            {
                                tracker.bodyTemp -= 0.05;
                            }
                            tracker.hydrate(10F);
                        } else if(type == 2)
                        {
                            if(entityPlayer.getRNG().nextInt(1) == 0)
                            {
                                if(entityPlayer.getActivePotionEffect(EnviroPotion.dehydration) != null && entityPlayer.getRNG().nextInt(5) == 0)
                                {
                                    int amp = entityPlayer.getActivePotionEffect(EnviroPotion.dehydration).getAmplifier();
                                    entityPlayer.addPotionEffect(new PotionEffect(EnviroPotion.dehydration.id, 600, amp + 1));
                                } else
                                {
                                    entityPlayer.addPotionEffect(new PotionEffect(EnviroPotion.dehydration.id, 600));
                                }
                            }
                            if(tracker.bodyTemp >= 37.05)
                            {
                                tracker.bodyTemp -= 0.05;
                            }
                            tracker.hydrate(5F);
                        } else if(type == 3)
                        {
                            if(tracker.bodyTemp >= 30.1)
                            {
                                tracker.bodyTemp -= 0.1;
                            }
                            tracker.hydrate(10F);
                        }

                        if(isValidCauldron)
                        {
                            entityPlayer.worldObj.setBlockMetadataWithNotify(i, j, k, entityPlayer.worldObj.getBlockMetadata(i, j, k) - 1, 2);
                        }

                        entityPlayer.worldObj.playSoundAtEntity(entityPlayer, "random.drink", 1.0F, 1.0F);

                        if(event != null)
                        {
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    public static int getWaterType(World world, int x, int y, int z)
    {
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);

        if(biome == null)
        {
            return 0;
        }

        int waterColour = biome.getWaterColorMultiplier();
        boolean looksBad = false;

        if(waterColour != 16777215)
        {
            Color bColor = new Color(waterColour);
            //Color cColor = new Color(16777215); //This is the colour of clean water in Minecraft. This is just here for reference

            if(bColor.getRed() < 200 || bColor.getGreen() < 200 || bColor.getBlue() < 200)
            {
                looksBad = true;
            }
        }

        if(biome.biomeName == BiomeGenBase.swampland.biomeName || biome.biomeName == BiomeGenBase.jungle.biomeName || biome.biomeName == BiomeGenBase.jungleHills.biomeName || y < 48 || looksBad)
        {
            return 1;
        } else if(biome.biomeName == BiomeGenBase.frozenOcean.biomeName || biome.biomeName == BiomeGenBase.ocean.biomeName || biome.biomeName == BiomeGenBase.beach.biomeName)
        {
            return 2;
        } else if(biome.biomeName == BiomeGenBase.icePlains.biomeName || biome.biomeName == BiomeGenBase.taiga.biomeName || biome.biomeName == BiomeGenBase.taigaHills.biomeName || biome.temperature < 0F || y > 127)
        {
            return 3;
        } else
        {
            return 0;
        }
    }

    public static int[] getAdjacentBlockCoordsFromSide(int x, int y, int z, int side)
    {
        int[] coords = new int[3];
        coords[0] = x;
        coords[1] = y;
        coords[2] = z;

        ForgeDirection dir = ForgeDirection.getOrientation(side);
        switch(dir)
        {
            case NORTH:
            {
                coords[2] -= 1;
                break;
            }
            case SOUTH:
            {
                coords[2] += 1;
                break;
            }
            case WEST:
            {
                coords[0] -= 1;
                break;
            }
            case EAST:
            {
                coords[0] += 1;
                break;
            }
            case UP:
            {
                coords[1] += 1;
                break;
            }
            case DOWN:
            {
                coords[1] -= 1;
                break;
            }
            case UNKNOWN:
            {
                break;
            }
        }

        return coords;
    }

    @ForgeSubscribe
    public void onBreakBlock(HarvestDropsEvent event)
    {
        if(event.world.isRemote)
        {
            return;
        }

        if(event.harvester != null)
        {
            if(event.getResult() != Result.DENY && !event.harvester.capabilities.isCreativeMode)
            {
                EM_PhysManager.schedulePhysUpdate(event.world, event.x, event.y, event.z, true, "Normal");
            }
        } else
        {
            if(event.getResult() != Result.DENY)
            {
                EM_PhysManager.schedulePhysUpdate(event.world, event.x, event.y, event.z, true, "Normal");
            }
        }
    }

    @ForgeSubscribe
    public void onLivingUpdate(LivingUpdateEvent event)
    {
        if(event.entityLiving.isDead)
        {
            return;
        }

        if(event.entityLiving.worldObj.isRemote)
        {
            if(event.entityLiving.getRNG().nextInt(5) == 0)
            {
                EM_StatusManager.createFX(event.entityLiving);
            }

            if(event.entityLiving instanceof EntityPlayer && event.entityLiving.worldObj.isRemote)
            {
                if(Minecraft.getMinecraft().thePlayer.isPotionActive(EnviroPotion.insanity))
                {
                    if(event.entityLiving.getRNG().nextInt(75) == 0)
                    {
                        new Hallucination(event.entityLiving);
                    }
                }

                Hallucination.update();
            }
            return;
        }

        EnviroDataTracker tracker = EM_StatusManager.lookupTracker(event.entityLiving);

        if(tracker == null)
        {
            return;
        }

        EM_StatusManager.updateTracker(tracker);

        UUID EM_DEHY1_ID = EM_Settings.DEHY1_UUID;

        if(tracker.hydration < 10F)
        {
            event.entityLiving.addPotionEffect(new PotionEffect(Potion.weakness.id, 200, 0));
            event.entityLiving.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 200, 0));

            AttributeInstance attribute = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
            AttributeModifier mod = new AttributeModifier(EM_DEHY1_ID, "EM_Dehydrated", -0.25D, 2);

            if(mod != null && attribute.getModifier(mod.getID()) == null)
            {
                attribute.applyModifier(mod);
            }
        } else
        {
            AttributeInstance attribute = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.movementSpeed);

            if(attribute.getModifier(EM_DEHY1_ID) != null)
            {
                attribute.removeModifier(attribute.getModifier(EM_DEHY1_ID));
            }
        }

        UUID EM_FROST1_ID = EM_Settings.FROST1_UUID;
        UUID EM_FROST2_ID = EM_Settings.FROST2_UUID;
        UUID EM_FROST3_ID = EM_Settings.FROST3_UUID;
        UUID EM_HEAT1_ID = EM_Settings.HEAT1_UUID;

        if(event.entityLiving.isPotionActive(EnviroPotion.heatstroke))
        {
            AttributeInstance attribute = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
            AttributeModifier mod = new AttributeModifier(EM_HEAT1_ID, "EM_Heat", -0.25D, 2);

            if(mod != null && attribute.getModifier(mod.getID()) == null)
            {
                attribute.applyModifier(mod);
            }
        } else
        {
            AttributeInstance attribute = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.movementSpeed);

            if(attribute.getModifier(EM_HEAT1_ID) != null)
            {
                attribute.removeModifier(attribute.getModifier(EM_HEAT1_ID));
            }
        }

        if(event.entityLiving.isPotionActive(EnviroPotion.hypothermia) || event.entityLiving.isPotionActive(EnviroPotion.frostbite))
        {
            AttributeInstance attribute = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
            AttributeModifier mod = new AttributeModifier(EM_FROST1_ID, "EM_Frost_Cold", -0.25D, 2);
            String msg = "";

            if(event.entityLiving.isPotionActive(EnviroPotion.frostbite))
            {
                if(event.entityLiving.getActivePotionEffect(EnviroPotion.frostbite).getAmplifier() > 0)
                {
                    mod = new AttributeModifier(EM_FROST3_ID, "EM_Frost_NOLEGS", -0.99D, 2);

                    if(event.entityLiving instanceof EntityPlayer)
                    {
                        msg = "Your legs stiffen as they succumb to frostbite";
                    }
                } else
                {
                    mod = new AttributeModifier(EM_FROST2_ID, "EM_Frost_NOHANDS", -0.5D, 2);

                    if(event.entityLiving instanceof EntityPlayer)
                    {
                        msg = "Your fingers start to feel numb and unresponsive";
                    }
                }
            }
            if(mod != null && attribute.getModifier(mod.getID()) == null)
            {
                attribute.applyModifier(mod);

                if(event.entityLiving instanceof EntityPlayer && mod.getID() != EM_FROST1_ID)
                {
                    ((EntityPlayer)event.entityLiving).sendChatToPlayer(ChatMessageComponent.createFromText(msg));
                }
            }
        } else
        {
            AttributeInstance attribute = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.movementSpeed);

            if(attribute.getModifier(EM_FROST1_ID) != null)
            {
                attribute.removeModifier(attribute.getModifier(EM_FROST1_ID));
            }
            if(attribute.getModifier(EM_FROST2_ID) != null && tracker.frostbiteLevel < 1)
            {
                attribute.removeModifier(attribute.getModifier(EM_FROST2_ID));
            }
            if(attribute.getModifier(EM_FROST3_ID) != null && tracker.frostbiteLevel < 2)
            {
                attribute.removeModifier(attribute.getModifier(EM_FROST3_ID));
            }
        }

        if(event.entityLiving instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer)event.entityLiving;

            if(player.isPlayerSleeping() && !player.worldObj.isRemote)
            {
                int i = MathHelper.floor_double(player.posX);
                int j = MathHelper.floor_double(player.posY) + 1;
                int k = MathHelper.floor_double(player.posZ);

                if(player.worldObj.isRaining() && player.worldObj.canBlockSeeTheSky(i, j, k))
                {
                    player.wakeUpPlayer(true, true, false);
                    player.sendChatToPlayer(ChatMessageComponent.createFromText("You can't sleep outside while it's " + (player.worldObj.canSnowAt(i, j, k)? "snow" : "rain") + "ing"));
                }
            }

            ItemStack item = null;
            int itemUse = 0;

            if(((EntityPlayer)event.entityLiving).isPlayerSleeping() && tracker != null)
            {
                tracker.sleepState = "Asleep";
                tracker.lastSleepTime = (int)event.entityLiving.worldObj.getWorldInfo().getWorldTime() % 24000;
            } else if(tracker != null)
            {
                int relitiveTime = (int)event.entityLiving.worldObj.getWorldInfo().getWorldTime() % 24000;

                if(tracker.sleepState.equals("Asleep") && tracker.lastSleepTime - relitiveTime > 100)
                {
                    int timeSlept = MathHelper.floor_float(100*(12000 - (tracker.lastSleepTime - 12000))/12000);

                    if(tracker.sanity + timeSlept > 100F)
                    {
                        tracker.sanity = 100;
                        if(tracker.bodyTemp < 37F)
                        {
                            tracker.bodyTemp = 37F;
                        }
                    } else if(timeSlept >= 0)
                    {
                        tracker.sanity += timeSlept;
                        if(tracker.bodyTemp < 37F)
                        {
                            tracker.bodyTemp = 37F;
                        }
                    } else
                    {
                        EnviroMine.logger.log(Level.SEVERE, "Something went wrong while calculating sleep sanity gain! Result: " + timeSlept);
                        tracker.sanity = 100;
                        if(tracker.trackedEntity instanceof EntityPlayer)
                        {
                            ((EntityPlayer)tracker.trackedEntity).sendChatToPlayer(ChatMessageComponent.createFromText(EnumChatFormatting.RED + "[ENVIROMINE] Sleep state failed to detect sleep time properly!"));
                            ((EntityPlayer)tracker.trackedEntity).sendChatToPlayer(ChatMessageComponent.createFromText(EnumChatFormatting.RED + "[ENVIROMINE] Defaulting to 100%"));
                        }
                    }
                }
                tracker.sleepState = "Awake";
            }

            if(((EntityPlayer)event.entityLiving).isUsingItem())
            {
                item = ((EntityPlayer)event.entityLiving).getHeldItem();

                if(tracker != null)
                {
                    itemUse = tracker.getAndIncrementItemUse();
                } else
                {
                    itemUse = 0;
                }
            } else
            {
                item = null;

                if(tracker != null)
                {
                    tracker.resetItemUse();
                } else
                {
                    itemUse = 0;
                }
            }

            if(item != null && tracker != null)
            {
                if(itemUse >= Item.itemsList[item.itemID].getMaxItemUseDuration(item) - 1)
                {
                    itemUse = 0;
                    if(EM_Settings.itemProperties.containsKey("" + item.itemID) || EM_Settings.itemProperties.containsKey("" + item.itemID + "," + item.getItemDamage()))
                    {
                        ItemProperties itemProps;
                        if(EM_Settings.itemProperties.containsKey("" + item.itemID + "," + item.getItemDamage()))
                        {
                            itemProps = EM_Settings.itemProperties.get("" + item.itemID + "," + item.getItemDamage());
                        } else
                        {
                            itemProps = EM_Settings.itemProperties.get("" + item.itemID);
                        }

                        if(itemProps.effTemp > 0F)
                        {
                            if(tracker.bodyTemp + itemProps.effTemp > itemProps.effTempCap)
                            {
                                if(tracker.bodyTemp <= itemProps.effTempCap)
                                {
                                    tracker.bodyTemp = itemProps.effTempCap;
                                }
                            } else
                            {
                                tracker.bodyTemp += itemProps.effTemp;
                            }
                        } else
                        {
                            if(tracker.bodyTemp + itemProps.effTemp < itemProps.effTempCap)
                            {
                                if(tracker.bodyTemp >= itemProps.effTempCap)
                                {
                                    tracker.bodyTemp = itemProps.effTempCap;
                                }
                            } else
                            {
                                tracker.bodyTemp += itemProps.effTemp;
                            }
                        }

                        if(tracker.sanity + itemProps.effSanity >= 100F)
                        {
                            tracker.sanity = 100F;
                        } else if(tracker.sanity + itemProps.effSanity <= 0F)
                        {
                            tracker.sanity = 0F;
                        } else
                        {
                            tracker.sanity += itemProps.effSanity;
                        }

                        if(itemProps.effHydration > 0F)
                        {
                            tracker.hydrate(itemProps.effHydration);
                        } else if(itemProps.effHydration < 0F)
                        {
                            tracker.dehydrate(Math.abs(itemProps.effHydration));
                        }

                        if(tracker.airQuality + itemProps.effAir >= 100F)
                        {
                            tracker.airQuality = 100F;
                        } else if(tracker.airQuality + itemProps.effAir <= 0F)
                        {
                            tracker.airQuality = 0F;
                        } else
                        {
                            tracker.airQuality += itemProps.effAir;
                        }
                    } else if(item.itemID == Item.appleGold.itemID)
                    {
                        if(item.isItemDamaged())
                        {
                            tracker.hydration = 100F;
                            tracker.sanity = 100F;
                            tracker.airQuality = 100F;
                            tracker.bodyTemp = 37F;
                            if(!EnviroMine.proxy.isClient() || EnviroMine.proxy.isOpenToLAN())
                            {
                                EM_StatusManager.syncMultiplayerTracker(tracker);
                            }
                        } else
                        {
                            tracker.sanity = 100F;
                            tracker.hydrate(10F);
                        }

                        tracker.trackedEntity.removePotionEffect(EnviroPotion.frostbite.id);
                        tracker.frostbiteLevel = 0;
                    } else if(item.itemID == Item.bowlSoup.itemID || item.itemID == Item.pumpkinPie.itemID)
                    {
                        if(tracker.bodyTemp <= 40F)
                        {
                            tracker.bodyTemp += 0.05F;
                        }
                        tracker.hydrate(5F);
                    } else if(item.itemID == Item.bucketMilk.itemID)
                    {
                        tracker.hydrate(10F);
                    } else if(item.itemID == Item.porkCooked.itemID || item.itemID == Item.beefCooked.itemID || item.itemID == Item.chickenCooked.itemID || item.itemID == Item.bakedPotato.itemID)
                    {
                        if(tracker.bodyTemp <= 40F)
                        {
                            tracker.bodyTemp += 0.05F;
                        }
                        if(!EnviroMine.proxy.isClient() || EnviroMine.proxy.isOpenToLAN())
                        {
                            EM_StatusManager.syncMultiplayerTracker(tracker);
                        }
                    } else if(item.itemID == Item.appleRed.itemID)
                    {
                        tracker.hydrate(5F);
                    } else if(item.itemID == Item.rottenFlesh.itemID || item.itemID == Item.spiderEye.itemID || item.itemID == Item.poisonousPotato.itemID)
                    {
                        tracker.dehydrate(5F);
                        if(tracker.sanity <= 1F)
                        {
                            tracker.sanity = 0F;
                        } else
                        {
                            tracker.sanity -= 1F;
                        }
                    }
                }
            }
        }
    }

    @ForgeSubscribe
    public void onPlayerSleepInBed(PlayerSleepInBedEvent event)
    {
        if(event.entityPlayer.worldObj.isRemote)
        {
            return;
        }

        if (event.entityPlayer.isPlayerSleeping() || !event.entityPlayer.isEntityAlive())
        {
            return;
        }

        if (!event.entityPlayer.worldObj.provider.canRespawnHere())
        {
            return;
        }

        if (event.entityPlayer.worldObj.isDaytime())
        {
            return;
        }

        if (Math.abs(event.entityPlayer.posX - (double)event.x) > 3.0D || Math.abs(event.entityPlayer.posY - (double)event.y) > 2.0D || Math.abs(event.entityPlayer.posZ - (double)event.z) > 3.0D)
        {
            return;
        }
        double d0 = 8.0D;
        double d1 = 5.0D;
        List list = event.entityPlayer.worldObj.getEntitiesWithinAABB(EntityMob.class, AxisAlignedBB.getAABBPool().getAABB((double)event.x - d0, (double)event.y - d1, (double)event.z - d0, (double)event.x + d0, (double)event.y + d1, (double)event.z + d0));

        if (!list.isEmpty())
        {
            return;
        }

        if(event.entityPlayer.worldObj.canBlockSeeTheSky(event.x, event.y, event.z) && event.entityPlayer.worldObj.isRaining())
        {
            event.result = EnumStatus.OTHER_PROBLEM;
            event.entityPlayer.sendChatToPlayer(ChatMessageComponent.createFromText("You can't sleep outside while it's " + (event.entityPlayer.worldObj.canSnowAt(event.x, event.y, event.z)? "snow" : "rain") + "ing"));
        }
    }

    @ForgeSubscribe
    public void onJump(LivingJumpEvent event)
    {
        if(event.entityLiving.isPotionActive(EnviroPotion.frostbite))
        {
            if(event.entityLiving.getActivePotionEffect(EnviroPotion.frostbite).getAmplifier() > 0)
            {
                event.entityLiving.motionY = 0;
            }
        }
    }

    @ForgeSubscribe
    public void onLand(LivingFallEvent event)
    {
        if(event.entityLiving.getRNG().nextInt(5) == 0)
        {
            EM_PhysManager.schedulePhysUpdate(event.entityLiving.worldObj, MathHelper.floor_double(event.entityLiving.posX), MathHelper.floor_double(event.entityLiving.posY - 1), MathHelper.floor_double(event.entityLiving.posZ), true, "Jump");
        }
    }

    @ForgeSubscribe
    public void onWorldLoad(Load event)
    {
        if(event.world.isRemote)
        {
            return;
        }

        if(EM_PhysManager.worldStartTime < 0)
        {
            EM_PhysManager.worldStartTime = event.world.getTotalWorldTime();
        }
    }

    @ForgeSubscribe
    public void onWorldUnload(Unload event)
    {
        EM_StatusManager.saveAndDeleteWorldTrackers(event.world);

        if(!event.world.isRemote)
        {
            if(!MinecraftServer.getServer().isServerRunning())
            {
                EM_PhysManager.physSchedule.clear();
                EM_PhysManager.excluded.clear();
                EM_PhysManager.usedSlidePositions.clear();
                EM_PhysManager.worldStartTime = -1;
                EM_PhysManager.chunkDelay.clear();
            }
        }
    }

    @ForgeSubscribe
    public void onChunkLoad(ChunkEvent.Load event)
    {
        if(event.world.isRemote)
        {
            return;
        }

        if(!EM_PhysManager.chunkDelay.containsKey("" + event.getChunk().xPosition + "," + event.getChunk().zPosition))
        {
            EM_PhysManager.chunkDelay.put("" + event.getChunk().xPosition + "," + event.getChunk().zPosition, event.world.getTotalWorldTime() + EM_Settings.chunkDelay);
        }
    }

    @ForgeSubscribe
    public void onWorldSave(Save event)
    {
        EM_StatusManager.saveAllWorldTrackers(event.world);
    }

    protected static MovingObjectPosition getMovingObjectPositionFromPlayer(World par1World, EntityPlayer par2EntityPlayer, boolean par3)
    {
        float f = 1.0F;
        float f1 = par2EntityPlayer.prevRotationPitch + (par2EntityPlayer.rotationPitch - par2EntityPlayer.prevRotationPitch) * f;
        float f2 = par2EntityPlayer.prevRotationYaw + (par2EntityPlayer.rotationYaw - par2EntityPlayer.prevRotationYaw) * f;
        double d0 = par2EntityPlayer.prevPosX + (par2EntityPlayer.posX - par2EntityPlayer.prevPosX) * (double)f;
        double d1 = par2EntityPlayer.prevPosY + (par2EntityPlayer.posY - par2EntityPlayer.prevPosY) * (double)f + (double)(par1World.isRemote ? par2EntityPlayer.getEyeHeight() - par2EntityPlayer.getDefaultEyeHeight() : par2EntityPlayer.getEyeHeight()); // isRemote check to revert changes to ray trace position due to adding the eye height clientside and player yOffset differences
        double d2 = par2EntityPlayer.prevPosZ + (par2EntityPlayer.posZ - par2EntityPlayer.prevPosZ) * (double)f;
        Vec3 vec3 = par1World.getWorldVec3Pool().getVecFromPool(d0, d1, d2);
        float f3 = MathHelper.cos(-f2 * 0.017453292F - (float)Math.PI);
        float f4 = MathHelper.sin(-f2 * 0.017453292F - (float)Math.PI);
        float f5 = -MathHelper.cos(-f1 * 0.017453292F);
        float f6 = MathHelper.sin(-f1 * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = 5.0D;
        if(par2EntityPlayer instanceof EntityPlayerMP)
        {
            d3 = ((EntityPlayerMP)par2EntityPlayer).theItemInWorldManager.getBlockReachDistance();
        }
        Vec3 vec31 = vec3.addVector((double)f7 * d3, (double)f6 * d3, (double)f8 * d3);
        return par1World.rayTraceBlocks_do_do(vec3, vec31, par3, !par3);
    }
}

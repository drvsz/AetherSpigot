package net.minecraft.server;

// CraftBukkit start
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
// CraftBukkit end

public class PlayerInteractManager {

    public World world;
    public EntityPlayer player;
    private WorldSettings.EnumGamemode gamemode;
    private boolean d;
    private int lastDigTick;
    // AetherSpigot start
    private int lastPlayerDigTick;
    private long startBreaking;
    // AetherSpigot end
    private BlockPosition f;
    private int currentTick;
    private boolean h;
    private BlockPosition i;
    private int j;
    private int k;

    public PlayerInteractManager(World world) {
        this.gamemode = WorldSettings.EnumGamemode.NOT_SET;
        this.f = BlockPosition.ZERO;
        this.i = BlockPosition.ZERO;
        this.k = -1;
        this.world = world;
    }

    public void setGameMode(WorldSettings.EnumGamemode worldsettings_enumgamemode) {
        this.gamemode = worldsettings_enumgamemode;
        worldsettings_enumgamemode.a(this.player.abilities);
        this.player.updateAbilities();
        this.player.server.getPlayerList().sendAll(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.UPDATE_GAME_MODE, new EntityPlayer[] { this.player}), this.player); // CraftBukkit
    }

    public WorldSettings.EnumGamemode getGameMode() {
        return this.gamemode;
    }

    public boolean c() {
        return this.gamemode.e();
    }

    public boolean isCreative() {
        return this.gamemode.d();
    }

    public void b(WorldSettings.EnumGamemode worldsettings_enumgamemode) {
        if (this.gamemode == WorldSettings.EnumGamemode.NOT_SET) {
            this.gamemode = worldsettings_enumgamemode;
        }

        this.setGameMode(this.gamemode);
    }

    public void a() {
        this.currentTick = MinecraftServer.currentTick; // CraftBukkit;
        float f;
        int i;

        if (this.h) {
            int j = this.currentTick - this.j;
            Block block = this.world.getType(this.i).getBlock();

            if (block.getMaterial() == Material.AIR) {
                this.h = false;
            } else {
                f = block.getDamage(this.player, this.player.world, this.i) * (float) (j + 1);
                i = (int) (f * 10.0F);
                if (i != this.k) {
                    this.world.c(this.player.getId(), this.i, i);
                    this.k = i;
                }

                if (f >= 1.0F) {
                    this.h = false;
                    this.breakBlock(this.i);
                }
            }
        } else if (this.d) {
            Block block1 = this.world.getType(this.f).getBlock();

            if (block1.getMaterial() == Material.AIR) {
                this.world.c(this.player.getId(), this.f, -1);
                this.k = -1;
                this.d = false;
            } else {
                int k = this.currentTick - this.lastDigTick;

                f = block1.getDamage(this.player, this.player.world, this.i) * (float) (k + 1);
                i = (int) (f * 10.0F);
                if (i != this.k) {
                    this.world.c(this.player.getId(), this.f, i);
                    this.k = i;
                }
            }
        }

    }

    public void a(BlockPosition blockposition, EnumDirection enumdirection) {
        // CraftBukkit start
        PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, blockposition, enumdirection, this.player.inventory.getItemInHand());
        if (event.isCancelled()) {
            // Let the client know the block still exists
            ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
            // Update any tile entity data for this block
            TileEntity tileentity = this.world.getTileEntity(blockposition);
            if (tileentity != null) {
                this.player.playerConnection.sendPacket(tileentity.getUpdatePacket());
            }
            return;
        }
        // CraftBukkit end
        if (this.isCreative()) {
            if (!this.world.douseFire((EntityHuman) null, blockposition, enumdirection)) {
                this.breakBlock(blockposition);
            }

        } else {
            Block block = this.world.getType(blockposition).getBlock();

            if (this.gamemode.c()) {
                if (this.gamemode == WorldSettings.EnumGamemode.SPECTATOR) {
                    return;
                }

                if (!this.player.cn()) {
                    ItemStack itemstack = this.player.bZ();

                    if (itemstack == null) {
                        return;
                    }

                    if (!itemstack.c(block)) {
                        return;
                    }
                }
            }

            // this.world.douseFire((EntityHuman) null, blockposition, enumdirection); // CraftBukkit - Moved down
            this.lastDigTick = this.currentTick;
            // AetherSpigot start
            this.lastPlayerDigTick = this.player.movementTick;
            this.startBreaking = System.currentTimeMillis();
            // AetherSpigot end
            float f = 1.0F;

            // CraftBukkit start - Swings at air do *NOT* exist.
            if (event.useInteractedBlock() == Event.Result.DENY) {
                // If we denied a door from opening, we need to send a correcting update to the client, as it already opened the door.
                IBlockData data = this.world.getType(blockposition);
                if (block == Blocks.WOODEN_DOOR) {
                    // For some reason *BOTH* the bottom/top part have to be marked updated.
                    boolean bottom = data.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;
                    ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
                    ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, bottom ? blockposition.up() : blockposition.down()));
                } else if (block == Blocks.TRAPDOOR) {
                    ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
                }
            } else if (block.getMaterial() != Material.AIR) {
                block.attack(this.world, blockposition, this.player);
                f = block.getDamage(this.player, this.player.world, blockposition);
                // Allow fire punching to be blocked
                this.world.douseFire((EntityHuman) null, blockposition, enumdirection);
            }

            if (event.useItemInHand() == Event.Result.DENY) {
                // If we 'insta destroyed' then the client needs to be informed.
                if (f > 1.0f) {
                    ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
                }
                return;
            }
            org.bukkit.event.block.BlockDamageEvent blockEvent = CraftEventFactory.callBlockDamageEvent(this.player, blockposition.getX(), blockposition.getY(), blockposition.getZ(), this.player.inventory.getItemInHand(), f >= 1.0f);

            if (blockEvent.isCancelled()) {
                // Let the client know the block still exists
                ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
                return;
            }

            if (blockEvent.getInstaBreak()) {
                f = 2.0f;
            }
            // CraftBukkit end

            if (block.getMaterial() != Material.AIR && f >= 1.0F) {
                this.breakBlock(blockposition);
            } else {
                this.d = true;
                this.f = blockposition;
                int i = (int) (f * 10.0F);

                this.world.c(this.player.getId(), blockposition, i);
                this.k = i;
            }

        }
        world.spigotConfig.antiXrayInstance.updateNearbyBlocks(world, blockposition); // Spigot
    }

    public void a(BlockPosition blockposition) {
        if (blockposition.equals(this.f)) {
            this.currentTick = MinecraftServer.currentTick; // CraftBukkit
            int i = this.currentTick - this.lastDigTick;
            Block block = this.world.getType(blockposition).getBlock();

            if (block.getMaterial() != Material.AIR) {
                // AetherSpigot start
                long timeInTicks = (System.currentTimeMillis() - this.startBreaking) / 50L;
                int playerDigTicks = this.player.movementTick - this.lastPlayerDigTick;
                float damagePerTick = block.getDamage(this.player, this.player.world, blockposition);

                float requiredTicks = 1f / damagePerTick;
                float compensation = 20f / Math.min((float) MinecraftServer.getServer().tps1.getAverage(), 20f) - 1f;
                if (timeInTicks + 1 >= requiredTicks - compensation || playerDigTicks >= requiredTicks) {
                    // AetherSpigot end
                    this.d = false;
                    this.world.c(this.player.getId(), blockposition, -1);
                    this.breakBlock(blockposition);
                } else if (!this.h) {
                    this.d = false;
                    this.h = true;
                    this.i = blockposition;
                    this.j = this.lastDigTick;
                }
            }
        // CraftBukkit start - Force block reset to client
        } else {
            this.player.playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
            // CraftBukkit end
        }

    }

    public void e() {
        this.d = false;
        this.world.c(this.player.getId(), this.f, -1);
    }

    private boolean c(BlockPosition blockposition) {
        IBlockData iblockdata = this.world.getType(blockposition);

        iblockdata.getBlock().a(this.world, blockposition, iblockdata, (EntityHuman) this.player);
        boolean flag = this.world.setAir(blockposition);

        if (flag) {
            iblockdata.getBlock().postBreak(this.world, blockposition, iblockdata);
        }

        return flag;
    }

    public boolean breakBlock(BlockPosition blockposition) {
        // CraftBukkit start - fire BlockBreakEvent
        BlockBreakEvent event = null;

        if (this.player instanceof EntityPlayer) {
            org.bukkit.block.Block block = this.world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

            // Sword + Creative mode pre-cancel
            boolean isSwordNoBreak = this.gamemode.d() && this.player.bA() != null && this.player.bA().getItem() instanceof ItemSword;

            // Tell client the block is gone immediately then process events
            // Don't tell the client if its a creative sword break because its not broken!
            if (world.getTileEntity(blockposition) == null && !isSwordNoBreak) {
                PacketPlayOutBlockChange packet = new PacketPlayOutBlockChange(this.world, blockposition);
                packet.block = Blocks.AIR.getBlockData();
                ((EntityPlayer) this.player).playerConnection.sendPacket(packet);
            }

            event = new BlockBreakEvent(block, this.player.getBukkitEntity());

            // Sword + Creative mode pre-cancel
            event.setCancelled(isSwordNoBreak);

            // Calculate default block experience
            IBlockData nmsData = this.world.getType(blockposition);
            Block nmsBlock = nmsData.getBlock();

            if (nmsBlock != null && !event.isCancelled() && !this.isCreative() && this.player.b(nmsBlock)) {
                // Copied from block.a(World world, EntityHuman entityhuman, BlockPosition blockposition, IBlockData iblockdata, TileEntity tileentity)
                if (!(nmsBlock.I() && EnchantmentManager.hasSilkTouchEnchantment(this.player))) {
                    int data = block.getData();
                    int bonusLevel = EnchantmentManager.getBonusBlockLootEnchantmentLevel(this.player);

                    event.setExpToDrop(nmsBlock.getExpDrop(this.world, nmsData, bonusLevel));
                }
            }

            this.world.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                if (isSwordNoBreak) {
                    return false;
                }
                // Let the client know the block still exists
                ((EntityPlayer) this.player).playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
                // Update any tile entity data for this block
                TileEntity tileentity = this.world.getTileEntity(blockposition);
                if (tileentity != null) {
                    this.player.playerConnection.sendPacket(tileentity.getUpdatePacket());
                }
                return false;
            }
        }
        if (false && this.gamemode.d() && this.player.bA() != null && this.player.bA().getItem() instanceof ItemSword) {
            return false;
        } else {
            IBlockData iblockdata = this.world.getType(blockposition);
            if (iblockdata.getBlock() == Blocks.AIR) return false; // CraftBukkit - A plugin set block to air without cancelling
            TileEntity tileentity = this.world.getTileEntity(blockposition);

            // CraftBukkit start - Special case skulls, their item data comes from a tile entity
            if (iblockdata.getBlock() == Blocks.SKULL && !this.isCreative()) {
                iblockdata.getBlock().dropNaturally(world, blockposition, iblockdata, 1.0F, 0);
                return this.c(blockposition);
            }
            // CraftBukkit end

            if (this.gamemode.c()) {
                if (this.gamemode == WorldSettings.EnumGamemode.SPECTATOR) {
                    return false;
                }

                if (!this.player.cn()) {
                    ItemStack itemstack = this.player.bZ();

                    if (itemstack == null) {
                        return false;
                    }

                    if (!itemstack.c(iblockdata.getBlock())) {
                        return false;
                    }
                }
            }

            this.world.a(this.player, 2001, blockposition, Block.getCombinedId(iblockdata));
            boolean flag = this.c(blockposition);

            if (this.isCreative()) {
                this.player.playerConnection.sendPacket(new PacketPlayOutBlockChange(this.world, blockposition));
            } else {
                ItemStack itemstack1 = this.player.bZ();
                boolean flag1 = this.player.b(iblockdata.getBlock());

                if (itemstack1 != null) {
                    itemstack1.a(this.world, iblockdata.getBlock(), blockposition, this.player);
                    if (itemstack1.count == 0) {
                        this.player.ca();
                    }
                }

                if (flag && flag1) {
                    iblockdata.getBlock().a(this.world, this.player, blockposition, iblockdata, tileentity);
                }
            }
            
            // CraftBukkit start - Drop event experience
            if (flag && event != null) {
                iblockdata.getBlock().dropExperience(this.world, blockposition, event.getExpToDrop());
            }
            // CraftBukkit end

            return flag;
        }
    }

    public boolean useItem(EntityHuman entityhuman, World world, ItemStack itemstack) {
        if (this.gamemode == WorldSettings.EnumGamemode.SPECTATOR) {
            return false;
        } else {
            int i = itemstack.count;
            int j = itemstack.getData();
            ItemStack itemstack1 = itemstack.a(world, entityhuman);

            if (itemstack1 == itemstack && (itemstack1 == null || itemstack1.count == i && itemstack1.l() <= 0 && itemstack1.getData() == j)) {
                return false;
            } else {
                entityhuman.inventory.items[entityhuman.inventory.itemInHandIndex] = itemstack1;
                if (this.isCreative()) {
                    itemstack1.count = i;
                    if (itemstack1.e()) {
                        itemstack1.setData(j);
                    }
                }

                if (itemstack1.count == 0) {
                    entityhuman.inventory.items[entityhuman.inventory.itemInHandIndex] = null;
                }

                if (!entityhuman.isUsingItem()) {
                    ((EntityPlayer) entityhuman).updateInventory(entityhuman.defaultContainer);
                }

                return true;
            }
        }
    }

    // CraftBukkit start
    public boolean interactResult = false;
    public boolean firedInteract = false;
    // CraftBukkit end

    public boolean interact(EntityHuman entityhuman, World world, ItemStack itemstack, BlockPosition blockposition, EnumDirection enumdirection, float f, float f1, float f2) {
        /* CraftBukkit start - whole method
        if (this.gamemode == WorldSettings.EnumGamemode.SPECTATOR) {
            TileEntity tileentity = world.getTileEntity(blockposition);

            if (tileentity instanceof ITileInventory) {
                Block block = world.getType(blockposition).getBlock();
                ITileInventory itileinventory = (ITileInventory) tileentity;

                if (itileinventory instanceof TileEntityChest && block instanceof BlockChest) {
                    itileinventory = ((BlockChest) block).f(world, blockposition);
                }

                if (itileinventory != null) {
                    entityhuman.openContainer(itileinventory);
                    return true;
                }
            } else if (tileentity instanceof IInventory) {
                entityhuman.openContainer((IInventory) tileentity);
                return true;
            }

            return false;
        } else {
            if (!entityhuman.isSneaking() || entityhuman.bA() == null) {
                IBlockData iblockdata = world.getType(blockposition);

                if (iblockdata.getBlock().interact(world, blockposition, iblockdata, entityhuman, enumdirection, f, f1, f2)) {
                    return true;
                }
            }

            if (itemstack == null) {
                return false;
            } else if (this.isCreative()) {
                int i = itemstack.getData();
                int j = itemstack.count;
                boolean flag = itemstack.placeItem(entityhuman, world, blockposition, enumdirection, f, f1, f2);

                itemstack.setData(i);
                itemstack.count = j;
                return flag;
            } else {
                return itemstack.placeItem(entityhuman, world, blockposition, enumdirection, f, f1, f2);
            }
        }
        // Interract event */
        IBlockData blockdata = world.getType(blockposition);
        boolean result = false;
        if (blockdata.getBlock() != Blocks.AIR) {
            boolean cancelledBlock = false;

            if (this.gamemode == WorldSettings.EnumGamemode.SPECTATOR) {
                TileEntity tileentity = world.getTileEntity(blockposition);
                cancelledBlock = !(tileentity instanceof ITileInventory || tileentity instanceof IInventory);
            }

            if (!entityhuman.getBukkitEntity().isOp() && itemstack != null && Block.asBlock(itemstack.getItem()) instanceof BlockCommand) {
                cancelledBlock = true;
            }

            PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(entityhuman, Action.RIGHT_CLICK_BLOCK, blockposition, enumdirection, itemstack, cancelledBlock);
            firedInteract = true;
            interactResult = event.useItemInHand() == Event.Result.DENY;

            if (event.useInteractedBlock() == Event.Result.DENY) {
                // If we denied a door from opening, we need to send a correcting update to the client, as it already opened the door.
                if (blockdata.getBlock() instanceof BlockDoor) {
                    boolean bottom = blockdata.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;
                    ((EntityPlayer) entityhuman).playerConnection.sendPacket(new PacketPlayOutBlockChange(world, bottom ? blockposition.up() : blockposition.down()));
                }
                result = (event.useItemInHand() != Event.Result.ALLOW);
            } else if (this.gamemode == WorldSettings.EnumGamemode.SPECTATOR) {
                TileEntity tileentity = world.getTileEntity(blockposition);

                if (tileentity instanceof ITileInventory) {
                    Block block = world.getType(blockposition).getBlock();
                    ITileInventory itileinventory = (ITileInventory) tileentity;

                    if (itileinventory instanceof TileEntityChest && block instanceof BlockChest) {
                        itileinventory = ((BlockChest) block).f(world, blockposition);
                    }

                    if (itileinventory != null) {
                        entityhuman.openContainer(itileinventory);
                        return true;
                    }
                } else if (tileentity instanceof IInventory) {
                    entityhuman.openContainer((IInventory) tileentity);
                    return true;
                }

                return false;
            } else if (!entityhuman.isSneaking() || itemstack == null) {
                result = blockdata.getBlock().interact(world, blockposition, blockdata, entityhuman, enumdirection, f, f1, f2);
            }

            if (itemstack != null && !result && !interactResult) { // add !interactResult SPIGOT-764
                int j1 = itemstack.getData();
                int k1 = itemstack.count;

                result = itemstack.placeItem(entityhuman, world, blockposition, enumdirection, f, f1, f2);

                // The item count should not decrement in Creative mode.
                if (this.isCreative()) {
                    itemstack.setData(j1);
                    itemstack.count = k1;
                }
            }
        }
        return result;
        // CraftBukkit end
    }

    public void a(WorldServer worldserver) {
        this.world = worldserver;
    }
}

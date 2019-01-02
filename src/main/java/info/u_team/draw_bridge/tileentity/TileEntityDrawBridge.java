package info.u_team.draw_bridge.tileentity;

import info.u_team.draw_bridge.block.BlockDrawBridge;
import info.u_team.u_team_core.tileentity.UTileEntity;
import info.u_team.u_team_core.util.NonNullListUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

public class TileEntityDrawBridge extends UTileEntity implements ITickable, IInventory {
	
	private NonNullList<ItemStack> itemstacks;
	
	private boolean extended;
	private boolean[] ourBlocks = new boolean[10];
	
	public TileEntityDrawBridge() {
		itemstacks = NonNullListUtil.withSize(11, ItemStack.EMPTY);
	}
	
	@Override
	public void onLoad() {
		super.onLoad();
		if (world.isRemote)
			System.out.println("LOADED");
	}
	
	@Override
	public void validate() {
		super.validate();
		if (world.isRemote)
			System.out.println("VALIDATED");
	}
	
	@Override
	public void update() {
		if (world.isRemote) {
			return;
		}
		boolean powered = world.isBlockPowered(pos);
		if (powered && !extended) {
			extend();
		} else if (!powered && extended) {
			retract();
		}
	}
	
	@SuppressWarnings("deprecation")
	private void extend() {
		EnumFacing facing = world.getBlockState(pos).getValue(BlockDrawBridge.FACING);
		for (int i = 0; i < 10; i++) {
			BlockPos newPos = pos.offset(facing, i + 1);
			if (world.isAirBlock(newPos)) {
				ItemStack itemstack = getStackInSlot(i);
				
				Block block = Block.getBlockFromItem(itemstack.getItem());
				world.setBlockState(newPos, block.getStateFromMeta(itemstack.getMetadata()));
				removeStackFromSlot(i);
				ourBlocks[i] = true;
			} else {
				ourBlocks[i] = false;
			}
		}
		extended = true;
		sendChangesToClient();
	}
	
	private void retract() {
		EnumFacing facing = world.getBlockState(pos).getValue(BlockDrawBridge.FACING);
		for (int i = 0; i < 10; i++) {
			if (ourBlocks[i]) {
				BlockPos newPos = pos.offset(facing, i + 1);
				if (!world.isAirBlock(newPos)) {
					IBlockState state = world.getBlockState(newPos);
					Block block = state.getBlock();
					
					ItemStack stack = new ItemStack(block, 1, block.getMetaFromState(state));
					setInventorySlotContents(i, stack);
					
					world.setBlockToAir(newPos);
				}
			}
		}
		extended = false;
		sendChangesToClient();
	}
	
	// Force render update
	
	public void forceRerendering() {
		getWorld().markBlockRangeForRenderUpdate(getPos(), getPos());
	}
	
	// sync data server -> client
	
	@Override
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound compound = new NBTTagCompound();
		compound.setInteger("x", pos.getX());
		compound.setInteger("y", pos.getY());
		compound.setInteger("z", pos.getZ());
		return compound;
		
	}
	
	@Override
	public void handleUpdateTag(NBTTagCompound compound) {
		System.out.println(compound);
	}
	
	// getter
	
	public boolean isExtended() {
		return extended;
	}
	
	// Nbt
	
	@Override
	public void readNBT(NBTTagCompound compound) {
		ItemStackHelper.loadAllItems(compound, itemstacks);
		
		extended = compound.getBoolean("extended");
		
		NBTTagCompound ourBlocksTag = compound.getCompoundTag("ourBlocks");
		for (int i = 0; i < ourBlocks.length; i++) {
			if (ourBlocksTag.hasKey("" + i)) {
				ourBlocks[i] = ourBlocksTag.getBoolean("" + i);
			} else {
				ourBlocks[i] = false;
			}
		}
	}
	
	@Override
	public void writeNBT(NBTTagCompound compound) {
		ItemStackHelper.saveAllItems(compound, itemstacks);
		
		compound.setBoolean("extended", extended);
		
		NBTTagCompound ourBlocksTag = new NBTTagCompound();
		for (int i = 0; i < ourBlocks.length; i++) {
			ourBlocksTag.setBoolean("" + i, ourBlocks[i]);
		}
		compound.setTag("ourBlocks", ourBlocksTag);
	}
	
	// Inventory handling
	
	@Override
	public String getName() {
		return "drawbridge";
	}
	
	@Override
	public boolean hasCustomName() {
		return false;
	}
	
	@Override
	public int getSizeInventory() {
		return itemstacks.size();
	}
	
	@Override
	public boolean isEmpty() {
		for (ItemStack itemstack : itemstacks) {
			if (!itemstack.isEmpty()) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public ItemStack getStackInSlot(int index) {
		return itemstacks.get(index);
	}
	
	@Override
	public ItemStack decrStackSize(int index, int count) {
		return ItemStackHelper.getAndSplit(itemstacks, index, count);
	}
	
	@Override
	public ItemStack removeStackFromSlot(int index) {
		return ItemStackHelper.getAndRemove(itemstacks, index);
	}
	
	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		itemstacks.set(index, stack);
		if (stack.getCount() > this.getInventoryStackLimit()) {
			stack.setCount(this.getInventoryStackLimit());
		}
	}
	
	@Override
	public int getInventoryStackLimit() {
		return 1;
	}
	
	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return true;
	}
	
	@Override
	public void openInventory(EntityPlayer player) {
	}
	
	@Override
	public void closeInventory(EntityPlayer player) {
	}
	
	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return false;
	}
	
	@Override
	public int getField(int id) {
		return 0;
	}
	
	@Override
	public void setField(int id, int value) {
	}
	
	@Override
	public int getFieldCount() {
		return 0;
	}
	
	@Override
	public void clear() {
		itemstacks.clear();
	}
	
}

package info.u_team.draw_bridge.tileentity;

import java.util.*;
import java.util.stream.*;

import info.u_team.draw_bridge.block.DrawBridgeBlock;
import info.u_team.draw_bridge.container.DrawBridgeContainer;
import info.u_team.draw_bridge.init.*;
import info.u_team.draw_bridge.util.*;
import info.u_team.u_team_core.api.sync.IAutoSyncedTileEntity;
import info.u_team.u_team_core.container.USyncedTileEntityContainer;
import info.u_team.u_team_core.tileentity.UTileEntity;
import net.minecraft.block.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.*;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.client.model.data.*;
import net.minecraftforge.common.util.LazyOptional;

public class DrawBridgeTileEntity extends UTileEntity implements ITickableTileEntity, IAutoSyncedTileEntity {
	
	public static final ModelProperty<BlockState> BLOCKSTATE_PROPERTY = new ModelProperty<BlockState>();
	
	private final LazyOptional<InventoryStackHandler> slots = LazyOptional.of(() -> new SingleStackInventoryStackHandler(10));
	private final LazyOptional<InventoryStackHandler> renderSlot = LazyOptional.of(() -> new SingleStackInventoryStackHandler(1) {
		
		@Override
		public boolean isItemValid(int index, ItemStack stack) {
			final Item item = stack.getItem();
			if (!(item instanceof BlockItem) || item == DrawBridgeBlocks.DRAW_BRIDGE.asItem()) {
				return false;
			}
			final Block block = ((BlockItem) item).getBlock();
			return block.getDefaultState().isSolid();
		}
		
		@Override
		protected void onLoaded() {
			setRenderState();
		}
		
		@Override
		protected void slotChanged(int index) {
			if (!hasWorld() || world.isRemote()) {
				return;
			}
			setRenderState();
			sendChangesToClient();
		}
		
		private void setRenderState() {
			final ItemStack stack = getStackInSlot(0);
			
			if (stack.isEmpty()) {
				renderBlockState = null;
				return;
			}
			final Item item = stack.getItem();
			if (!(item instanceof BlockItem) || item == DrawBridgeBlocks.DRAW_BRIDGE.asItem()) {
				return;
			}
			renderBlockState = ((BlockItem) item).getBlock().getDefaultState();
		}
	});
	
	private boolean powered;
	private int speed;
	private boolean needRedstone = true;
	private int extendState;
	private boolean extended;
	private boolean[] ourBlocks = new boolean[10];
	
	private int localSpeed;
	
	private BlockState renderBlockState;
	
	public DrawBridgeTileEntity() {
		super(DrawBridgeTileEntityTypes.DRAW_BRIDGE);
	}
	
	// Neighbor update
	
	public void neighborChanged() {
		final boolean newPowered = world.isBlockPowered(pos);
		updatePoweredState(newPowered);
		
		final Set<DrawBridgeTileEntity> drawBridges = new HashSet<>();
		collect(drawBridges, this, 0);
		
		final boolean newPoweredState = drawBridges.stream().anyMatch(drawBridge -> world.isBlockPowered(drawBridge.pos)) | newPowered;
		drawBridges.stream().forEach(drawBridge -> drawBridge.updatePoweredState(newPoweredState));
	}
	
	// Drawbridge logic
	
	private void updatePoweredState(boolean newPowered) {
		powered = needRedstone ? newPowered : !newPowered;
	}
	
	private void collect(Set<DrawBridgeTileEntity> tileEntites, DrawBridgeTileEntity callerTileEntity, int depth) {
		if (depth >= 20) {
			return;
		}
		getNeighbors(callerTileEntity.pos).stream().forEach(neighbor -> {
			final TileEntity tileEntity = world.getTileEntity(neighbor);
			if (!(tileEntity instanceof DrawBridgeTileEntity)) {
				return;
			}
			final DrawBridgeTileEntity drawBridge = (DrawBridgeTileEntity) tileEntity;
			
			if (tileEntites.add(drawBridge)) {
				drawBridge.collect(tileEntites, drawBridge, depth + 1);
			}
		});
	}
	
	private List<BlockPos> getNeighbors(BlockPos except) {
		return getPosExcept(pos, except).filter(pos -> world.getBlockState(pos).getBlock() == DrawBridgeBlocks.DRAW_BRIDGE).collect(Collectors.toList());
	}
	
	private Stream<BlockPos> getPosExcept(BlockPos start, BlockPos except) {
		return Stream.of(Direction.values()).map(start::offset).filter(pos -> !pos.equals(except));
	}
	
	@Override
	public void tick() {
		if (world.isRemote()) {
			return;
		}
		if (localSpeed <= 1) {
			localSpeed = speed;
			if (powered && extendState < 10) {
				if (localSpeed == 0) {
					for (int i = extendState; i < 10; i++) {
						extend();
					}
				} else {
					extend();
				}
				markDirty();
			} else if (!powered && extendState > 0) {
				if (localSpeed == 0) {
					for (int i = extendState; i > 0; i--) {
						retract();
					}
				} else {
					retract();
				}
				markDirty();
			}
		}
		localSpeed--;
	}
	
	private void extend() {
		Direction facing = world.getBlockState(pos).get(DrawBridgeBlock.FACING);
		trySetBlock(facing);
		extended = ++extendState > 0;
	}
	
	private void trySetBlock(Direction facing) {
		BlockPos newPos = pos.offset(facing, extendState + 1);
		if (slots.isPresent() && (world.isAirBlock(newPos) /* || world.getFluidState(newPos).getFluid() == Fluids.EMPTY */)) {
			slots.ifPresent(inventory -> {
				ItemStack itemstack = inventory.getStackInSlot(extendState);
				Block block = Block.getBlockFromItem(itemstack.getItem());
				world.setBlockState(newPos, block.getDefaultState(), 2);
				inventory.removeStackFromSlot(extendState);
				ourBlocks[extendState] = true;
			});
		} else {
			ourBlocks[extendState] = false;
		}
	}
	
	private void retract() {
		Direction facing = world.getBlockState(pos).get(DrawBridgeBlock.FACING);
		extended = --extendState > 0;
		tryRemoveBlock(facing);
	}
	
	private void tryRemoveBlock(Direction facing) {
		if (ourBlocks[extendState] && slots.isPresent()) {
			BlockPos newPos = pos.offset(facing, extendState + 1);
			if (!world.isAirBlock(newPos)) {
				slots.ifPresent(inventory -> {
					BlockState state = world.getBlockState(newPos);
					Block block = state.getBlock();
					
					ItemStack stack = new ItemStack(block);
					inventory.setInventorySlotContents(extendState, stack);
					
					world.setBlockState(newPos, Blocks.AIR.getDefaultState(), 2);
				});
			}
		}
	}
	
	// NBT
	
	@Override
	public void readNBT(CompoundNBT compound) {
		slots.ifPresent(inventory -> inventory.deserializeNBT(compound.getCompound("slots")));
		renderSlot.ifPresent(inventory -> inventory.deserializeNBT(compound.getCompound("render_slot")));
		
		powered = compound.getBoolean("powered");
		extendState = compound.getInt("extend");
		extended = extendState > 0;
		speed = compound.getInt("speed");
		needRedstone = compound.getBoolean("need_redstone");
		
		final CompoundNBT ourBlocksTag = compound.getCompound("our_blocks");
		for (int i = 0; i < ourBlocks.length; i++) {
			if (ourBlocksTag.contains("" + i)) {
				ourBlocks[i] = ourBlocksTag.getBoolean("" + i);
			} else {
				ourBlocks[i] = false;
			}
		}
	}
	
	@Override
	public void writeNBT(CompoundNBT compound) {
		slots.ifPresent(inventory -> compound.put("slots", inventory.serializeNBT()));
		renderSlot.ifPresent(inventory -> compound.put("render_slot", inventory.serializeNBT()));
		
		compound.putBoolean("powered", powered);
		compound.putInt("extend", extendState);
		compound.putInt("speed", speed);
		compound.putBoolean("need_redstone", needRedstone);
		
		final CompoundNBT ourBlocksTag = new CompoundNBT();
		for (int i = 0; i < ourBlocks.length; i++) {
			ourBlocksTag.putBoolean("" + i, ourBlocks[i]);
		}
		compound.put("our_blocks", ourBlocksTag);
	}
	
	// Container
	
	@Override
	public USyncedTileEntityContainer<?> createMenu(int id, PlayerInventory playerInventory, PlayerEntity player) {
		return new DrawBridgeContainer(id, playerInventory, this);
	}
	
	@Override
	public ITextComponent getDisplayName() {
		return new StringTextComponent("Draw Bridge"); // TODO language file
	}
	
	// Slot getter
	
	public LazyOptional<InventoryStackHandler> getSlots() {
		return slots;
	}
	
	public LazyOptional<InventoryStackHandler> getRenderSlot() {
		return renderSlot;
	}
	
	// Sync methods container
	
	@Override
	public void sendToClient(PacketBuffer buffer) {
		buffer.writeBoolean(extended);
		buffer.writeVarInt(speed);
		buffer.writeBoolean(needRedstone);
	}
	
	@OnlyIn(Dist.CLIENT)
	@Override
	public void handleFromServer(PacketBuffer buffer) {
		extended = buffer.readBoolean();
		speed = buffer.readVarInt();
		needRedstone = buffer.readBoolean();
	}
	
	@OnlyIn(Dist.CLIENT)
	@Override
	public void sendToServer(PacketBuffer buffer) {
		buffer.writeVarInt(speed);
		buffer.writeBoolean(needRedstone);
	}
	
	@Override
	public void handleFromClient(PacketBuffer buffer) {
		speed = buffer.readVarInt();
		needRedstone = buffer.readBoolean();
		neighborChanged();
	}
	
	// Getter and setter
	
	public boolean isExtended() {
		return extended;
	}
	
	public int getSpeed() {
		return speed;
	}
	
	public void setSpeed(int speed) {
		this.speed = speed;
	}
	
	public boolean isNeedRedstone() {
		return needRedstone;
	}
	
	public void setNeedRedstone(boolean needRedstone) {
		this.needRedstone = needRedstone;
	}
	
	public boolean hasRenderBlockState() {
		return renderBlockState != null;
	}
	
	public BlockState getRenderBlockState() {
		return renderBlockState;
	}
	
	// Util methods for render block
	
	private void writeRenderState(CompoundNBT compound) {
		if (renderBlockState != null) {
			compound.put("render", NBTUtil.writeBlockState(renderBlockState));
		}
	}
	
	private void readRenderState(CompoundNBT compound) {
		if (compound.contains("render")) {
			renderBlockState = NBTUtil.readBlockState(compound.getCompound("render"));
		} else {
			renderBlockState = null;
		}
	}
	
	// Sync methods chunk
	
	@Override
	public void sendChunkLoadData(CompoundNBT compound) {
		writeRenderState(compound);
	}
	
	@Override
	public void handleChunkLoadData(CompoundNBT compound) {
		readRenderState(compound);
	}
	
	@Override
	public void sendUpdateStateData(CompoundNBT compound) {
		writeRenderState(compound);
	}
	
	@Override
	public void handleUpdateStateData(CompoundNBT compound) {
		readRenderState(compound);
		requestModelDataUpdate();
		world.notifyBlockUpdate(pos, getBlockState(), getBlockState(), 0);
	}
	
	// Model data
	
	@Override
	public IModelData getModelData() {
		if (renderBlockState != null) {
			return new ModelDataMap.Builder().withInitial(BLOCKSTATE_PROPERTY, renderBlockState).build();
		}
		return EmptyModelData.INSTANCE;
	}
	
	// Send update tag even if tag is empty
	
	@Override
	public SUpdateTileEntityPacket getUpdatePacket() {
		final CompoundNBT compound = new CompoundNBT();
		sendUpdateStateData(compound);
		return new SUpdateTileEntityPacket(pos, -1, compound);
	}
	
}
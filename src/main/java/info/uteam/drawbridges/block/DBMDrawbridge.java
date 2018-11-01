/*-*****************************************************************************
 * Copyright 2018 U-Team
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package info.uteam.drawbridges.block;

import info.u_team.u_team_core.block.UBlockTileEntity;
import info.u_team.u_team_core.tileentity.UTileEntityProvider;
import info.uteam.drawbridges.DBMConstants;
import info.uteam.drawbridges.container.DBMDrawbridgeContainer;
import info.uteam.drawbridges.gui.DBMDrawbridgeGui;
import info.uteam.drawbridges.init.*;
import info.uteam.drawbridges.tiles.DBMDrawbridgeTile;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.property.*;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/**
 * @author MrTroble
 *
 */
public class DBMDrawbridge extends UBlockTileEntity {

	public static final PropertyDirection FACING = PropertyDirection.create("facing");
	public static final PropertyBool COSTUM = PropertyBool.create("costum");
	protected int gui;

	/**
	 * @param name
	 * @param materialIn
	 */
	public DBMDrawbridge(String name, Material materialIn) {
		super(name, materialIn, DBMCreativeTabs.dbm_tab, new UTileEntityProvider(
				new ResourceLocation(DBMConstants.MODID, "draw_bridge"), true, DBMDrawbridgeTile.class));
		gui = DBMGuis.addContainer(DBMDrawbridgeContainer.class);
		if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
			DBMGuis.addGuiContainer(DBMDrawbridgeGui.class, gui);
		}
		setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH).withProperty(COSTUM, false));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.minecraft.block.Block#onBlockActivated(net.minecraft.world.World,
	 * net.minecraft.util.math.BlockPos, net.minecraft.block.state.IBlockState,
	 * net.minecraft.entity.player.EntityPlayer, net.minecraft.util.EnumHand,
	 * net.minecraft.util.EnumFacing, float, float, float)
	 */
	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
			EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		playerIn.openGui(DBMConstants.MODID, gui, worldIn, pos.getX(), pos.getY(), pos.getZ());
		return true;
	}

	private static final PropertyItemStack STACKS = PropertyItemStack.create();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see net.minecraft.block.Block#getExtendedState(net.minecraft.block.state.
	 * IBlockState, net.minecraft.world.IBlockAccess,
	 * net.minecraft.util.math.BlockPos)
	 */
	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
		TileEntity ent = world.getTileEntity(pos);
		if (ent instanceof DBMDrawbridgeTile) {
			DBMDrawbridgeTile dbt = (DBMDrawbridgeTile) ent;
			return ((IExtendedBlockState)(new ExtendedBlockState(this, new IProperty[] { FACING, COSTUM }, new IUnlistedProperty[] { STACKS })
					.getBaseState())).withProperty(STACKS, dbt.getRender());
		}
		return state;
	}

	static class PropertyItemStack implements IUnlistedProperty<ItemStack>{

		/**
		 * 
		 */
		private PropertyItemStack() {
			// TODO Auto-generated constructor stub
		}
		
		public static PropertyItemStack create() {
			return new PropertyItemStack();
		}
		
		/* (non-Javadoc)
		 * @see net.minecraftforge.common.property.IUnlistedProperty#getName()
		 */
		@Override
		public String getName() {
			return "itemstacks";
		}

		/* (non-Javadoc)
		 * @see net.minecraftforge.common.property.IUnlistedProperty#isValid(java.lang.Object)
		 */
		@Override
		public boolean isValid(ItemStack value) {
			return true;
		}

		/* (non-Javadoc)
		 * @see net.minecraftforge.common.property.IUnlistedProperty#getType()
		 */
		@Override
		public Class<ItemStack> getType() {
			return ItemStack.class;
		}

		/* (non-Javadoc)
		 * @see net.minecraftforge.common.property.IUnlistedProperty#valueToString(java.lang.Object)
		 */
		@Override
		public String valueToString(ItemStack value) {
			String str = value.getItem().getRegistryName().toString() + "[" + value.getMetadata() + "]";
			return str;
		}
		
	}
	
	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer) {
		return getDefaultState().withProperty(FACING, facing).withProperty(COSTUM, false);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)).withProperty(COSTUM, false);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(FACING).getIndex();
	}

	@Override
	public IBlockState withRotation(IBlockState state, Rotation rotation) {
		return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))).withProperty(COSTUM, false);
	}

	@Override
	public IBlockState withMirror(IBlockState state, Mirror mirror) {
		return state.withRotation(mirror.toRotation(state.getValue(FACING))).withProperty(COSTUM, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.minecraft.block.Block#shouldSideBeRendered(net.minecraft.block.state.
	 * IBlockState, net.minecraft.world.IBlockAccess,
	 * net.minecraft.util.math.BlockPos, net.minecraft.util.EnumFacing)
	 */
	@SuppressWarnings("deprecation")
	@Override
	public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos,
			EnumFacing side) {
		TileEntity ent = blockAccess.getTileEntity(pos);
		if (ent != null && ent instanceof DBMDrawbridgeTile) {
			DBMDrawbridgeTile tile = (DBMDrawbridgeTile) ent;
			return !tile.hasRender();
		}
		return super.shouldSideBeRendered(blockState, blockAccess, pos, side);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.minecraft.block.Block#isBlockNormalCube(net.minecraft.block.state.
	 * IBlockState)
	 */
	@Override
	public boolean isBlockNormalCube(IBlockState state) {
		return false;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, new IProperty[] { FACING, COSTUM });
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see info.u_team.u_team_core.block.UBlock#registerModel()
	 */
	@Override
	public void registerModel() {
		super.registerModel();
	}

}

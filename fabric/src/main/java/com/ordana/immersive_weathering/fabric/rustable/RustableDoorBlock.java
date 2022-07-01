package com.ordana.immersive_weathering.fabric.rustable;

import com.ordana.immersive_weathering.blocks.rustable.Rustable;
import com.ordana.immersive_weathering.configs.CommonConfigs;
import com.ordana.immersive_weathering.reg.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;

import java.util.Random;

public class RustableDoorBlock extends DoorBlock implements Rustable {
    private final RustLevel rustLevel;

    public RustableDoorBlock(RustLevel rustLevel, Properties settings) {
        super(settings);
        this.rustLevel = rustLevel;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf doubleBlockHalf = state.getValue(HALF);
        if ((direction == Direction.UP && doubleBlockHalf == DoubleBlockHalf.LOWER) ||
                (direction == Direction.DOWN && doubleBlockHalf == DoubleBlockHalf.UPPER)) {
            if (neighborState.getBlock() instanceof RustableDoorBlock) {
                state = neighborState.getBlock().withPropertiesOf(state);
            }
        }
        if (direction.getAxis() == Direction.Axis.Y && doubleBlockHalf == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            return neighborState.is(state.getBlock()) && neighborState.getValue(HALF) != doubleBlockHalf ? state.setValue(FACING, neighborState.getValue(FACING)).setValue(OPEN, neighborState.getValue(OPEN)).setValue(HINGE, neighborState.getValue(HINGE)).setValue(POWERED, neighborState.getValue(POWERED)) : Blocks.AIR.defaultBlockState();
        } else {
            return doubleBlockHalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : state;
        }
    }

    public void playSound(Level world, BlockPos pos, boolean open) {
        world.levelEvent(null, open ? this.getOpenSound() : this.getCloseSound(), pos, 0);
    }

    private int getOpenSound() {
        return this.material == Material.METAL ? 1011 : 1012;
    }

    private int getCloseSound() {
        return this.material == Material.METAL ? 1005 : 1006;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        boolean hasPower = world.hasNeighborSignal(pos) || world.hasNeighborSignal(pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
        if (hasPower != state.getValue(POWERED) && this != block) { // checks if redstone input has changed. only reacts to non door blocks

            switch (this.getAge()) {
                case UNAFFECTED -> {
                    if (hasPower != state.getValue(OPEN)) {
                        this.playSound(world, pos, hasPower);
                        world.gameEvent(hasPower ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
                    }
                    world.setBlock(pos, state.setValue(POWERED, hasPower).setValue(OPEN, hasPower), 2);
                }
                case EXPOSED -> {
                    if (hasPower) { // if the door is now being powered, open right away
                        world.scheduleTick(pos, this, 1); // 1-tick
                    } else {
                        world.scheduleTick(pos, this, 10); // 0.5 second
                    }
                    world.setBlock(pos, state.setValue(POWERED, hasPower), Block.UPDATE_CLIENTS);
                }
                case WEATHERED -> {
                    if (hasPower) { // if the door is now being powered, open right away
                        world.scheduleTick(pos, this, 1); // 1-tick
                    } else {
                        world.scheduleTick(pos, this, 20); // 1 second
                    }
                    world.setBlock(pos, state.setValue(POWERED, hasPower), Block.UPDATE_CLIENTS);
                }
                case RUSTED -> {
                    if (hasPower && !state.getValue(POWERED)) { // if its recieving power but the blockstate says unpowered, that means it has just been powered on this tick
                        state = state.cycle(OPEN);
                        this.playSound(world, pos, state.getValue(OPEN));
                        world.gameEvent(state.getValue(OPEN) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
                    }
                    world.setBlock(pos, state.setValue(POWERED, hasPower).setValue(OPEN, state.getValue(OPEN)), Block.UPDATE_CLIENTS);

                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random random) {
        if (this.getAge() == RustLevel.EXPOSED || this.getAge() == RustLevel.WEATHERED) {
            state = state.cycle(OPEN);
            this.playSound(world, pos, state.getValue(OPEN)); // if it is powered, play open sound, else play close sound
            world.gameEvent(state.getValue(OPEN) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos); // same principle here
            world.setBlock(pos, state.setValue(OPEN, state.getValue(OPEN)), Block.UPDATE_CLIENTS); // set open to match the powered state (powered true, open true)
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, Random random) {
        if (CommonConfigs.RUSTING.get()) {
            if (world.getBlockState(pos).is(ModTags.CLEAN_IRON)) {
                for (Direction direction : Direction.values()) {
                    var targetPos = pos.relative(direction);
                    BlockState neighborState = world.getBlockState(targetPos);
                    if (world.getBlockState(pos.relative(direction)).is(Blocks.AIR) || neighborState.getFluidState().getType() == Fluids.FLOWING_WATER || neighborState.getFluidState().getType() == Fluids.WATER) {
                        this.onRandomTick(state, world, pos, random);
                    }
                    if (world.getBlockState(pos.relative(direction)).is(Blocks.BUBBLE_COLUMN)) {
                        float f = 0.06f;
                        if (random.nextFloat() > 0.06f) {
                            this.applyChangeOverTime(state, world, pos, random);
                        }
                    }
                }
            }
            if (world.getBlockState(pos).is(ModTags.EXPOSED_IRON)) {
                for (Direction direction : Direction.values()) {
                    var targetPos = pos.relative(direction);
                    BlockState neighborState = world.getBlockState(targetPos);
                    if (world.isRainingAt(pos.above()) || neighborState.getFluidState().getType() == Fluids.FLOWING_WATER || neighborState.getFluidState().getType() == Fluids.WATER) {
                        this.onRandomTick(state, world, pos, random);
                    }
                    if (world.getBlockState(pos.relative(direction)).is(Blocks.BUBBLE_COLUMN)) {
                        float f = 0.06f;
                        if (random.nextFloat() > 0.06f) {
                            this.applyChangeOverTime(state, world, pos, random);
                        }
                    }
                    if (world.isRainingAt(pos.relative(direction)) && world.getBlockState(pos.above()).is(ModTags.WEATHERED_IRON)) {
                        if (BlockPos.withinManhattanStream(pos, 2, 2, 2)
                                .map(world::getBlockState)
                                .filter(b -> b.is(ModTags.WEATHERED_IRON))
                                .toList().size() <= 9) {
                            float f = 0.06f;
                            if (random.nextFloat() > 0.06f) {
                                this.applyChangeOverTime(state, world, pos, random);
                            }
                        }
                    }
                }
            }
            if (world.getBlockState(pos).is(ModTags.WEATHERED_IRON)) {
                for (Direction direction : Direction.values()) {
                    var targetPos = pos.relative(direction);
                    BlockState neighborState = world.getBlockState(targetPos);
                    if (neighborState.getFluidState().getType() == Fluids.WATER || neighborState.getFluidState().getType() == Fluids.FLOWING_WATER) {
                        this.onRandomTick(state, world, pos, random);
                    }
                    if (world.getBlockState(pos.relative(direction)).is(Blocks.BUBBLE_COLUMN)) {
                        if (random.nextFloat() > 0.07f) {
                            this.applyChangeOverTime(state, world, pos, random);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return Rustable.getIncreasedRustBlock(state.getBlock()).isPresent();
    }

    @Override
    public RustLevel getAge() {
        return this.rustLevel;
    }

}

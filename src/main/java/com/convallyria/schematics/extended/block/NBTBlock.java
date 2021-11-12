package com.convallyria.schematics.extended.block;

import net.minecraft.nbt.NBTTagCompound;

import com.convallyria.schematics.extended.WrongIdException;

import org.bukkit.block.BlockState;
import org.bukkit.util.Vector;

public abstract class NBTBlock {
    
    private final NBTTagCompound nbtTag;
    
    public NBTBlock(NBTTagCompound nbtTag) {
        this.nbtTag = nbtTag;
    }

    public NBTTagCompound getNbtTag() {
        return nbtTag;
    }

    public Vector getOffset() {
        NBTTagCompound compound = this.getNbtTag();
        int[] pos = compound.getIntArray("Pos");
        return new Vector(pos[0], pos[1], pos[2]);
    }

    public abstract void setData(BlockState state) throws WrongIdException;

    public abstract boolean isEmpty();
}
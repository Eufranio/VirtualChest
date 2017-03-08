package com.github.ustc_zzzz.virtualchest.unsafe;

import com.google.common.base.Throwables;
import org.spongepowered.api.item.inventory.Slot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author ustc_zzzz
 */
public class SpongeUnimplemented
{
    private static final Class<?> SLOT_ADAPTER_CLASS;
    private static final MethodHandle GET_ORDINAL;

    public static int getSlotOrdinal(Slot slot)
    {
        if (!SLOT_ADAPTER_CLASS.isInstance(slot))
        {
            throw new UnsupportedOperationException("Not recognized");
        }
        try
        {
            return (int) GET_ORDINAL.invoke(slot);
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    static
    {
        try
        {
            MethodType getOrdinalMethodType = MethodType.methodType(int.class);
            SLOT_ADAPTER_CLASS = Class.forName("org.spongepowered.common.item.inventory.adapter.impl.slots.SlotAdapter");
            GET_ORDINAL = MethodHandles.publicLookup().findVirtual(SLOT_ADAPTER_CLASS, "getOrdinal", getOrdinalMethodType);
        }
        catch (ReflectiveOperationException e)
        {
            throw Throwables.propagate(e);
        }
    }

    private SpongeUnimplemented()
    {
    }
}
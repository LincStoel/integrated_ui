package com.lincstoel.integratedui.compat.curios;

import com.lincstoel.integratedui.compat.EquipmentBackend;
import com.lincstoel.integratedui.compat.EquipmentGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CuriosBackend implements EquipmentBackend
{
    @Override
    public List<EquipmentGroup> collectGroups(Player player)
    {
        List<EquipmentGroup> groups = new ArrayList<>();
        getCurios(player).forEach((id, stacks) -> {
            int size = stacks.getStacks().getSlots();
            if(stacks.isVisible() && size > 0)
            {
                groups.add(new EquipmentGroup(id, size));
            }
        });
        return groups;
    }

    @Override
    public void addSlots(Player player, List<EquipmentGroup> groups, Consumer<Slot> adder)
    {
        Map<String, ICurioStacksHandler> curios = getCurios(player);
        for(EquipmentGroup group : groups)
        {
            ICurioStacksHandler stacks = curios.get(group.id());
            for(int i = 0; i < group.size(); i++)
            {
                if(stacks != null && i < stacks.getStacks().getSlots())
                {
                    adder.accept(new CurioSlot(player, stacks.getStacks(), i, group.id(), 0, 0, stacks.getRenders(), stacks.getActiveStates(), stacks.canToggleRendering(), false, false));
                }
                else
                {
                    adder.accept(new DisabledSlot());
                }
            }
        }
    }

    private static Map<String, ICurioStacksHandler> getCurios(Player player)
    {
        return CuriosApi.getCuriosInventory(player).map(ICuriosItemHandler::getCurios).orElse(Map.of());
    }

    @Override
    public Component getSlotName(Slot slot)
    {
        if(slot instanceof CurioSlot curioSlot)
        {
            return Component.literal(curioSlot.getSlotName());
        }
        return null;
    }

    @Override
    public Component getPanelName()
    {
        return Component.translatable("integrated_ui.gui.curios");
    }

    @Override
    public boolean hasFullScreen()
    {
        return true;
    }

    @Override
    public boolean openFullScreen()
    {
        return CuriosClientAccess.openScreen();
    }
}

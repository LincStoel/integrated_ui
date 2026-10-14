package com.lincstoel.integratedui.compat.ftbquests;

import com.lincstoel.integratedui.compat.CompatGuard;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;

/**
 * Opens the FTB Quests quest book from the button next to the equipment panel (see EquipmentPanel). Only used when
 * FTB Quests is installed.
 */
public final class FtbQuestsCompat
{
    private FtbQuestsCompat() {}

    /** Opens the quest book as its key does, which does nothing if the quests haven't been synced from the server yet. */
    public static void openQuestBook()
    {
        CompatGuard.run(CompatGuard.FTB_QUESTS, () -> ClientQuestFile.openGui());
    }
}

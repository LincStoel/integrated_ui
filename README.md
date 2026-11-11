# Integrated UI

A NeoForge mod for Minecraft 1.21.1 that adds backpacks, carried container items (shulker boxes, sacks, and similar) and Curios/Accessories slots to the survival inventory screen.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.214 or later
- Required on both the client and the server, except for the client-only integrations listed under [Optional dependencies](#optional-dependencies).

## Features

### Integrated inventory screen

- Replaces the vanilla survival inventory screen.
- Keeps the 2x2 crafting grid, armor slots, offhand slot, recipe book and status effects. Other mods still see it as the player's inventory menu.
- Adds a **band** of extra slots between the crafting area and the main inventory to display held containers and backpacks.
- Adds an **equipment panel** on the left for Curios or Accessories slots.
- Status effects move right to make room for the band's scroll tab. They switch to compact mode or are hidden when there isn't enough space.

### The band

- Shows one of the following at a time:
  1. The **Mount** page, while the player rides something with equipment or an inventory (see below).
  2. Equipped Backpacked backpacks, in Backpacked's order.
  3. A Quark backpack worn in the chest slot.
  4. Container items in the player's inventory. These are grouped by kind (shulker boxes before sacks), then by color (undyed first, then in dye order), then by position in the inventory.
  5. The **Bundles** page, while the player carries any bundles (see below).
- Previous/next buttons and a position counter (such as `2/5`) appear in the band header when there is more than one thing to show.
- Keybinds for previous/next: **Page Up** and **Page Down** by default. They work only inside the inventory screen and can be rebound under the "Integrated UI" category.
- The band remembers what each player last looked at and shows it again the next time they open their inventory, including after respawning or changing dimension.
- Grids taller than the visible row limit scroll vertically, using the mouse wheel or the scrollbar tab on the right edge of the window. Grids wider than 9 columns scroll horizontally, using Shift + mouse wheel or the scrollbar.
- The number of visible rows is set in the client config (1 to 11, default 3). Fewer rows are shown if the screen is too short.
- The band is colored to match what it shows:
  - Shulker boxes and other dyed items use their dye color, taken from the `c:dyed/<color>` tags.
  - Some undyed items use a fixed color; other undyed items use the color of their block.
  - Backpacked backpacks use Backpacked's sprites.
  - The Quark backpack uses Quark's leather texture.
- **Rename button** in the header: edits the name in place. A blank name removes the custom name, and names are limited to 50 characters.
- **Open button** in the header: opens the item in its own full screen (Backpacked's backpack screen, or the container item's screen).

### Container items

- A container item is any item that stores its contents in the vanilla container data component, places a container block, and keeps its contents when that block is broken. This includes vanilla shulker boxes, Supplementaries sacks, and any other mod's items built the same way.
- Items that use the container data component but don't place a container block (such as the guns from Iron's Arms 'n Artifice, which store their attachments in it) are not container items, since their size and what they accept are unknown. They can still be added by using the config item list as a whitelist.
- The following are never shown in the band:
  - Items with a vanilla lock
  - Items not on the config item list while it is a whitelist (the default), or on it while it is a blacklist
  - Items in the `integrated_ui:band_excluded` tag (by default, the Supplementaries safe)
  - Items with no more than the configured minimum number of slots (default: 1)
- The config item list (item ids and `#tags`) is a whitelist by default, holding shulker boxes and Supplementaries sacks, so only those are shown. Turned into a blacklist, every container item found is shown except those on it.
- While the item is on the cursor or in the crafting grid (for example, while dyeing it), its slots stay visible but are locked and shaded. If the item leaves the inventory, the band moves on to the next container.

### Bundles page

- Has one slot for each bundle the player carries: in the main inventory, hotbar and offhand (in that order), then in Curios/Accessories slots. It is at most 9 slots wide, scrolls by rows, and is left out of the band while the player carries no bundles.
- A bundle is any item that stores its contents in the vanilla bundle contents data component: vanilla bundles (an experimental feature in 1.21.1) and other mods' items built the same way. Some other mods' bags work too:
  - Quark's seed pouch and Create Diesel Generators' track layers bag, which each hold one item in bulk. Their contents show as one stack of the whole amount, and a click takes out at most a full stack.
  - Items in the `integrated_ui:item_handler_bundles` tag, used through their item handler: Ars Elemental's caster bag and curio bag by default. Datapacks can add other items whose contents are reachable through NeoForge's item handler capability. Their tooltip on the page lists their contents like a bundle's does.
- Items in the `integrated_ui:band_excluded` tag are left out.
- Each slot shows the bundle's selected item, with the bundle drawn small in the top right corner. An empty bundle is shown as itself.
- Scroll the mouse wheel over a slot to select another item in the bundle. Hovering shows the bundle's usual tooltip, with the selected item outlined. The slot the bundle is really in (in the inventory or an equipment slot) glows while its page slot is hovered.
- With an empty cursor, left-click takes the selected stack out of the bundle and right-click takes half of it. Shift-click moves it into the inventory, and Q drops one (Ctrl+Q the whole stack).
- With a stack on the cursor, left-click puts as much of it as fits into the bundle and right-click puts in one.
- When a bundle is moved, picked up or added, the page is rebuilt to match.

### Mount page

- While riding a mount that has its own inventory screen, the inventory key opens the integrated inventory instead, with the mount's equipment and storage on the first page of the band. This includes horses, donkeys, mules, llamas, camels, skeleton and zombie horses, chest boats, Deep Aether's and Altered Aether's aerwhales, and other mods' mounts built the same way.
- The page shows the mount's equipment slots (saddle, armor or carpet) on the left, a small portrait of the mount, and its storage on the right. Only slots the mount can use are shown: a camel has only a saddle, a llama only its carpet, and a donkey's chest slots appear only once it has a chest. The page never scrolls.
- The slots behave exactly as in the mount's own screen, because they are that screen's own slots. Shift-clicking a saddle or horse armor from the inventory puts it on the mount first, as in the mount's own screen.
- The header button opens the mount's own screen. Closing that screen returns to the inventory. The rename button is hidden; mounts are named with a name tag.
- The page is left out when the player dismounts, and when the mount's own screen would close (for example, when a donkey's chest is taken off). The band then shows the next page.
- The band remembers the last page viewed: the Mount page is shown again while riding if it was the last page looked at, and the page viewed before it is shown again after dismounting.
- Riding an untamed horse opens the integrated inventory without a Mount page, where vanilla opens nothing. Mounts left out by the config (see `mountList`) keep their own screen.
- Mounts that open their own screen are supported without any setup, as long as they use vanilla's horse screen, the vanilla container-entity menu, or a registered menu type.

### Opening container items in their own screen

- Right-clicking a container item in the player's inventory with an empty cursor opens it in its own screen. This works in any container screen that shows the player's inventory (a chest's, for example) but not in the creative inventory.
- Only items in the `integrated_ui:opens_own_screen` tag can be opened this way. By default the tag contains `#c:shulker_boxes` and `#supplementaries:sacks`.
- Each item opens in one of these screens:
  - Shulker boxes: the vanilla shulker box screen.
  - Sacks: Supplementaries' sack screen.
  - Other items: a vanilla chest screen with as many rows as the item needs.
- The item's own inventory slot is locked and shaded while its screen is open, so it can't be moved, dropped or swapped away. The screen closes if the item is lost anyway.
- Closing the screen returns to the screen it was opened from: the inventory, or another container such as a chest. That container stays open in the meantime (a chest keeps its lid open).
- Switching screens is seamless: the world never shows in between and the cursor stays where it is. The same applies to Create filters and Ars Nouveau spell books, which open the same way.
- In the inventory, shulker boxes open in Integrated UI's screen instead of Quark's held shulker box screen. Turn `compat.overrideQuarkShulkerBoxes` off in the client config to leave them to Quark.

### Equipment panel (Curios / Accessories)

- A collapsible, scrollable panel of Curios or Accessories slots on the left of the inventory window.
- The show/hide toggle remembers its state across sessions.
- Hovering an empty slot shows the slot's name.
- With Accessories, a button shows or hides unused slots. This uses Accessories' own per-player setting.
- If both Curios and Accessories are installed, Accessories is used, since its compatibility layer already holds the Curios slots.

### Robustness

- Calls into other mods' internals go through a guard. If a mod update renames a class, method or field Integrated UI relies on, that mod's integration is turned off for the rest of the session and one error is logged. Integrated UI then behaves as if that mod weren't installed.
- The server sends the equipment slot layout to the client, so both sides always build the same slots.

## Optional dependencies

None of these are required. Each integration can be turned off in the config, which makes Integrated UI behave as if the mod weren't installed.

| Mod | Minimum version | Server/Client | What the integration does |
|---|---|---------------|---|
| [Backpacked](https://www.curseforge.com/minecraft/mc-mods/backpacked) (requires Framework) | 3.0.5 | Both          | Equipped backpacks are shown in the band, using Backpacked's slot unlocking (lock overlay, hold-to-unlock, drag selection, cost tooltips, particles). Unlocking from the inventory costs and stores the same as from Backpacked's screen, and Backpacked's "unlockable slot mode" option is respected. The header has a button to open Backpacked's full screen, drawn as the backpack item, and closing that screen returns to the inventory. Backpacks can be renamed from the band. |
| [Quark](https://www.curseforge.com/minecraft/mc-mods/quark) (requires Zeta) | 4.1-485 | Both          | The worn Quark backpack (if enabled in Quark's config) is shown in the band instead of in Quark's backpack screen, drawn with Quark's leather texture. It can be renamed from the band. Quark's player inventory buttons and keybinds (sorting and others) work on the integrated inventory, with space left above the main inventory for Quark's sort button. Seed pouches are shown on the Bundles page. When the Quark integration is off, a worn Quark backpack is left to Quark's own screen. |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | 9.5.1 | Both          | Curios slots appear in the equipment panel, with a button to open Curios' own screen. The menu reopens when slot counts change. |
| [Accessories](https://www.curseforge.com/minecraft/mc-mods/accessories) | 1.1.0-beta.53 | Both          | Accessories slots appear in the equipment panel, matching Accessories' own screen, with the unused-slots toggle and a button to open Accessories' screen. Accessories takes precedence over Curios. |
| [Supplementaries](https://www.curseforge.com/minecraft/mc-mods/supplementaries) | 1.21.1-3.9.9 | Both          | Sacks open in Supplementaries' own sack screen, with the sack's slot locked, and closing it returns to the inventory. Sacks are also shown in the band through the general container item support. With this integration off, sacks are treated like any other container item. The safe is excluded from the band by default. |
| [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) | 5.13.2 | Both          | Right-clicking a spell book in the inventory with an empty cursor opens its spell crafting screen, from any container screen. The book is swapped into the main hand while the screen is open and swapped back when it closes or the player logs out. Closing the book returns to the screen it was opened from (the inventory or a container). |
| [Create](https://www.curseforge.com/minecraft/mc-mods/create) | 6.0.10 | Both          | Right-clicking a filter (list, attribute or package filter) in the inventory with an empty cursor opens its screen, from any container screen, and its tooltip says so. The filter is swapped into the main hand while the screen is open and swapped back when it closes or the player logs out. Closing the filter returns to the screen it was opened from (the inventory or a container). |
| [Create Diesel Generators](https://modrinth.com/mod/create-diesel-generators) | 1.21.1-1.3.15 | Both          | Track layers bags are shown on the Bundles page. |
| [Ars Elemental](https://www.curseforge.com/minecraft/mc-mods/ars-elemental) | Any | Both          | The caster bag and curio bag are shown on the Bundles page, through the `integrated_ui:item_handler_bundles` tag rather than code (so there is no config option; a datapack can take them out of the tag). Items in Ars Elemental's `blacklist_bag_item` tag can't be put in. |
| [The Aether](https://www.curseforge.com/minecraft/mc-mods/aether) | 1.5.10 | Both          | Adds the Aether's accessories button (the cloud on the player portrait) to the integrated inventory, at the position set in the Aether's client config, and following the window when the recipe book moves it. If the Aether uses its own accessories menu instead of Accessories' default one, the equipment panel is left out. |
| [Field Guide](https://modrinth.com/mod/field-guide) | 1.21.0+1.21.1 | Client        | Adds a small button that opens the Field Guide to the column of buttons beside the equipment panel, below the Curios/Accessories button (or in its place without either mod). As with Field Guide's own inventory button, it does nothing if the guide can't be opened yet, such as when Field Guide's server config requires the item. |
| [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | 2101.1.36 | Client        | Adds a small button that opens the quest book to the same column, below the Field Guide button (or in its place without Field Guide). |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | 19.51.0 | Client        | JEI's overlays stay clear of the band tab, equipment panel and status effects. JEI's "+" button fills the 2x2 crafting grid, taking ingredients from the inventory and from the band (except from locked slots). The recipe book button is hidden, since JEI replaces it. A button left of the equipment panel shows/hides JEI, and turns grey while it is hidden. |
| [Mouse Tweaks](https://www.curseforge.com/minecraft/mc-mods/mouse-tweaks) | 2.26.1 | Client        | Mouse Tweaks is told which slots are visible, so it never moves items into band rows scrolled out of view, a collapsed equipment panel, or hidden slots. It fills slots in this order: inventory, band, crafting grid, equipment. When Mouse Tweaks' wheel tweak is on, the mouse wheel over a slot moves items, and the band and panel scroll from their edges instead. Over the Bundles page the wheel still picks the bundle's item, and Mouse Tweaks leaves those slots alone. |
| [Vercte's Satchels](https://modrinth.com/mod/vercte-satchels) | 1.2.0 | Both          | Compatible without any setup: Satchels' equip slot and satchel row stay below the hotbar and move down with the inventory when the band is open, and the band's slots are unaffected. There is no config option, since Integrated UI doesn't call into Satchels. |

## Configuration

### Client (`config/integrated_ui-client.toml`)

| Option | Default | Description |
|---|---|---|
| `replaceInventoryScreen` | `true` | Use the integrated inventory in place of the vanilla inventory screen. |
| `maxVisibleRows` | `3` | Maximum band rows shown at once (1 to 11). Larger grids scroll. |
| `equipmentPanelOpen` | `true` | Whether the equipment panel is open. Updated automatically when toggled in game. |
| `rightClickOpensSpellBooks` | `true` | Right-clicking an Ars Nouveau spell book opens it. |
| `rightClickOpensContainers` | `true` | Right-clicking a container item opens it in its own screen. |
| `rightClickOpensFilters` | `true` | Right-clicking a Create filter opens it. |
| `compat.jei` | `true` | JEI integration. |
| `compat.aether` | `true` | Aether accessories button. |
| `compat.fieldGuide` | `true` | Field Guide button. |
| `compat.ftbQuests` | `true` | FTB Quests quest book button. |
| `compat.overrideQuarkShulkerBoxes` | `true` | Open shulker boxes in the inventory in Integrated UI's screen rather than Quark's. |

### Server (`serverconfig/integrated_ui-server.toml`, per world, synced to clients)

Changes take effect the next time the inventory is opened.

| Option | Default | Description |
|---|---|---|
| `features.containerItemsInBand` | `true` | Show carried container items in the band. |
| `features.containerItemListIsWhitelist` | `true` | `true`: `containerItemList` is a whitelist, and only the container items on it are shown (the `band_excluded` tag and the minimum slot count are ignored). `false`: it is a blacklist, and every container item found is shown except those on it. Backpacks are not affected. |
| `features.containerItemList` | `["#c:shulker_boxes", "#supplementaries:sacks"]` | Item ids, or item tags prefixed with `#`. Items left out aren't shown in the band or opened in their own screen. The server log lists every container item found, and whether it is shown, when a world loads. |
| `features.ignoreContainerItemsUpToSlots` | `1` | Container items with this many slots or fewer are not shown in the band and can't be opened in their own screen. `0` includes all of them. Doesn't apply to whitelisted items. |
| `features.containerItemScreens` | `true` | Allow container items to be opened in their own screen. |
| `features.bundlesInBand` | `true` | Show the Bundles page in the band. |
| `features.mountsInBand` | `true` | Show the Mount page while riding. |
| `features.mountListIsWhitelist` | `false` | `true`: `mountList` is a whitelist, and only the mounts on it get the Mount page (the `mount_excluded` tag is ignored). `false`: it is a blacklist, and every mount gets one except those on it or in the tag. Mounts left out keep their own screen. |
| `features.mountList` | `[]` | Entity type ids, or entity type tags prefixed with `#`. |
| `features.renaming` | `true` | Show the rename button in the band header. |
| `features.equipmentSlots` | `true` | Show the Curios/Accessories equipment panel. |
| `compat.backpacked` | `true` | Backpacked integration. |
| `compat.quark` | `true` | Quark integration. |
| `compat.curios` | `true` | Curios integration. |
| `compat.accessories` | `true` | Accessories integration. |
| `compat.supplementaries` | `true` | Open sacks in Supplementaries' screen. |
| `compat.arsNouveau` | `true` | Ars Nouveau spell book integration. |
| `compat.create` | `true` | Create filter integration. |
| `compat.dieselGenerators` | `true` | Create Diesel Generators track layers bags on the Bundles page. |

Every option has a translated name, so config screen mods such as Configured show readable labels.

### Tags

| Tag | Default contents | Purpose |
|---|---|---|
| `integrated_ui:band_excluded` | `supplementaries:safe` | Container items never shown in the band. |
| `integrated_ui:opens_own_screen` | `#c:shulker_boxes`, `#supplementaries:sacks` | Container items that can be opened in their own screen. |
| `integrated_ui:mount_excluded` (entity type) | empty | Mounts never shown in the band while `mountList` is a blacklist. |

All tags can be extended with datapacks.

## License

GPL-3.0-only. See [LICENSE](LICENSE).

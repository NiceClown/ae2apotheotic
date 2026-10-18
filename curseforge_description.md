# AE2 Apotheotic Automation

An addon for **Applied Energistics 2** that connects AE2 autocrafting directly to **Apotheosis** machines:

* **Gem Case:** Automates gem purity upgrading (2 gems + materials -> 1 gem of the next purity tier) and crushing gems into dust.
* **Raven's Enchanting Table:** Automates infusion crafting (special items such as new bookshelves or Infused Dragon's Breath). Standard enchantments are **not** automated by this addon.

---

## Requirements & Compatibility

* **Environment:** Minecraft 26.1.2, NeoForge 26.1.2.x
* **Required Mods:**
  * Applied Energistics 2 (`26.1.13-beta+`)
  * Apotheosis (`9.1.0+`)
  * Apothic Enchanting (`2.1.0+`)
  * Required dependencies: Placebo, Apothic Attributes (`3.1.0+`), GuideME
* **Optional Mods:** Just Dire Things (supported for liquid experience storage)

> **Note for ATM11 Players:** All the Mods 11 currently ships with Apotheosis 9.0.3 and Apothic Enchanting 2.0.0. The Gem Case uses a different API in those versions, so before installing this addon, please update them to **Apotheosis 9.1.0**, **Apothic Enchanting 2.1.0**, and **Apothic Attributes 3.1.0**.

---

## In-Game Guidebook (GuideME)

The addon includes full in-game documentation powered by **GuideME**:
* Open the **AE2 Guide** (`ae2:guide`) to access the **"Apotheotic Automation"** chapter with live 3D models, setups, and recipes;
* Click the **`?`** button in the top-right corner of any machine GUI to open its manual page immediately;
* Hover over any block or pattern in inventory or JEI/REI and press **`G`** to jump directly to its guide;
* Fully translated in **English** and **Russian**.

---

## Blocks

| Block | Visual Style | Capacity | Purpose |
| :--- | :--- | :--- | :--- |
| **Apotheotic Pattern Encoder** | Industrial AE2 terminal chassis with cyan screen and violet Fluix runes | 1 Sample, 1 Blank, 1 Output | Encodes specialized patterns of all three types |
| **Apotheotic Pattern Provider (Gems)** | AE2 machine casing with brass gem sockets (Ruby, Sapphire, Emerald, Amethyst) | Categorized UI (Tiers I–V + Dust per gem) | Placed directly adjacent to a Gem Case |
| **Apotheotic Pattern Provider (Infusion)** | AE2 machine casing with violet runes and illuminated liquid XP tube | 9 Pattern Slots | Placed directly adjacent to an Enchanting Table (Raven's) |

The Pattern Providers act as standard ME network nodes: they require an ME channel, can be connected via cable from any side, appear in the Pattern Access Terminal, and hold specialized pattern cards (categorized per gem for Gems, 9 slots for Infusion). Addon patterns only work inside their respective specialized providers (a standard ME Pattern Provider will simply ignore them).

---

## Pattern Encoder

The encoder features a **Sample** slot on the left, a slot for **AE2 Blank Patterns** on the bottom-left, and an **Encoded Pattern** output slot on the bottom-right. Modes are switched via tabs. Clicking the **"Encode"** button consumes one Blank Pattern (or overwrites a pattern currently placed in the output slot).

### 1. Gem Upgrading (Gem Case)
1. Insert any gem of the desired type into the sample slot. Use the arrows to select the starting purity tier (defaults to the purity of the sample gem).
2. If the upgrade recipe offers multiple material options (for example, 3 Arcane Shards **or** 1 Tattered Cloth), use the arrows below the materials to choose your preferred option.
3. Click **"Encode"** to create a pattern for `2x Gem + Materials -> 1x Next Purity Gem`.
4. **Shift + "Encode":** Automatically generates the entire upgrade chain all the way up to **Perfect** purity (consuming one Blank Pattern per tier and placing the encoded patterns into your inventory; subsequent tiers automatically use the first material option). With the full chain loaded into the provider, AE2 will automatically calculate how many Cracked gems, gem dust, and materials are needed to craft a Perfect gem.

The provider inserts two gems and the required materials **directly into the Gem Case**, triggers its native upgrade operation, extracts the result, and sends it back into the ME network. Because of this, make sure that:
* The Gem Case is placed directly adjacent to the provider;
* There is enough room in its 6 material slots (each material stack is inserted into a single slot);
* The gem count does not exceed the storage limit of the Gem Case.

### 2. Gem Dust (Crushing)
Crushes gems into gem dust, providing the guaranteed minimum yield matching Apotheosis salvaging recipes (Cracked/Chipped: 1, Flawed: 2, Normal: 3, Flawless: 4, Perfect: 5 dust). Available modes:
* **"Only this gem"** — matches the exact gem type and purity of the sample;
* **"Any — <Purity>"** — matches any gem type of the selected purity tier (very convenient for Cracked gems);
* **"Any gem"** — matches any gem regardless of type or purity (use with caution: AE2 may crush valuable gems).

Crushing is performed internally by the Gem Pattern Provider itself — a Gem Case is not required for this operation.

### 3. Infusion (Raven's Enchanting Table)
1. Insert the item to be infused (for example, Dragon's Breath or a Hellshelf).
2. If multiple infusion recipes exist for the item, select the desired one using the arrows.
3. Adjust **Eterna / Quanta / Arcana** using the `-` / `+` buttons (hold **Shift** to adjust by 10). The **"Auto"** button sets the minimum stats required for the recipe. The valid stat ranges and XP cost are displayed next to the controls; the encoder verifies that the table will produce the exact selected recipe at these values and that Eterna does not exceed your maximum limit.
4. Click **"Encode"** to create a pattern for `Item + 3x Lapis Lazuli -> Result`.

When paired with a **Raven's Enchanting Table**, the provider applies the stat values stored directly in the pattern. When paired with any other **Apothic Enchanting** table, the stats are read from the bookshelves surrounding the table — meaning the pattern will only execute if the surrounding shelves provide matching stats.

#### Experience Handling
The XP cost is calculated the same way as at an Enchanting Table (third slot, `level = max(ceil(recipe Eterna), 3)`; for Infused Dragon's Breath, this equals 1,631 XP points). Experience is consumed in the following order:
1. Directly from the connected ME network's fluid storage (`c:experience` / `neoforge:experience`) using ME network energy;
2. From the provider's internal XP buffer (managed via the **"+1 Lvl" / "+All" / "Take"** buttons in its GUI, or filled via pipes with liquid XP);
3. Extracted as liquid experience (`20 mB = 1 XP point`) from adjacent fluid tanks — such as an **Experience Holder** from **Just Dire Things** placed directly against the provider.

If there is not enough experience available, the crafting job waits until enough XP is supplied. The pattern itself does not track fluids — all XP calculations are handled automatically by the provider.

---

## Crafting Recipes

* **Apotheotic Pattern Encoder:** 1x AE2 Blank Pattern, 2x Gem Dust, 1x Crafting Table, 1x Engineering Processor.
* **Apotheotic Pattern Provider (Gems):** 1x ME Pattern Provider, 1x Anvil, 2x Gem Dust, 1x Logic Processor.
* **Apotheotic Pattern Provider (Infusion):** 1x ME Pattern Provider, 1x Enchanting Table, 2x Lapis Lazuli Blocks, 1x Engineering Processor.

---

## Configuration (`serverconfig/ae2apotheotic-server.toml`)

* `infusion.requiresExperience` — whether infusion requires experience (enabled by default);
* `infusion.experienceMultiplier` — multiplier applied to the XP cost;
* `infusion.bufferCapacity` — capacity of the internal XP buffer (in XP points).
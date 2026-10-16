# AE2 Apotheotic Automation

An addon for **Applied Energistics 2** that connects AE2 autocrafting directly to **Apotheosis** and **Apothic Enchanting** mechanics:

* **Gem Case:** Automates gem purity upgrading (2 gems + materials → 1 gem of the next purity tier) and crushing gems into gem dust.
* **Raven's Enchanting Table:** Automates infusion crafting (special items such as custom bookshelves or Infused Dragon's Breath). Standard item enchantments are **not** automated by this addon.

---

## Requirements & Compatibility

* **Environment:** Minecraft 26.1.2, NeoForge 26.1.2.x
* **Required Dependencies:**
  * **Applied Energistics 2** (`26.1.13-beta+`)
  * **Apotheosis** (`9.1.0+`)
  * **Apothic Enchanting** (`2.1.0+`)
  * **Apothic Attributes** (`3.1.0+`)
  * **Placebo** (`10.0.2+`)
  * **GuideME** (`26.1.15-beta+`)
* **Optional Integrations:**
  * **Just Dire Things** (supported for adjacent liquid experience storage via the Experience Holder)

> [!IMPORTANT]
> **Notice for All the Mods 11 (ATM11) Players:**
> Earlier ATM11 versions may ship with Apotheosis 9.0.3 and Apothic Enchanting 2.0.0. The Gem Case uses a different internal API in those versions. Make sure to update to **Apotheosis 9.1.0+**, **Apothic Enchanting 2.1.0+**, and **Apothic Attributes 3.1.0+**.

---

## Blocks & Devices

| Block | Visual Style | Capacity | Purpose |
| :--- | :--- | :--- | :--- |
| **Apotheotic Pattern Encoder** | Industrial AE2 terminal chassis with cyan screen and violet Fluix runes | 1 Sample, 1 Blank, 1 Output | Encodes specialized autocrafting patterns of all three types |
| **Apotheotic Gem Pattern Provider** | AE2 machine casing with brass gem sockets (Ruby, Sapphire, Emerald, Amethyst) | 27 Pattern Slots (9x3 grid) | Connects directly to an adjacent Gem Case |
| **Apotheotic Infusion Pattern Provider** | AE2 machine casing with violet runes and illuminated liquid XP tube | 9 Pattern Slots | Connects directly to an adjacent Enchanting Table |

Pattern Providers act as full ME network nodes:
* Require 1 ME channel.
* Can be connected by cable or placed against dense/normal cables and other ME blocks on any side.
* Pattern slots are directly visible and accessible through the **ME Pattern Access Terminal**.
* Addon patterns function exclusively in their designated providers (standard AE2 ME Pattern Providers will ignore them).

---

## Pattern Encoder

The **Apotheotic Pattern Encoder** interface features a 210px layout designed to accommodate long localized item names and complex crafting recipes:
* **Sample Slot** (top-left): Place a gem or infusion base item.
* **AE2 Blank Patterns Slot** (bottom-left): Supplies blank patterns.
* **Encoded Pattern Output Slot** (bottom-right): Holds the encoded output card.
* **Encode Button**: Encodes one blank pattern (or overwrites a pattern in the output slot).

### 1. Gem Upgrading (Gem Case)
1. Insert any gem of the target type into the sample slot. Use the navigation buttons to select the starting purity tier (defaults to the sample's purity).
2. If the cutting recipe has multiple material choices (e.g. 3 Arcane Shards **or** 1 Tattered Cloth), use the selector buttons below the materials to choose your variant.
3. Click **Encode** to write a pattern: `2x Gem + Materials → 1x Next Purity Gem`.
4. **Shift + Encode:** Automatically generates the entire upgrade chain up to **Perfect** purity (consuming one blank pattern per step and sending the resulting patterns straight to your inventory). When the full chain is placed into a Gem Pattern Provider, the ME network can calculate the exact amounts of Cracked gems, dust, and materials needed to craft a Perfect gem on demand.

The Gem Pattern Provider automatically places the two gems and materials directly into the adjacent Gem Case, executes the native upgrade operation, extracts the upgraded gem, and deposits it into the ME network.

### 2. Gem Dust (Crushing)
Crushes gems into gem dust. Autocrafting guarantees the minimum threshold matching native Apotheosis salvaging recipes:
* **Cracked:** 1 Gem Dust
* **Chipped:** 1 Gem Dust
* **Flawed:** 2 Gem Dust
* **Normal:** 3 Gem Dust
* **Flawless:** 4 Gem Dust
* **Perfect:** 5 Gem Dust

Supported filter modes:
* **"Only this gem"**: Matches the exact gem type and purity of the sample.
* **"Any — <Purity>"**: Matches any gem type of the selected purity tier (ideal for crushing surplus Cracked gems).
* **"Any gem"**: Matches any gem regardless of type or purity (use with caution).

Crushing is performed internally by the Gem Pattern Provider — a Gem Case is not required for crushing operations.

### 3. Infusion (Raven's Enchanting Table)
1. Place the item to be infused (e.g. Dragon's Breath, Bookshelf) into the sample slot.
2. Select the target recipe with the navigation buttons if multiple recipes exist.
3. Adjust **Eterna**, **Quanta**, and **Arcana** using `-` and `+` (hold **Shift** for ±10).
4. Click **Auto** to automatically populate the exact minimum requirements for the recipe.
5. Click **Encode** to write the pattern: `Item + 3x Lapis Lazuli → Result`.

* **With Raven's Enchanting Table:** The provider applies the exact stat values stored inside the encoded pattern.
* **With Standard Apothic Enchanting Tables:** Stats are determined by bookshelves surrounding the table. The crafting job executes only if surrounding shelves meet or exceed the required stats.

#### Experience Handling
The experience cost matches table enchanting (`level = max(ceil(recipe Eterna), 3)`). XP is consumed automatically in this order:
1. Internal XP buffer of the provider (configurable capacity, default 10,000 pts). Manageable in the GUI via **"+1 lvl"**, **"+All"**, and **"Take"**, or supplied via fluid pipes.
2. Adjacent fluid tanks holding liquid experience (`c:experience`, 20 mB = 1 XP point) — such as the **Experience Holder** from **Just Dire Things**.

---

## Crafting Recipes

* **Apotheotic Pattern Encoder:** 1x AE2 Blank Pattern, 2x Gem Dust, 1x Crafting Table, 1x Engineering Processor.
* **Apotheotic Gem Pattern Provider:** 1x ME Pattern Provider, 1x Anvil, 2x Gem Dust, 1x Logic Processor.
* **Apotheotic Infusion Pattern Provider:** 1x ME Pattern Provider, 1x Enchanting Table, 2x Lapis Lazuli Blocks, 1x Engineering Processor.

---

## Configuration (`serverconfig/ae2apotheotic-server.toml`)

* `infusion.requiresExperience`: Whether infusion requires XP (default: `true`).
* `infusion.experienceMultiplier`: Multiplier applied to the XP cost (default: `1.0`).
* `infusion.bufferCapacity`: Capacity of the internal XP buffer in points (default: `10000`).

---

## Building

### Quick Build (No Gradle Required)
Using Git Bash and Prism Launcher dependencies:
```bash
./build-local.sh
```
Compiles against JDK 25 and NeoForge/Minecraft libraries, outputting `build/libs/ae2apotheotic-1.1.0.jar` and automatically updating local Prism instances.

### Gradle Build
Standard ModDevGradle configuration is included in `build.gradle` and `settings.gradle`.

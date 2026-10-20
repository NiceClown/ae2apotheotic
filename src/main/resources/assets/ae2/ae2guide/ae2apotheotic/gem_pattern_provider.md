---
navigation:
  parent: ae2apotheotic/index.md
  title: Gem Pattern Provider
  icon: ae2apotheotic:gem_pattern_provider
  position: 20
categories:
- machines
item_ids:
- ae2apotheotic:gem_pattern_provider
---

# Apotheotic Gem Pattern Provider

<Row gap="20">
  <BlockImage id="ae2apotheotic:gem_pattern_provider" scale="8" />
</Row>

The <ItemLink id="ae2apotheotic:gem_pattern_provider" /> connects your ME network directly to the **Apotheosis Gem Case**, automating purity upgrades and gem crushing without any manual intervention.

Like standard ME Pattern Providers, it functions as a full ME network device: it requires 1 channel, connects via cable or direct block face contact from any side, and announces all loaded patterns to connected Crafting CPUs.

<br clear="all" />

## Physical Setup

1. Place the **Apotheotic Gem Pattern Provider** directly adjacent to an Apotheosis **Gem Case** (touching any face).
2. Connect the provider to your ME network using an ME Cable.
3. The provider GUI will display **"Gem Case: connected"** in green once recognized.

<br clear="all" />

![Gem Pattern Provider Setup](assets/gem_provider_setup.png)

<br clear="all" />

## How Autocrafting Works

When an autocrafting order is placed through your ME terminals:
1. **Gem Upgrades:** The provider pushes the two input gems and the required materials directly into the internal inventory of the Gem Case. It triggers the native cutting upgrade operation, retrieves the newly created higher-purity gem, and deposits it into the ME network.
2. **Gem Crushing:** Gems are pulverized into **Gem Dust** according to the encoded ratio. Crushing is performed internally within the provider itself and does not require a Gem Case to be attached.

> [!NOTE]
> Make sure the adjacent Gem Case has sufficient storage space and that its material slots are not clogged with leftover items.

<br clear="all" />

## The Categorized Interface

### 1. Search and Gem Grid
* The search box filters gems by name; right-click it to clear.
* **All / In use** switches between all gems and only the gems that already have patterns.
* The grid shows **Bulk Crushing** first and then every registered gem (9 × 3, scroll with the mouse wheel or the scrollbar).
* The bar at the bottom of each icon shows how many of its slots are filled (green = all of them).
* The small indicators at the top right show the network (**N**), the Gem Case (**G**) and an info note (**i**).

### 2. Gem Category
Each gem has six slots, shown below its name:
* **I:** Cracked → Chipped
* **II:** Chipped → Flawed
* **III:** Flawed → Normal
* **IV:** Normal → Flawless
* **V:** Flawless → Perfect
* **Dust:** crushing exactly this gem ("This gem" crushing patterns).

Empty slots show a faint ghost of what they produce. Steps below the gem's minimum purity do not exist and are crossed out.

### 3. Bulk Crushing
27 free slots for any crushing pattern (exact gem, any gem of a purity, or any gem).

### 4. Smart Sorting (Shift-Click)
Shift-click a pattern in your inventory and the provider puts it into its slot and opens that category.
Upgrade patterns go to their gem, "This gem" crushing patterns to that gem's Dust slot (or Bulk Crushing if it is taken),
all other crushing patterns to Bulk Crushing.

> [!NOTE]
> Because patterns are sorted by gem, this provider is **not** shown in the ME Pattern Access Terminal.
> Patterns are stored by gem id, so adding or removing gems never moves them around.

<br clear="all" />

## Crafting Recipe

<RecipeFor id="ae2apotheotic:gem_pattern_provider" />

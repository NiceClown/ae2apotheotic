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

Like standard ME Pattern Providers, it functions as a full ME network device: it requires 1 channel, connects via cable or direct block face contact from any side, and announces all loaded patterns to connected Crafting CPUs and the ME Pattern Access Terminal.

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

To make managing hundreds of gem recipes enjoyable and compact, the Gem Pattern Provider features an interactive categorized interface:

### 1. Top Gem Selector Strip
* Displays 8 gem icons per page with navigation arrows (`<` and `>`) and a page counter (`1/3`).
* The currently active gem is highlighted with a cyan border.
* Badges in the corner of each gem icon indicate installed patterns (green badge when all upgrade tiers are filled).

### 2. Category Header Row
* Displays the current gem's full-size icon, localized name, installed tier count (e.g. `4 / 5 tiers`), and cycling arrows (`<` and `>`) for stepping through gems sequentially.

### 3. Purity Tier Slots
Six dedicated slots are provided for each gem:
* **Tier I:** Cracked → Chipped
* **Tier II:** Chipped → Flawed
* **Tier III:** Flawed → Normal
* **Tier IV:** Normal → Flawless
* **Tier V:** Flawless → Perfect
* **Tier VI (Dust):** Dedicated slot for crushing this specific gem into dust.

Empty slots display subtle ghost labels (`I`, `II`, `III`, `IV`, `V`, `Dust`) and rich tooltips upon hovering.

### 4. Category 0: General Crushing
Page 1 starts with **Category 0 (General Crushing)**, offering 6 slots dedicated to bulk and purity-based gem crushing patterns (e.g. "Any Cracked Gem → 1 Dust").

### 5. Smart Pattern Auto-Routing (Shift-Click)
When you hold **Shift** and click any encoded pattern from your player inventory:
* The provider automatically inspects the pattern, identifies its gem type and purity tier, and deposits it directly into its designated slot.
* The interface immediately flips the active view to that gem!
* Strict slot filtering guarantees patterns can never be placed into mismatched slots, whether through the GUI or remotely via the ME Pattern Access Terminal.

<br clear="all" />

## Crafting Recipe

<RecipeFor id="ae2apotheotic:gem_pattern_provider" />

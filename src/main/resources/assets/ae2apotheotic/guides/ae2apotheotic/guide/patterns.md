---
navigation:
  parent: ae2apotheotic/index.md
  title: Specialized Patterns
  icon: ae2apotheotic:gem_upgrade_pattern
  position: 40
categories:
- machines
item_ids:
- ae2apotheotic:gem_upgrade_pattern
- ae2apotheotic:gem_crush_pattern
- ae2apotheotic:infusion_pattern
---

# Specialized Apotheotic Patterns

Standard AE2 processing patterns are designed for standard crafting operations and lack data fields for purity levels, cutting material alternatives, table enchantability stats, and liquid experience.

To solve this, **AE2 Apotheotic Automation** provides three dedicated pattern card types, encoded using the <ItemLink id="ae2apotheotic:pattern_encoder" />.

<br clear="all" />

## 1. Encoded Gem Upgrade Pattern

<Row gap="16">
  <ItemImage id="ae2apotheotic:gem_upgrade_pattern" scale="4" />
</Row>

* **Item:** <ItemLink id="ae2apotheotic:gem_upgrade_pattern" />
* **Used in:** <ItemLink id="ae2apotheotic:gem_pattern_provider" />
* **Contents:**
  * Target Gem type (e.g. *Brawling Gem*, *Wanderer's Gem*)
  * Purity transition: `from → to` (e.g. *Flawless → Perfect*)
  * Selected cutting materials (e.g. *3x Arcane Shards* or *1x Tattered Cloth*)
* **Crafting Behavior:** The Gem Pattern Provider automatically inserts the two lower-purity gems and required materials into an adjacent Gem Case, triggers cutting, and returns the upgraded gem into the ME network.

<br clear="all" />

## 2. Encoded Gem Crushing Pattern

<Row gap="16">
  <ItemImage id="ae2apotheotic:gem_crush_pattern" scale="4" />
</Row>

* **Item:** <ItemLink id="ae2apotheotic:gem_crush_pattern" />
* **Used in:** <ItemLink id="ae2apotheotic:gem_pattern_provider" />
* **Contents:**
  * Crushing filter mode: **"Only this gem"**, **"Any — <Purity>"**, or **"Any gem"**
  * Target gem purity tier
  * Guaranteed output dust quantity (1 to 5 Gem Dust)
* **Crafting Behavior:** Pulverizes gems directly into Gem Dust internally without requiring an external machine.

<br clear="all" />

## 3. Encoded Infusion Pattern

<Row gap="16">
  <ItemImage id="ae2apotheotic:infusion_pattern" scale="4" />
</Row>

* **Item:** <ItemLink id="ae2apotheotic:infusion_pattern" />
* **Used in:** <ItemLink id="ae2apotheotic:infusion_pattern_provider" />
* **Contents:**
  * Input item (e.g. *Dragon's Breath*, *Bookshelf*)
  * 3x Lapis Lazuli
  * Required table stats: **Eterna**, **Quanta**, and **Arcana**
  * Required liquid experience cost (mB)
  * Target infused item result
* **Crafting Behavior:** The Infusion Pattern Provider places ingredients onto the table, pays experience (from ME storage, internal buffer, or adjacent tanks), and collects the infused result.

<br clear="all" />

## Clearing & Reusing Patterns

To clear any encoded pattern card and recover the original **AE2 Blank Pattern**:
* **Shift + Right-Click** with the encoded pattern in your hand while looking at the air.
* Alternatively, place the pattern card into the output slot of the <ItemLink id="ae2apotheotic:pattern_encoder" /> to overwrite it directly with a new recipe.

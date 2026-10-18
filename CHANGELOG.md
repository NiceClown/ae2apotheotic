# Changelog

All notable changes to **AE2 Apotheotic Automation** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-10-18

### Added
* **Integrated GuideME In-Game Guidebook:**
  * Added complete, comprehensive in-game guidebook documentation powered by **GuideME** (the official AE2 guidebook engine).
  * Seamlessly integrated into the AE2 Guidebook (`ae2:guide`) with a dedicated top-level **"Apotheotic Automation"** chapter and cross-listings in the standard **Machines** category.
  * **Full English and Russian Localizations:** Every guide page is natively available in both English (`en_us`) and Russian (`ru_ru`), dynamically selected according to player language settings.
  * **Interactive 3D Block Models & Visual Illustrations:**
    * Live interactive 3D block model previews rendered on each page (`<BlockImage ... />`).
    * Clear isometric 3D render illustrations showing proper block placement, machine adjacencies, ME cable hookups, and automation pipelines.
    * Full crafting recipe previews (`<RecipeFor ... />`) and item tag links (`<ItemLink ... />`, `<ItemImage ... />`).
  * **Dedicated In-Game Guide Pages:**
    * **Overview (`index.md`):** Architecture, features, and quick navigation.
    * **Apotheotic Pattern Encoder (`pattern_encoder.md`):** Complete walkthrough of all 3 encoding modes (Gem Upgrades, Gem Crushing, Table Infusion), Shift+Encode chain generation, and infusion stat calculator.
    * **Apotheotic Gem Pattern Provider (`gem_pattern_provider.md`):** Physical setup with the Gem Case, the new categorized UI, purity tier slots, Category 0, and smart shift-click routing.
    * **Apotheotic Infusion Pattern Provider (`infusion_pattern_provider.md`):** Setup with Raven's vs standard tables, 3-tier liquid XP extraction priority (ME Network fluid storage -> Internal buffer -> Adjacent tanks), and atomic transactions.
    * **Specialized Patterns (`patterns.md`):** Deep dive into the three pattern cards, data payloads, clearing, and reuse.
  * **Interactive `?` Help Buttons in GUIs:**
    * Added a dedicated `?` button to the top-right corner of all machine screens (Encoder and Providers).
    * Clicking `?` opens the exact GuideME manual page for that machine directly.
    * Pressing the GuideME hotkey (`G`) while hovering over any block or pattern in inventories or JEI/REI immediately navigates to its guide page.

### Fixed
* **GuideME Image Format & Real In-Game Visuals:**
  * Fixed guide illustrations displaying as purple/black missing-texture placeholders caused by JPEG format incompatibility in Minecraft/NeoForge's `NativeImage` loader (which strictly requires PNG).
  * Replaced all conceptual art with real, authentic in-game screenshots captured via an automated test client run showing actual blocks, cables, tables, and lighting in working functional configurations.
  * Optimized all screenshot assets to lightweight high-resolution PNGs (~130–190 KB each), significantly reducing the compiled JAR file size while preserving crystal-clear pixel art quality.

---

## [1.2.0] - 2026-10-18

### Added
* **Categorized Gem Pattern Provider Interface:**
  * Replaced the flat pattern grid with an interactive categorized architecture organized by gem type.
  * **Top Gem Selector Strip:** Paginated scrollable strip displaying 8 gem icons per page with navigation arrows (`<`, `>`), page counter, and active gem highlight with progress indicator badges.
  * **Selected Gem Category View:** Dedicated header showing the current gem's icon, localized name, installed tier count, and prev/next cycling arrows.
  * **Purity Tier Slots (I to V + Dust):**
    * Tier I: Cracked → Chipped
    * Tier II: Chipped → Flawed
    * Tier III: Flawed → Normal
    * Tier IV: Normal → Flawless
    * Tier V: Flawless → Perfect
    * Tier VI (Dust): Dedicated slot for crushing this specific gem into Gem Dust.
  * **General Crushing Category:** Dedicated category 0 for general or purity-based gem crushing patterns.
  * **Slot-Level Validation:** Enforces strict pattern placement matching category gem and upgrade purity, both via GUI interactions and the ME Pattern Access Terminal.
  * **Smart Pattern Auto-Routing (Shift-Click):** Shift-clicking any encoded pattern from the player inventory automatically inspects its gem and tier, routes it into the exact designated slot, and instantly switches the category view to that gem.
* **Direct ME Network Experience (XP) Pulling for Infusion Provider:**
  * The **Apotheotic Infusion Pattern Provider** now prioritizes extracting liquid experience (`c:experience` / `neoforge:experience`) directly from the connected ME network's fluid storage first.
  * Strict 3-tier priority execution:
    1. **ME Network Storage (Primary):** Extracted directly using ME grid power and storage services.
    2. **Internal XP Buffer:** Fallback to the provider's internal buffer.
    3. **Adjacent Fluid Tanks:** Fallback to physical tanks (e.g. Just Dire Things Experience Holder) in an atomic transaction with zero XP leakage.
  * GUI status label and tooltips updated to display external XP (`XP external: %s`) and explain the network-first extraction priority.

---

## [1.1.0] - 2026-10-16

### Added
* **AE2-Style Industrial Block Textures & Concept Arts:**
  * Redesigned all machine textures to adhere strictly to the classic Applied Energistics 2 industrial aesthetic:
    * Standard brushed slate-steel chassis (`#4E5462`), authentic AE2 beveled metallic borders, and riveted corner chamfers.
    * **Apotheotic Pattern Encoder:** High-contrast cyan ME CRT terminal display, 3x3 pattern matrix, bottom pattern card slot with glowing insertion guide, and protected side Fluix cooling tubes with violet illumination.
    * **Apotheotic Gem Pattern Provider:** Standard AE2 machine casing, 5x5 glowing cyan ME pattern matrix, and 4 corner brass brackets with inlaid Apotheosis gems (ruby, sapphire, emerald, amethyst).
    * **Apotheotic Infusion Pattern Provider:** Standard AE2 3x3 glowing cyan pattern aperture, subtle violet runic inlays on steel bevels, and an inset vertical glass containment cylinder displaying luminous liquid experience (Liquid XP).
  * Distinctive item sprites for pattern cards:
    * `Encoded Gem Upgrade Pattern`: AE2 data card with circuit traces, sapphire gem, and golden purity upgrade chevrons (`>>`).
    * `Encoded Gem Crushing Pattern`: Titanium card chassis with a shattering crystal and rainbow Gem Dust sparkles.
    * `Encoded Infusion Pattern`: Purpur tech card with glowing magenta enchanting seal and luminous liquid XP essence droplet.
* **Expanded Gem Provider Capacity (27 Slots):**
  * Increased pattern storage in the **Gem Pattern Provider** from 9 to **27 slots** (arranged in a standard 9x3 inventory grid).
  * Fully integrated with ME Pattern Access Terminal, block drops, and NBT serialization.
* **Guaranteed Minimum Threshold for Gem Crushing:**
  * Automated crushing through the ME network now yields the guaranteed minimum threshold matching `min_count` from Apotheosis salvaging recipes (`apotheosis:salvaging/gem_dust`), ensuring autocrafting is never more profitable than manual salvaging at the Salvaging Table:
    * **Cracked:** 1 Gem Dust
    * **Chipped:** 1 Gem Dust
    * **Flawed:** 2 Gem Dust
    * **Normal:** 3 Gem Dust
    * **Flawless:** 4 Gem Dust
    * **Perfect:** 5 Gem Dust
  * Autocrafting patterns (`GemCrushPattern`), `GemProviderBlockEntity`, and `EncoderLogic` preview reflect and output these exact values.
  * Preview ratio text dynamically updates (`1 gem = 4 dust`).

### Fixed & Improved
* **Widened 210px Pattern Encoder Window & Restored Arrow:**
  * Widened the encoder interface from 176px to **210px** to provide ample horizontal space for long localized names and multi-step craft displays without text clipping.
  * Restored the full purity upgrade transition text with arrow: **`from → to`** (e.g. `Flawless → Perfect`), centered cleanly on the header row.
  * Repositioned navigation buttons (`<` at `x = 30`, `>` at `x = 186`) to guarantee a 4px margin from the sample slot and 7px padding from the text label.
  * In Infusion Mode, relocated the `Auto` button to `(x = 94, y = 90)` between XP cost and max Eterna, completely eliminating the 6px overlap with the preview result slot.
* **GUI Layout & Text Overlaps Across All Screens:**
  * Standardized GUI screen titles (`Pattern Encoder`, `Gem Pattern Provider`, `Infusion Pattern Provider`).
  * Fixed text collisions in Gem Provider by dedicating separate lines for network status (`y = 17`) and Gem Case connection (`y = 28`), adjusting the 27 pattern slots down to `y = 38..92`.
  * Dedicated a clean row (`y = 94`) for machine alert / problem text, setting the player inventory title to `y = 104`, completely eliminating text overlap in all machine states (connected, offline, missing case, full inventory).
  * Verified all GUI screens with rendered playtest screenshots.

---

## [1.0.0] - 2026-10-15

### Added
* Initial release of **AE2 Apotheotic Automation** for Minecraft 26.1.2 on NeoForge.
* **Apotheotic Pattern Encoder:**
  * Dedicated encoder for creating Apotheotic autocrafting patterns.
  * Gem Upgrade Mode: 2x gems + materials to produce the next purity tier. Supports Shift+Encode chain generation up to Perfect purity.
  * Gem Crushing Mode: Crush gems into dust (supports exact gem, same purity, or any gem).
  * Infusion Mode: Automates table infusion recipes with Eterna, Quanta, Arcana tuning and liquid XP handling.
* **Apotheotic Pattern Provider (Gems):**
  * Connects directly to adjacent Apotheosis Gem Cases to execute automated purity upgrades and crushing.
* **Apotheotic Pattern Provider (Infusion):**
  * Interfaces directly with adjacent Enchanting Tables (including Enchanting Table of the Raven) to execute infusion crafting.
  * Built-in experience buffer with support for pipe input and pulling liquid experience from neighboring tanks (such as Just Dire Things Experience Holder).
* Full English (`en_us`) and Russian (`ru_ru`) localization.

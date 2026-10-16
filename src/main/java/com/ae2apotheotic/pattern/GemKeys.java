package com.ae2apotheotic.pattern;

import appeng.api.stacks.AEItemKey;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Helpers to turn Apotheosis gems into AE2 keys. */
public final class GemKeys {
    private GemKeys() {}

    public static AEItemKey gem(Gem gem, Purity purity) {
        return AEItemKey.of(GemItem.createStack(gem, purity, 1));
    }

    public static AEItemKey dust() {
        return AEItemKey.of(Apoth.Items.GEM_DUST.value());
    }

    /** All gems registered right now, sorted by id so that the order is stable. */
    public static List<Gem> sortedGems() {
        List<Gem> gems = new ArrayList<>(GemRegistry.INSTANCE.getValues());
        gems.sort(Comparator.comparing(g -> String.valueOf(GemRegistry.INSTANCE.getKey(g))));
        return gems;
    }

    /**
     * Every gem key that exists, optionally restricted to a single purity. A gem whose minimum purity is above the
     * requested one is skipped, because such a gem item cannot exist.
     */
    public static List<AEItemKey> allGems(@Nullable Purity onlyPurity) {
        Set<AEItemKey> keys = new LinkedHashSet<>();
        for (Gem gem : sortedGems()) {
            for (Purity purity : Purity.values()) {
                if (!purity.isAtLeast(gem.getMinPurity())) {
                    continue;
                }
                if (onlyPurity != null && purity != onlyPurity) {
                    continue;
                }
                keys.add(gem(gem, purity));
            }
        }
        return List.copyOf(keys);
    }
}

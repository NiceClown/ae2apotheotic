package com.ae2apotheotic.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.Collection;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Pattern input that accepts any of several keys, {@link #multiplier} of them per craft.
 */
public final class SimpleInput implements IPatternDetails.IInput {
    private final GenericStack[] candidates;
    private final long multiplier;

    private SimpleInput(GenericStack[] candidates, long multiplier) {
        if (candidates.length == 0) {
            throw new IllegalArgumentException("A pattern input needs at least one candidate");
        }
        this.candidates = candidates;
        this.multiplier = multiplier;
    }

    public static SimpleInput of(AEKey key, long amount) {
        return new SimpleInput(new GenericStack[] { new GenericStack(key, 1) }, amount);
    }

    public static SimpleInput of(Collection<? extends AEKey> keys, long amount) {
        return new SimpleInput(keys.stream().map(k -> new GenericStack(k, 1)).toArray(GenericStack[]::new), amount);
    }

    public static SimpleInput of(MaterialChoice choice) {
        return of(choice.items(), choice.count());
    }

    @Override
    public GenericStack[] getPossibleInputs() {
        return candidates;
    }

    @Override
    public long getMultiplier() {
        return multiplier;
    }

    @Override
    public boolean isValid(AEKey input, Level level) {
        for (GenericStack candidate : candidates) {
            if (input.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable AEKey getRemainingKey(AEKey template) {
        return null;
    }
}

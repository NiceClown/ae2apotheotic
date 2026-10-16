package com.ae2apotheotic.pattern;

import appeng.api.stacks.AEItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * One concrete option of a gem cutting ingredient list: any of {@link #items} is accepted, {@link #count} of them are consumed.
 */
public record MaterialChoice(List<AEItemKey> items, int count) {
    public static final Codec<MaterialChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
            AEItemKey.CODEC.listOf().fieldOf("items").forGetter(MaterialChoice::items),
            Codec.INT.fieldOf("count").forGetter(MaterialChoice::count))
        .apply(i, MaterialChoice::new));

    public MaterialChoice {
        items = List.copyOf(items);
    }

    @SuppressWarnings("deprecation") // Ingredient#items() is still the only way to list the accepted items
    public static MaterialChoice of(SizedIngredient ingredient) {
        List<AEItemKey> keys = ingredient.ingredient().items()
            .map(h -> AEItemKey.of(h.value()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        return new MaterialChoice(keys, ingredient.count());
    }

    public boolean isEmpty() {
        return items.isEmpty() || count <= 0;
    }

    public ItemStack displayStack() {
        return items.isEmpty() ? ItemStack.EMPTY : items.getFirst().toStack(Math.min(count, 99));
    }
}

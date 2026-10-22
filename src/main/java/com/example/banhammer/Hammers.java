package com.example.banhammer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Bouwt de twee hamers. Beide zijn een mace; alleen de banhammer heeft een enchant-glans. */
public final class Hammers {
	private Hammers() {
	}

	public static ItemStack banhammer() {
		ItemStack stack = base("Banhammer", ChatFormatting.RED, "banhammer");
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static ItemStack kickhammer() {
		return base("Kickhammer", ChatFormatting.GOLD, "kickhammer");
	}

	private static ItemStack base(String name, ChatFormatting color, String tagKey) {
		ItemStack stack = new ItemStack(Items.MACE);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false)));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(tagKey, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}
}

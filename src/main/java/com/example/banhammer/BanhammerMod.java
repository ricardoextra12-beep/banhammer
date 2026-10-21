package com.example.banhammer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanhammerMod implements ModInitializer {
	public static final String MOD_ID = "banhammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String BAN_REASON = "Gebanned met de Banhammer";

	@Override
	public void onInitialize() {
		// Server-only: er wordt niets geregistreerd. De hamer is een gewoon item
		// (bijv. een mace) met custom_data {banhammer:1b}.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}
			if (!isBanhammer(attacker.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}

			MinecraftServer server = attacker.level().getServer();
			String targetName = target.getGameProfile().name();

			// /ban draait namens de aanvaller: alleen operators kunnen bannen
			server.getCommands().performPrefixedCommand(
					attacker.createCommandSourceStack(),
					"ban " + targetName + " " + BAN_REASON
			);

			attacker.sendSystemMessage(Component.literal("Banhammer: " + targetName + " is gebanned."));
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Banhammer (server-only) geladen!");
	}

	private static boolean isBanhammer(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains("banhammer");
	}
}

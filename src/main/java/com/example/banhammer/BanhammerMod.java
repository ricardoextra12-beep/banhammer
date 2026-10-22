package com.example.banhammer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.commands.Commands;
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
	private static final String KICK_REASON = "Gekicked met de Kickhammer";

	@Override
	public void onInitialize() {
		// /adminpanel: alleen zichtbaar en bruikbaar voor operators (en dus de owner)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("adminpanel")
						.requires(source -> source.getPlayer() != null && Ops.isOperator(source.getPlayer()))
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							if (!Ops.isOperator(player)) {
								return 0;
							}
							AdminPanel.openMain(player);
							return 1;
						})));

		// Hamers: gewone mace-items met custom_data {banhammer:1b} of {kickhammer:1b}
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}

			ItemStack held = attacker.getItemInHand(hand);
			boolean ban = hasTag(held, "banhammer");
			boolean kick = hasTag(held, "kickhammer");
			if (!ban && !kick) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}

			String targetName = target.getGameProfile().name();

			if (Ops.isOperator(target)) {
				attacker.sendSystemMessage(Component.literal(
						"Banhammer: " + targetName + " is een operator en kan niet worden " + (ban ? "gebanned." : "gekicked.")));
				return InteractionResult.SUCCESS;
			}

			MinecraftServer server = attacker.level().getServer();
			String command = ban
					? "ban " + targetName + " " + BAN_REASON
					: "kick " + targetName + " " + KICK_REASON;
			server.getCommands().performPrefixedCommand(attacker.createCommandSourceStack(), command);

			attacker.sendSystemMessage(Component.literal(
					"Banhammer: " + targetName + (ban ? " is gebanned." : " is gekicked.")));
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Banhammer, Kickhammer en Admin Panel (server-only) geladen!");
	}

	private static boolean hasTag(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(key);
	}
}

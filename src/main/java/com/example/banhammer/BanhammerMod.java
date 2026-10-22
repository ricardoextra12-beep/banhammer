package com.example.banhammer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanhammerMod implements ModInitializer {
	public static final String MOD_ID = "banhammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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

		// Shift + rechtsklik met een hamer: volgende reden (staat meteen in de naam van de hamer)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			boolean ban = Hammers.isHammer(held, true);
			boolean kick = Hammers.isHammer(held, false);
			if (!ban && !kick) {
				return InteractionResult.PASS;
			}
			if (!Ops.isOperator(sp)) {
				return InteractionResult.PASS;
			}
			Hammers.cycle(held, ban);
			sp.sendSystemMessage(Component.literal("Reden: " + Hammers.reason(held, ban)), true);
			return InteractionResult.SUCCESS;
		});

		// Slaan met een hamer: ban of kick met de gekozen reden
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}

			ItemStack held = attacker.getItemInHand(hand);
			boolean ban = Hammers.isHammer(held, true);
			boolean kick = Hammers.isHammer(held, false);
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

			String reason = Hammers.reason(held, ban);
			MinecraftServer server = attacker.level().getServer();
			String command = ban
					? "ban " + targetName + " " + reason
					: "kick " + targetName + " " + reason;
			server.getCommands().performPrefixedCommand(attacker.createCommandSourceStack(), command);

			attacker.sendSystemMessage(Component.literal(
					"Banhammer: " + targetName + (ban ? " is gebanned" : " is gekicked") + " (" + reason + ")."));
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Banhammer, Kickhammer en Admin Panel (server-only) geladen!");
	}
}

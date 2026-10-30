package com.platino;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PlatinoClient implements ClientModInitializer {
	public static final String MOD_ID = "cobbleplatino";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static KeyBinding notesKey;
	private static KeyBinding cardKey;
	private static KeyBinding poketchKey;
	private static KeyBinding bagKey;
	private static KeyBinding partyKey;
	private static double stepDistance;
	private static double lastX, lastZ;
	private static boolean hasLast;
	private static int unsavedSteps;

	@Override
	public void onInitializeClient() {
		PlatinoConfig.load();
		Notes.load();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ScreenRecolor());

		notesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.notes",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.cobbleplatino"));
		cardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.card",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.cobbleplatino"));
		poketchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.poketch",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.cobbleplatino"));
		bagKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.bag",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.cobbleplatino"));
		partyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cobbleplatino.party",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.cobbleplatino"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (partyKey.wasPressed()) {
				if (client.currentScreen == null) client.setScreen(new com.platino.summary.PartyScreen(null));
			}
			if (pendingParty > 0 && --pendingParty == 0) client.setScreen(new com.platino.summary.PartyScreen(null));
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (bagKey.wasPressed()) {
				if (client.currentScreen == null) client.setScreen(new BagScreen());
			}
			LowHpAlarm.tick(client);
			if (pendingUse > 0 && --pendingUse == 0 && client.currentScreen == null
					&& client.player != null && client.interactionManager != null) {
				client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
			}
			if (pendingBag > 0 && --pendingBag == 0) client.setScreen(new BagScreen());
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (poketchKey.wasPressed()) {
				if (client.currentScreen == null) client.setScreen(new PoketchScreen());
			}
			countSteps(client);
			if (pendingPoketch > 0 && --pendingPoketch == 0) client.setScreen(new PoketchScreen());
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (cardKey.wasPressed()) {
				if (client.currentScreen == null) openCard();
			}
			if (pendingCard > 0 && --pendingCard == 0) openCard();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (notesKey.wasPressed()) {
				if (client.currentScreen != null) continue;
				Pokemon p = selectedPartyPokemon();
				if (p != null) client.setScreen(new NotesScreen(null, p));
			}
		});

		// Botón "Notas" (dibujado y gestionado por nosotros) + tecla N dentro del Resumen
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof Summary summary)) return;
			int px = (w - Summary.BASE_WIDTH) / 2;
			int py = (h - Summary.BASE_HEIGHT) / 2;
			int bw = 62, bh = 14;
			int bx = px + Summary.BASE_WIDTH - bw - 4, by = py - bh - 1;

			ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> {
				if (button == 0 && mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
					openNotes(summary);
					return false;
				}
				return true;
			});
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, mods) -> {
				if (notesKey.matchesKey(key, scancode) && !(s.getFocused() instanceof TextFieldWidget)) {
					openNotes(summary);
					return false;
				}
				return true;
			});
			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0, 0, 500);
				boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
				Draw.panelGradient(ctx, bx, by, bx + bw, by + bh, Draw.OUTLINE,
						hover ? 0xFFF8E070 : 0xFFE8ECF0, hover ? Draw.GOLD : 0xFFB0B8C4);
				String label = "✎ Notas (N)";
				Draw.dsText(ctx, label, bx + (bw - Draw.width(label)) / 2, by + 3, Draw.TEXT_DARK, 0x80FFFFFF);
				ctx.getMatrices().pop();

				Pokemon p = summary.getSelectedPokemon$common();
				if (p == null || !Notes.has(p.getUuid())) return;
				drawNoteCard(ctx, Notes.get(p.getUuid()), px, py, Summary.BASE_WIDTH, Summary.BASE_HEIGHT, s.height);
			});
		});

		// /platino paleta <diamante|perla|platino|original>
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			var root = ClientCommandManager.literal("platino");
			var pal = ClientCommandManager.literal("paleta");
			for (String name : new String[]{"diamante", "perla", "platino", "original"}) {
				pal.then(ClientCommandManager.literal(name).executes(ctx -> {
					PlatinoConfig.get().palette = name;
					PlatinoConfig.get().screensTheme = !name.equals("original");
					PlatinoConfig.save();
					ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Paleta: " + name + " (recargando texturas…)"));
					ScreenRecolor.reapply();
					return 1;
				}));
			}
			var combate = ClientCommandManager.literal("combate");
			for (String part : new String[]{"menu", "texto", "pitido", "bolsa"}) {
				var node = ClientCommandManager.literal(part);
				for (String state : new String[]{"on", "off"}) {
					node.then(ClientCommandManager.literal(state).executes(ctx -> {
						boolean on = state.equals("on");
						if (part.equals("menu")) PlatinoConfig.get().battleMenu = on;
						else if (part.equals("texto")) PlatinoConfig.get().battleText = on;
						else if (part.equals("bolsa")) PlatinoConfig.get().battleBag = on;
						else PlatinoConfig.get().lowHpBeep = on;
						PlatinoConfig.save();
						ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] "
								+ (part.equals("menu") ? "Menú de combate DS" : part.equals("texto") ? "Cuadro de texto DS" : part.equals("bolsa") ? "Bolsa en combate" : "Pitido de vida baja")
								+ (on ? " ACTIVADO" : " DESACTIVADO")));
						return 1;
					}));
				}
				combate.then(node);
			}
			for (String side : new String[]{"izquierda", "derecha"}) {
				combate.then(ClientCommandManager.literal("posicion").then(ClientCommandManager.literal(side).executes(ctx -> {
					PlatinoConfig.get().battleMenuSide = side;
					PlatinoConfig.save();
					ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Menú de combate a la " + side));
					return 1;
				})));
			}
			combate.then(ClientCommandManager.literal("tamano").then(ClientCommandManager
					.argument("escala", FloatArgumentType.floatArg(0.5f, 1.5f)).executes(ctx -> {
						PlatinoConfig.get().battleMenuScale = FloatArgumentType.getFloat(ctx, "escala");
						PlatinoConfig.save();
						ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Tamaño del menú: "
								+ PlatinoConfig.get().battleMenuScale));
						return 1;
					})));
			root.then(combate);
			root.then(ClientCommandManager.literal("ficha").executes(ctx -> {
				// Se abre 2 ticks después, cuando el chat ya se ha cerrado
				pendingCard = 2;
				return 1;
			}));
			root.then(ClientCommandManager.literal("debug").then(ClientCommandManager.literal("marcador").executes(ctx -> {
				var lines = ServerStats.debugLines();
				ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Marcador DiosesMon: " + lines.size() + " líneas"));
				for (String l : lines) {
					ctx.getSource().sendFeedback(Text.literal(" · " + l));
					LOGGER.info("[Cobblemon Platino] marcador: {}", l);
				}
				ServerStats.update();
				ctx.getSource().sendFeedback(Text.literal("→ dinero=" + ServerStats.money + " coins=" + ServerStats.coins
						+ " puntos=" + ServerStats.points + " clan=" + ServerStats.clan));
				return 1;
			})));
			var resumen = ClientCommandManager.literal("resumen");
			for (String state : new String[]{"on", "off"}) {
				resumen.then(ClientCommandManager.literal(state).executes(ctx -> {
					PlatinoConfig.get().dppSummary = state.equals("on");
					PlatinoConfig.save();
					ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Ficha de Pokémon estilo DP "
							+ (state.equals("on") ? "ACTIVADA" : "DESACTIVADA (se usa la de Cobblemon)")));
					return 1;
				}));
			}
			root.then(resumen);
			root.then(ClientCommandManager.literal("bolsa").executes(ctx -> {
				pendingBag = 2;
				return 1;
			}));
			root.then(ClientCommandManager.literal("equipo").executes(ctx -> {
				pendingParty = 2;
				return 1;
			}));
			root.then(ClientCommandManager.literal("pokereloj").executes(ctx -> {
				pendingPoketch = 2;
				return 1;
			}));
			root.executes(ctx -> {
				ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino " + version() + "] /platino ficha · pokereloj · bolsa · equipo · resumen · combate · paleta · recargar"));
				return 1;
			});
			dispatcher.register(root.then(pal).then(ClientCommandManager.literal("recargar").executes(ctx -> {
				PlatinoConfig.reload(() -> ctx.getSource().sendFeedback(Text.literal("[Cobblemon Platino] Configuración recargada.")));
				return 1;
			})));
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			ServerStats.reset();
			if (client.player != null) {
				client.player.sendMessage(Text.literal("§6[Cobblemon Platino " + version() + "]§f I = Bolsa · K = Ficha · P = Pokéreloj · N = Notas"
						+ (DiosesmonBridge.isPresent() ? " · marcador DiosesMon OK" : "")), false);
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ServerStats::reset));

		LOGGER.info("[Cobblemon Platino] Cargado " + version());
	}

	private static int pendingCard = 0;
	private static int pendingPoketch = 0;
	private static int pendingBag = 0;
	private static int pendingParty = 0;
	private static int pendingUse = 0;

	/** Usa el objeto de la mano en 2 ticks (cuando la Bolsa ya se ha cerrado). */
	public static void useHeldItemSoon() {
		pendingUse = 2;
	}

	public static boolean isBagKey(int keyCode, int scanCode) {
		return bagKey != null && bagKey.matchesKey(keyCode, scanCode);
	}

	public static boolean isPoketchKey(int keyCode, int scanCode) {
		return poketchKey != null && poketchKey.matchesKey(keyCode, scanCode);
	}

	/** Podómetro del Pokéreloj: 1 paso por cada bloque recorrido a pie. */
	private static void countSteps(MinecraftClient client) {
		var player = client.player;
		if (player == null) {
			hasLast = false;
			if (unsavedSteps > 0) {
				unsavedSteps = 0;
				PlatinoConfig.save();
			}
			return;
		}
		double x = player.getX(), z = player.getZ();
		if (hasLast && player.isOnGround() && !player.hasVehicle() && !player.getAbilities().flying) {
			double d = Math.hypot(x - lastX, z - lastZ);
			if (d < 2.0) stepDistance += d; // ignora teletransportes
			while (stepDistance >= 1.0) {
				stepDistance -= 1.0;
				PlatinoConfig.get().poketchSteps++;
				if (++unsavedSteps >= 50) {
					unsavedSteps = 0;
					PlatinoConfig.save();
				}
			}
		}
		lastX = x;
		lastZ = z;
		hasLast = true;
	}

	private static void openCard() {
		MinecraftClient mc = MinecraftClient.getInstance();
		try {
			mc.setScreen(new TrainerCardScreen());
		} catch (Throwable t) {
			LOGGER.error("[Cobblemon Platino] No se pudo abrir la Ficha", t);
			if (mc.player != null) mc.player.sendMessage(Text.literal("§c[Cobblemon Platino] Error al abrir la Ficha: " + t), false);
		}
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	public static Pokemon selectedPartyPokemon() {
		try {
			var storage = CobblemonClient.INSTANCE.getStorage();
			return storage.getParty().get(storage.getSelectedSlot());
		} catch (Throwable t) {
			return null;
		}
	}

	private static void openNotes(Summary summary) {
		Pokemon p = summary.getSelectedPokemon$common();
		if (p != null) MinecraftClient.getInstance().setScreen(new NotesScreen(summary, p));
	}

	/** Tarjeta con la nota debajo del Resumen (o encima si no cabe). Máx. 3 líneas. */
	private static void drawNoteCard(DrawContext ctx, String note, int px, int py, int w, int sumH, int screenH) {
		var tr = MinecraftClient.getInstance().textRenderer;
		List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(note), w - 12);
		int n = Math.min(3, lines.size());
		int h = 8 + n * 10;
		int y = py + sumH + 4;
		if (y + h > screenH - 2) y = Math.max(2, py - 18 - h);
		int x = px;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 500);
		Draw.panelGradient(ctx, x, y, x + w, y + h, Draw.OUTLINE, 0xFFF8F0C8, 0xFFE8D898);
		for (int i = 0; i < n; i++) {
			ctx.drawText(tr, lines.get(i), x + 6, y + 5 + i * 10, Draw.TEXT_DARK, false);
		}
		ctx.getMatrices().pop();
	}
}

package com.platino;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee del marcador lateral del servidor (DINERO, PUNTOS BATALLA, CLAN…).
 * 1) Marcador vanilla. 2) Si el servidor lo dibuja con su propio mod, se "escucha" el texto que se pinta en pantalla
 *    mientras la Ficha de Entrenador está abierta.
 */
public final class ServerStats {
	private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
	private static final Pattern MONEY = Pattern.compile("(?:DINERO|MONEY|SALDO|BALANCE)\\s*:?\\s*\\$?\\s*([0-9][0-9.,]*[0-9kKmM]?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern POINTS = Pattern.compile("PUNTOS\\s+(?:DE\\s+)?BATALLA\\s*:?\\s*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLAN = Pattern.compile("CLAN\\s*:\\s*\\(?\\s*([^)\\s]{1,24})", Pattern.CASE_INSENSITIVE);

	/** Activo mientras la ficha está abierta. */
	public static volatile boolean capturing = false;
	private static final List<String> frameText = new ArrayList<>();

	public static String money, points, clan;

	private ServerStats() {}

	public static void capture(String s) {
		if (!capturing || s == null || s.isBlank()) return;
		synchronized (frameText) {
			if (frameText.size() < 600) frameText.add(s);
		}
	}

	public static void capture(OrderedText t) {
		if (!capturing || t == null) return;
		StringBuilder sb = new StringBuilder();
		t.accept((i, style, cp) -> {
			sb.appendCodePoint(cp);
			return true;
		});
		capture(sb.toString());
	}

	public static void capture(Text t) {
		if (capturing && t != null) capture(t.getString());
	}

	/** Procesa lo capturado en este fotograma y el marcador vanilla. */
	public static void update() {
		StringBuilder all = new StringBuilder();
		// Marcador vanilla
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world != null) {
				Scoreboard sb = mc.world.getScoreboard();
				ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
				if (obj != null) {
					for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
						Team team = sb.getScoreHolderTeam(e.owner());
						Text line = Team.decorateName(team, e.name());
						all.append(line.getString()).append('\n');
					}
				}
			}
		} catch (Throwable ignored) {
		}
		synchronized (frameText) {
			for (String s : frameText) all.append(s).append(' ');
			frameText.clear();
		}
		String text = FORMAT.matcher(all).replaceAll("");
		Matcher m = MONEY.matcher(text);
		if (m.find()) money = m.group(1);
		m = POINTS.matcher(text);
		if (m.find()) points = m.group(1);
		m = CLAN.matcher(text);
		if (m.find()) clan = m.group(1);
	}
}

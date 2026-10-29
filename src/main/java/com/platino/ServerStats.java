package com.platino;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Datos del marcador del servidor: primero el de DiosesMon, si no el marcador normal de Minecraft. */
public final class ServerStats {
	private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
	private static final Pattern MONEY = Pattern.compile("(?:DINERO|MONEY|SALDO|BALANCE)\\s*:?\\s*\\$?\\s*([0-9][0-9.,]*[0-9kKmM]?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern COINS = Pattern.compile("DIOSESCOINS\\s*:?\\s*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE);
	private static final Pattern POINTS = Pattern.compile("PUNTOS\\s+(?:DE\\s+)?BATALLA\\s*:?\\s*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLAN = Pattern.compile("CLAN\\s*:\\s*\\(?\\s*([^)\\s]{1,24})", Pattern.CASE_INSENSITIVE);

	public static String money, coins, points, clan;
	private static long lastUpdate;

	private ServerStats() {}

	public static void update() {
		long now = System.currentTimeMillis();
		if (now - lastUpdate < 500) return;
		lastUpdate = now;
		StringBuilder all = new StringBuilder();
		for (Text t : DiosesmonBridge.scoreboardLines()) all.append(t.getString()).append('\n');
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world != null) {
				Scoreboard sb = mc.world.getScoreboard();
				ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
				if (obj != null) {
					for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
						Team team = sb.getScoreHolderTeam(e.owner());
						all.append(Team.decorateName(team, e.name()).getString()).append('\n');
					}
				}
			}
		} catch (Throwable ignored) {
		}
		String text = FORMAT.matcher(all).replaceAll("");
		Matcher m = MONEY.matcher(text);
		if (m.find()) money = m.group(1);
		m = COINS.matcher(text);
		if (m.find()) coins = m.group(1);
		m = POINTS.matcher(text);
		if (m.find()) points = m.group(1);
		m = CLAN.matcher(text);
		if (m.find()) clan = m.group(1);
	}
}

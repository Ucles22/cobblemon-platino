package com.platino;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
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
	private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
	private static final Pattern NUMBER = Pattern.compile("(?<![0-9A-Za-z])([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)(?![0-9]*\\s*[dhms]\\b)(?![:/])");
	private static final char COIN_GLYPH = '\ue104';

	private ServerStats() {}

	/** Pasa versalitas a letras normales y quita iconos de fuente propia (zona privada Unicode). */
	static String normalize(String raw) {
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char ch = raw.charAt(i);
			int sc = SMALL_CAPS.indexOf(ch);
			if (sc >= 0) sb.append((char) ('A' + sc));
			else if (ch >= '\ue000' && ch <= '\uf8ff') sb.append(' ');
			else sb.append(ch);
		}
		return FORMAT.matcher(sb).replaceAll("").replaceAll("[ \\t]+", " ");
	}

	public static void update() {
		long now = System.currentTimeMillis();
		if (now - lastUpdate < 500) return;
		lastUpdate = now;
		List<String> raw = new ArrayList<>();
		for (Text t : DiosesmonBridge.scoreboardLines()) raw.add(t.getString());
		try {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (raw.isEmpty() && mc.world != null) {
				Scoreboard sb = mc.world.getScoreboard();
				ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
				if (obj != null) {
					for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
						Team team = sb.getScoreHolderTeam(e.owner());
						raw.add(Team.decorateName(team, e.name()).getString());
					}
				}
			}
		} catch (Throwable ignored) {
		}

		String m = null, co = null, p = null, cl = null;
		List<String> numeric = new ArrayList<>();
		for (String r : raw) {
			String line = normalize(r);
			Matcher x;
			if (m == null && (x = MONEY.matcher(line)).find()) m = x.group(1);
			if (co == null && (x = COINS.matcher(line)).find()) co = x.group(1);
			if (p == null && (x = POINTS.matcher(line)).find()) p = x.group(1);
			if (cl == null && (x = CLAN.matcher(line)).find()) cl = x.group(1);
			// Para el plan B: líneas con un número que no sean fechas ni cuentas atrás
			if (!line.matches(".*\\d+\\s*[dhms]\\b.*") && !line.matches(".*\\d{1,2}[/:]\\d{2}.*")) {
				Matcher n = NUMBER.matcher(line);
				if (n.find()) numeric.add((r.indexOf(COIN_GLYPH) >= 0 ? "C" : "N") + n.group(1));
			}
		}
		// Plan B por orden: DINERO, DIOSESCOINS (lleva el icono de moneda), PUNTOS BATALLA
		if (m == null || p == null) {
			List<String> plain = new ArrayList<>();
			for (String v : numeric) {
				if (v.charAt(0) == 'C') { if (co == null) co = v.substring(1); }
				else plain.add(v.substring(1));
			}
			if (m == null && !plain.isEmpty()) m = plain.get(0);
			if (p == null && plain.size() >= 2) p = plain.get(1);
		}
		if (m != null) money = m;
		if (co != null) coins = co;
		if (p != null) points = p;
		if (cl != null) clan = cl;
	}

	/** Para /platino debug marcador: líneas tal cual llegan, con los caracteres especiales visibles. */
	public static List<String> debugLines() {
		List<String> out = new ArrayList<>();
		for (Text t : DiosesmonBridge.scoreboardLines()) {
			StringBuilder sb = new StringBuilder();
			for (char ch : t.getString().toCharArray()) {
				if (ch < 32 || ch > 126) sb.append(String.format("<%04x>", (int) ch));
				else sb.append(ch);
			}
			out.add(sb.toString());
		}
		return out;
	}
}

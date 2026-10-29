package com.platino;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

final class JsonFileStore {
	private final Path path;
	private final Path tempPath;
	private final Path backupPath;
	private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);
	private volatile boolean writable = true;

	JsonFileStore(String fileName) {
		path = FabricLoader.getInstance().getConfigDir().resolve(fileName);
		tempPath = path.resolveSibling(fileName + ".tmp");
		backupPath = path.resolveSibling(fileName + ".bak");
	}

	<T> T readNow(Gson gson, TypeToken<T> type) {
		if (!Files.exists(path)) return null;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return gson.fromJson(reader, type);
		} catch (IOException | JsonParseException e) {
			PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudo leer {}, se guarda una copia en {}", path, backupPath.getFileName(), e);
			backUp();
			return null;
		}
	}

	<T> CompletableFuture<T> read(Gson gson, TypeToken<T> type) {
		CompletableFuture<T> result = queue.thenApplyAsync(ignored -> readNow(gson, type), Util.getIoWorkerExecutor());
		queue = result.handle((value, error) -> null);
		return result;
	}

	void write(String json) {
		queue = queue.thenRunAsync(() -> writeIfAllowed(json), Util.getIoWorkerExecutor())
				.exceptionally(this::logWriteFailure);
	}

	private void writeIfAllowed(String json) {
		if (!writable) return;
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(tempPath, json, StandardCharsets.UTF_8);
			moveIntoPlace();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void moveIntoPlace() throws IOException {
		try {
			Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void backUp() {
		try {
			Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			writable = false;
			PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudo copiar {}, no se sobrescribirá", path, e);
		}
	}

	private Void logWriteFailure(Throwable error) {
		PlatinoClient.LOGGER.error("[Cobblemon Platino] No se pudo guardar {}", path, error);
		return null;
	}
}

package com.strange.safety.vlm.snapshotassist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-disk snapshot + JSON status for snapshot-assist MVP (7-day retention elsewhere).
 * Not multi-node safe; S3 is out of this MVP scope.
 */
@Component
public class SnapshotAssistStore {
    private final Path root;
    private final ObjectMapper mapper;
    private final Map<String, SnapshotAssistRecord> memory = new ConcurrentHashMap<>();

    public SnapshotAssistStore(SnapshotAssistProperties properties) throws IOException {
        this.root = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path root() {
        return root;
    }

    public synchronized SnapshotAssistRecord saveSnapshot(
            String eventId,
            String cameraLoginId,
            byte[] jpegBytes
    ) throws IOException {
        String safeId = sanitize(eventId);
        Path dir = root.resolve(safeId);
        Files.createDirectories(dir);
        Path jpeg = dir.resolve("snapshot.jpg");
        Path tmp = dir.resolve("snapshot.jpg.tmp");
        Files.write(tmp, jpegBytes);
        try {
            Files.move(tmp, jpeg, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, jpeg, StandardCopyOption.REPLACE_EXISTING);
        }

        SnapshotAssistRecord existing = memory.get(eventId);
        if (existing != null && existing.status() != SnapshotAssistStatus.PENDING) {
            // Idempotent re-upload keeps terminal status; refresh jpeg only
            SnapshotAssistRecord refreshed = existing.withJpegPath(jpeg.toString()).touch();
            memory.put(eventId, refreshed);
            writeStatus(dir, refreshed);
            return refreshed;
        }
        SnapshotAssistRecord record = SnapshotAssistRecord.pending(eventId, cameraLoginId, jpeg.toString());
        memory.put(eventId, record);
        writeStatus(dir, record);
        return record;
    }

    public Optional<SnapshotAssistRecord> find(String eventId) {
        SnapshotAssistRecord mem = memory.get(eventId);
        if (mem != null) {
            return Optional.of(mem);
        }
        Path status = root.resolve(sanitize(eventId)).resolve("status.json");
        if (!Files.isRegularFile(status)) {
            return Optional.empty();
        }
        try {
            SnapshotAssistRecord loaded = mapper.readValue(status.toFile(), SnapshotAssistRecord.class);
            memory.put(eventId, loaded);
            return Optional.of(loaded);
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public synchronized SnapshotAssistRecord update(SnapshotAssistRecord record) throws IOException {
        memory.put(record.eventId(), record);
        Path dir = root.resolve(sanitize(record.eventId()));
        Files.createDirectories(dir);
        writeStatus(dir, record);
        return record;
    }

    public synchronized void saveContext(String eventId, SnapshotAssistContext context) throws IOException {
        Path dir = root.resolve(sanitize(eventId));
        Files.createDirectories(dir);
        Path ctx = dir.resolve("context.json");
        Path tmp = dir.resolve("context.json.tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), context);
        try {
            Files.move(tmp, ctx, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, ctx, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Optional<SnapshotAssistContext> loadContext(String eventId) {
        Path ctx = root.resolve(sanitize(eventId)).resolve("context.json");
        if (!Files.isRegularFile(ctx)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(ctx.toFile(), SnapshotAssistContext.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public int deleteOlderThan(Instant cutoff) throws IOException {
        int removed = 0;
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (var stream = Files.list(root)) {
            for (Path dir : stream.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Path status = dir.resolve("status.json");
                Instant updated = Files.getLastModifiedTime(dir).toInstant();
                if (Files.isRegularFile(status)) {
                    try {
                        SnapshotAssistRecord rec = mapper.readValue(status.toFile(), SnapshotAssistRecord.class);
                        if (rec.updatedAt() != null) {
                            updated = rec.updatedAt();
                        }
                    } catch (IOException ignored) {
                        // use dir mtime
                    }
                }
                if (updated.isBefore(cutoff)) {
                    deleteRecursive(dir);
                    memory.entrySet().removeIf(e -> sanitize(e.getKey()).equals(dir.getFileName().toString()));
                    removed += 1;
                }
            }
        }
        return removed;
    }

    private void writeStatus(Path dir, SnapshotAssistRecord record) throws IOException {
        Path status = dir.resolve("status.json");
        Path tmp = dir.resolve("status.json.tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), record);
        try {
            Files.move(tmp, status, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, status, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static String sanitize(String eventId) {
        return eventId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

package com.vinekeepers.workflow.planning.rag;

import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Walks a repo workspace and emits text chunks with stable content hashes (scoped paths, size caps).
 */
public final class RepoFileChunker {

    private static final int DEFAULT_MAX_FILE_BYTES = 512_000;
    private static final int CHUNK_CHARS = 2000;
    private static final int CHUNK_STEP = 1600;

    private final int maxFileBytes;
    private final int chunkChars;
    private final int chunkStep;

    public RepoFileChunker() {
        this(DEFAULT_MAX_FILE_BYTES, CHUNK_CHARS, CHUNK_STEP);
    }

    public RepoFileChunker(int maxFileBytes, int chunkChars, int chunkStep) {
        this.maxFileBytes = Math.max(4096, maxFileBytes);
        this.chunkChars = Math.max(256, chunkChars);
        this.chunkStep = Math.max(128, Math.min(chunkChars, chunkStep));
    }

    public List<GroundingChunkRecord> chunkRepo(Path repoRoot, String repoRef, String branch, long nowMs, String embeddingModel)
            throws IOException {
        Objects.requireNonNull(repoRoot, "repoRoot");
        String ref = repoRef != null ? repoRef.trim() : "";
        String br = branch != null && !branch.isBlank() ? branch.trim() : "unknown";
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(
                repoRoot,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (shouldSkipDir(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && attrs.size() <= maxFileBytes && isTextCandidate(file)) {
                            files.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        List<GroundingChunkRecord> out = new ArrayList<>();
        for (Path f : files) {
            String rel = normalizeRel(repoRoot, f);
            String text = readUtf8Lenient(f);
            if (text == null || text.isBlank()) {
                continue;
            }
            String normalized = normalizeWhitespace(text);
            int len = normalized.length();
            for (int start = 0; start < len; start += chunkStep) {
                int end = Math.min(len, start + chunkChars);
                String piece = normalized.substring(start, end);
                if (piece.isBlank()) {
                    continue;
                }
                String hash = sha256Hex(piece);
                out.add(new GroundingChunkRecord(hash, ref, br, rel, piece, embeddingModel, nowMs));
                if (end >= len) {
                    break;
                }
            }
        }
        return out;
    }

    private static boolean shouldSkipDir(String name) {
        return switch (name) {
            case ".git",
                    ".vinekeepers",
                    ".cursor",
                    "target",
                    "build",
                    "node_modules",
                    ".idea",
                    ".gradle",
                    "dist",
                    "out" -> true;
            default -> false;
        };
    }

    /** Allow .vinekeepers for other files but skip the cache file itself in walk — handled by skipping .vinekeepers? Handoff says cache at .vinekeepers/repo-grounding.parquet — skip whole .vinekeepers to avoid reading parquet as text. */
    private static boolean isTextCandidate(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".parquet") || n.endsWith(".bin") || n.endsWith(".png") || n.endsWith(".jpg")) {
            return false;
        }
        return n.endsWith(".java")
                || n.endsWith(".md")
                || n.endsWith(".yml")
                || n.endsWith(".yaml")
                || n.endsWith(".json")
                || n.endsWith(".txt")
                || n.endsWith(".properties")
                || n.endsWith(".gradle")
                || n.endsWith(".kts")
                || n.endsWith(".xml")
                || n.endsWith(".html")
                || n.endsWith(".css")
                || n.endsWith(".js")
                || n.endsWith(".ts")
                || n.endsWith(".tsx")
                || n.endsWith(".sql")
                || n.endsWith(".sh")
                || n.endsWith(".ps1")
                || n.endsWith(".toml")
                || n.endsWith(".rs")
                || n.endsWith(".go")
                || n.endsWith(".py")
                || n.endsWith(".rb")
                || n.endsWith(".cs")
                || n.endsWith(".c")
                || n.endsWith(".h")
                || n.endsWith(".cpp")
                || n.endsWith(".hpp");
    }

    private static String normalizeRel(Path root, Path file) {
        try {
            Path rel = root.relativize(file);
            return rel.toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getFileName().toString();
        }
    }

    private static String readUtf8Lenient(Path file) {
        try {
            byte[] raw = Files.readAllBytes(file);
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
            dec.onMalformedInput(CodingErrorAction.REPLACE);
            dec.onUnmappableCharacter(CodingErrorAction.REPLACE);
            return dec.decode(java.nio.ByteBuffer.wrap(raw)).toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String normalizeWhitespace(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

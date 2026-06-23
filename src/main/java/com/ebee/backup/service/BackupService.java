package com.ebee.backup.service;

import com.ebee.backup.enums.BackupAction;
import com.ebee.backup.service.BackupAuditLogService;
import com.ebee.common.config.AppProperties;
import com.ebee.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class BackupService {

    private final BackupAuditLogService auditLogService;
    private final AppProperties appProperties;
    private final DataSourceProperties dataSourceProperties;

    public BackupService(BackupAuditLogService auditLogService,
                         AppProperties appProperties,
                         DataSourceProperties dataSourceProperties) {
        this.auditLogService = auditLogService;
        this.appProperties = appProperties;
        this.dataSourceProperties = dataSourceProperties;
    }

    private String getBackupDir() {
        String dir = appProperties.getBackup().getDir();
        return dir != null && !dir.isBlank() ? dir : System.getProperty("user.dir") + "/backups";
    }

    public List<Map<String, Object>> listBackups() {
        File dir = new File(getBackupDir());
        if (!dir.exists()) {
            dir.mkdirs();
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".dump"));
        if (files == null) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (File f : files) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", f.getName());
            entry.put("size", f.length());
            entry.put("lastModified", f.lastModified());
            result.add(entry);
        }
        result.sort((a, b) -> ((String) b.get("name")).compareTo((String) a.get("name")));
        return result;
    }

    public Map<String, Object> createBackup() {
        ensureDir();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "ebee_db_" + timestamp + ".dump";
        Path filePath = Paths.get(getBackupDir(), filename);

        Map<String, String> db = parseDbUrl();

        List<String> cmd = List.of(
                "pg_dump",
                "-h", db.get("host"),
                "-p", db.get("port"),
                "-U", db.get("user"),
                "-d", db.get("name"),
                "-Fc", "--no-owner",
                "-f", filePath.toString()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", db.get("password"));

        try {
            Process process = pb.start();
            String output = readOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Files.deleteIfExists(filePath);
                throw new RuntimeException("pg_dump failed (exit " + exitCode + "):\n" + output);
            }

            if (!verifyDump(filePath.toString())) {
                Files.deleteIfExists(filePath);
                throw new RuntimeException("Backup verification failed — file is corrupt");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", filename);
            result.put("size", Files.size(filePath));
            result.put("path", filePath.toAbsolutePath().toString());
            result.put("verified", true);
            auditLogService.record(BackupAction.CREATE, filename, null);
            return result;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Backup failed: " + e.getMessage(), e);
        }
    }

    public boolean verifyDump(String filePath) {
        try {
            List<String> cmd = List.of("pg_restore", "-l", filePath);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();
            boolean ok = process.waitFor() == 0;
            if (!ok) {
                log.error("pg_restore -l failed for {}:\n{}", filePath, readOutput(process));
            }
            return ok;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to verify dump: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> restore(String filename) {
        Path dumpPath = resolveSafePath(filename);

        if (!Files.exists(dumpPath)) {
            throw new ResourceNotFoundException("Backup file not found: " + filename);
        }

        if (!verifyDump(dumpPath.toString())) {
            throw new IllegalArgumentException("Dump file is invalid or corrupt: " + filename);
        }

        Map<String, Object> preBackup = createBackup();
        log.info("Pre-restore auto-backup created: {}", preBackup.get("name"));

        Map<String, String> db = parseDbUrl();
        Map<String, String> env = Map.of("PGPASSWORD", db.get("password"));

        List<String> dropCmd = List.of(
                "psql",
                "-h", db.get("host"),
                "-p", db.get("port"),
                "-U", db.get("user"),
                "-d", "postgres",
                "-c", "DROP DATABASE IF EXISTS " + db.get("name") + " WITH (FORCE);"
        );

        List<String> createCmd = List.of(
                "createdb",
                "-h", db.get("host"),
                "-p", db.get("port"),
                "-U", db.get("user"),
                db.get("name")
        );

        List<String> restoreCmd = List.of(
                "pg_restore",
                "-h", db.get("host"),
                "-p", db.get("port"),
                "-U", db.get("user"),
                "-d", db.get("name"),
                "--exit-on-error", "--no-owner",
                dumpPath.toString()
        );

        try {
            runProcess("dropdb", dropCmd, env);
            runProcess("createdb", createCmd, env);
            runProcess("restore", restoreCmd, env);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("restoredFrom", filename);
            result.put("preRestoreBackup", preBackup.get("name"));
            auditLogService.record(BackupAction.RESTORE, filename,
                    "Pre-restore backup: " + preBackup.get("name"));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Restore failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> uploadBackup(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        ensureDir();
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "uploaded_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".dump";
        }
        if (!filename.endsWith(".dump")) {
            filename = filename + ".dump";
        }

        Path target = Paths.get(getBackupDir(), filename);
        int counter = 1;
        while (Files.exists(target)) {
            String base = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
            target = Paths.get(getBackupDir(), base + "_" + counter + ext);
            counter++;
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            boolean valid = verifyDump(target.toString());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", target.getFileName().toString());
            result.put("size", Files.size(target));
            result.put("verified", valid);
            auditLogService.record(BackupAction.UPLOAD, target.getFileName().toString(),
                    valid ? null : "Verification failed");
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload backup: " + e.getMessage(), e);
        }
    }

    public Path getBackupFile(String filename) {
        Path path = resolveSafePath(filename);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Backup file not found: " + filename);
        }
        return path;
    }

    private Path resolveSafePath(String filename) {
        Path base = Paths.get(getBackupDir()).normalize().toAbsolutePath();
        Path resolved = base.resolve(filename).normalize().toAbsolutePath();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Invalid path traversal: " + filename);
        }
        return resolved;
    }

    private Map<String, String> parseDbUrl() {
        String jdbcUrl = dataSourceProperties.getUrl();
        String withoutPrefix = jdbcUrl.substring("jdbc:postgresql://".length());
        int slashIndex = withoutPrefix.indexOf('/');
        String hostPort = (slashIndex > 0) ? withoutPrefix.substring(0, slashIndex) : withoutPrefix;
        String dbName = (slashIndex > 0) ? withoutPrefix.substring(slashIndex + 1) : "ebee_db";

        int colonIndex = hostPort.indexOf(':');
        String host = (colonIndex > 0) ? hostPort.substring(0, colonIndex) : hostPort;
        String port = (colonIndex > 0) ? hostPort.substring(colonIndex + 1) : "5432";

        if (dbName.contains("?")) {
            dbName = dbName.substring(0, dbName.indexOf('?'));
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("host", host);
        result.put("port", port);
        result.put("name", dbName);
        result.put("user", dataSourceProperties.getUsername());
        result.put("password", dataSourceProperties.getPassword() != null ? dataSourceProperties.getPassword() : "");
        return result;
    }

    private void runProcess(String label, List<String> command, Map<String, String> env)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);

        log.debug("Running {}: {}", label, String.join(" ", command));
        Process process = pb.start();
        String output = readOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("{} failed (exit {}):\n{}", label, exitCode, output);
            throw new RuntimeException(label + " failed with exit code " + exitCode);
        }
        log.debug("{} completed successfully", label);
    }

    private String readOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private void ensureDir() {
        File dir = new File(getBackupDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}

package dev.frostguard.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsInstallerHandoffTest {
    @TempDir
    Path temp;

    @Test
    void startsCmdWaiterWithInstallerAndParentIdentity() throws Exception {
        Path installer = Files.writeString(temp.resolve("Frostguard-3.0.1.msi"), "test");
        Path installDirectory = Files.createDirectory(temp.resolve("installed Frostguard"));
        Path launcher = Files.writeString(installDirectory.resolve("Frostguard.exe"), "test");
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        AtomicReference<java.util.List<String>> command = new AtomicReference<>();
        AtomicReference<java.util.Map<String, String>> environment = new AtomicReference<>();
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff((actualCommand, actualEnvironment) -> {
            command.set(actualCommand);
            environment.set(actualEnvironment);
        });

        InstallerHandoff.HandoffSession session = handoff.stage(
                installer, 4242L, launcher, workspace);

        assertTrue(command.get().get(0).toLowerCase().endsWith("cmd.exe"));
        assertTrue(command.get().contains("/d"));
        assertTrue(command.get().contains("/c"));
        assertTrue(command.get().contains("call"));
        assertFalse(command.get().stream().anyMatch(part -> part.toLowerCase().contains("powershell")));
        assertFalse(command.get().contains("-EncodedCommand"));
        assertFalse(command.get().contains("Bypass"));

        Path script = Path.of(command.get().get(command.get().size() - 1));
        assertTrue(Files.isRegularFile(script));
        assertTrue(script.getFileName().toString().endsWith(WindowsInstallerHandoff.HANDOFF_SCRIPT_SUFFIX));
        String scriptText = Files.readString(script, StandardCharsets.UTF_8);
        assertTrue(scriptText.contains("tasklist /FI \"PID eq %TARGET_PID%\""));
        assertTrue(scriptText.contains("msiexec /i \"%INSTALLER_PATH%\" /passive /norestart INSTALLDIR=\"%INSTALL_DIRECTORY%\""));
        assertTrue(scriptText.contains("The Frostguard restart launcher was not found"));
        assertTrue(scriptText.contains(":start_frostguard"));
        assertTrue(scriptText.contains("Windows Installer exited with code"));
        assertFalse(scriptText.toLowerCase().contains("powershell"));

        assertEquals("4242", environment.get().get(WindowsInstallerHandoff.PID_ENV));
        assertEquals(installer.toAbsolutePath().normalize().toString(),
                environment.get().get(WindowsInstallerHandoff.INSTALLER_ENV));
        assertEquals(launcher.toAbsolutePath().normalize().toString(),
                environment.get().get(WindowsInstallerHandoff.RESTART_LAUNCHER_ENV));
        assertEquals(installDirectory.toAbsolutePath().normalize().toString(),
                environment.get().get(WindowsInstallerHandoff.INSTALL_DIR_ENV));
        assertEquals(workspace.toAbsolutePath().normalize().toString(),
                environment.get().get(WindowsInstallerHandoff.WORKSPACE_ENV));
        Path token = Path.of(environment.get().get(WindowsInstallerHandoff.TOKEN_PATH_ENV));
        session.authorize();
        assertEquals(environment.get().get(WindowsInstallerHandoff.TOKEN_VALUE_ENV), Files.readString(token));
        session.cancel();
        assertTrue(Files.notExists(token));
        assertTrue(Files.notExists(script));
    }

    @Test
    void restartsExistingInstallationWhenWindowsInstallerFails() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path installer = Files.writeString(temp.resolve("Frostguard-broken.msi"), "not an MSI package");
        Path installDirectory = Files.createDirectory(temp.resolve("installed Frostguard"));
        Path restartMarker = temp.resolve("restart-marker.txt");
        Path launcher = Files.writeString(installDirectory.resolve("Frostguard.cmd"),
                "@echo off\r\n>\"" + restartMarker + "\" echo restarted\r\n");
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path persistedData = Files.writeString(workspace.resolve("frostguard.db"), "existing profile data");
        Path fakeSystemTools = Files.createDirectory(temp.resolve("fake-system-tools"));
        Files.copy(Path.of(System.getenv("SystemRoot"), "System32", "where.exe"),
                fakeSystemTools.resolve("msiexec.exe"));
        Process parent = new ProcessBuilder("cmd.exe", "/d", "/c", "timeout /t 1 /nobreak >nul").start();
        AtomicReference<Process> waiter = new AtomicReference<>();
        AtomicReference<Map<String, String>> handoffEnvironment = new AtomicReference<>();
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff((command, environment) -> {
            String scriptPath = command.get(command.size() - 1);
            String testScript = Files.readString(Path.of(scriptPath), StandardCharsets.UTF_8)
                    .replace("call :show_update_failure", "echo %FAILURE_DETAILS%");
            Files.writeString(Path.of(scriptPath), testScript, StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().putAll(environment);
            builder.environment().put("PATH", fakeSystemTools + ";" + builder.environment().get("PATH"));
            handoffEnvironment.set(environment);
            waiter.set(builder.start());
        });

        InstallerHandoff.HandoffSession session = handoff.stage(installer, parent.pid(), launcher, workspace);
        session.authorize();

        assertTrue(waiter.get().waitFor(20, TimeUnit.SECONDS));
        assertEquals(1, waiter.get().exitValue());
        waitForFile(restartMarker);
        assertEquals("restarted", Files.readString(restartMarker).trim());
        assertEquals("existing profile data", Files.readString(persistedData));
        assertTrue(Files.notExists(Path.of(handoffEnvironment.get().get(WindowsInstallerHandoff.TOKEN_PATH_ENV))));
        String output = new String(waiter.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(output.contains("Windows Installer exited with code"), output);
    }

    @Test
    void rejectsInvalidInputsBeforeStartingWaiter() {
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff((command, environment) -> {
            throw new AssertionError("Waiter should not start");
        });
        Path installer = temp.resolve("missing.msi");
        Path launcher = temp.resolve("missing-launcher.exe");
        assertThrows(UpdateException.class,
                () -> handoff.stage(installer, 10L, launcher, temp));
    }

    @Test
    void reportsWaiterLaunchFailure() throws Exception {
        Path installer = Files.writeString(temp.resolve("Frostguard-3.0.1.msi"), "test");
        Path installDirectory = Files.createDirectory(temp.resolve("installed"));
        Path launcher = Files.writeString(installDirectory.resolve("Frostguard.exe"), "test");
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff((command, environment) -> {
            throw new IOException("blocked");
        });
        assertThrows(UpdateException.class,
                () -> handoff.stage(installer, 10L, launcher, workspace));
        assertTrue(Files.notExists(installer.resolveSibling(installer.getFileName() + WindowsInstallerHandoff.HANDOFF_SCRIPT_SUFFIX)));
    }

    @Test
    void rejectsExeWrapperBeforeStartingWaiter() throws Exception {
        Path installer = Files.writeString(temp.resolve("Frostguard-3.0.1.exe"), "test");
        Path installDirectory = Files.createDirectory(temp.resolve("installed-exe"));
        Path launcher = Files.writeString(installDirectory.resolve("Frostguard.exe"), "test");
        Path workspace = Files.createDirectory(temp.resolve("workspace-exe"));
        WindowsInstallerHandoff handoff = new WindowsInstallerHandoff((command, environment) -> {
            throw new AssertionError("Waiter should not start for an EXE wrapper");
        });

        assertThrows(UpdateException.class,
                () -> handoff.stage(installer, 10L, launcher, workspace));
    }

    private static void waitForFile(Path file) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Files.notExists(file) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(Files.isRegularFile(file), "Restart launcher was not executed");
    }
}

package dev.frostguard.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stages a detached Windows Installer handoff after Frostguard exits.
 *
 * <p>The waiter is a plain {@code .cmd} script launched through {@code cmd.exe}.
 * A hidden PowerShell {@code -EncodedCommand} / {@code -ExecutionPolicy Bypass}
 * payload matches Norton Behavioral Protection ({@code IDP.HELU.PSE96%s_cmd})
 * even when Frostguard itself is exempted, because the detection targets the
 * interpreter command line rather than the Frostguard binary path.
 */
public final class WindowsInstallerHandoff implements InstallerHandoff {
    static final String INSTALLER_ENV = "FROSTGUARD_UPDATE_INSTALLER";
    static final String PID_ENV = "FROSTGUARD_UPDATE_PARENT_PID";
    static final String TOKEN_PATH_ENV = "FROSTGUARD_UPDATE_TOKEN_PATH";
    static final String TOKEN_VALUE_ENV = "FROSTGUARD_UPDATE_TOKEN_VALUE";
    static final String RESTART_LAUNCHER_ENV = "FROSTGUARD_UPDATE_RESTART_LAUNCHER";
    static final String INSTALL_DIR_ENV = "FROSTGUARD_UPDATE_INSTALL_DIR";
    static final String WORKSPACE_ENV = "FROSTGUARD_WORKSPACE";
    static final String HANDOFF_SCRIPT_SUFFIX = ".handoff.cmd";

    private final DetachedProcessStarter processStarter;

    public WindowsInstallerHandoff() {
        this((command, environment) -> {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(environment);
            builder.redirectInput(ProcessBuilder.Redirect.PIPE);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.start();
        });
    }

    WindowsInstallerHandoff(DetachedProcessStarter processStarter) {
        this.processStarter = processStarter;
    }

    @Override
    public HandoffSession stage(Path installer, long parentPid, Path restartLauncher, Path workspaceRoot)
            throws UpdateException {
        if (!Files.isRegularFile(installer)) {
            throw new UpdateException("Installer handoff target does not exist: " + installer);
        }
        if (!installer.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".msi")) {
            throw new UpdateException("Windows update handoff requires an MSI package");
        }
        if (parentPid <= 0) {
            throw new UpdateException("Installer handoff requires a valid Frostguard process ID");
        }
        if (!Files.isRegularFile(restartLauncher)) {
            throw new UpdateException("Frostguard restart launcher does not exist: " + restartLauncher);
        }
        if (!Files.isDirectory(workspaceRoot)) {
            throw new UpdateException("Frostguard restart workspace does not exist: " + workspaceRoot);
        }
        Path normalizedLauncher = restartLauncher.toAbsolutePath().normalize();
        Path installDirectory = normalizedLauncher.getParent();
        if (installDirectory == null) {
            throw new UpdateException("Frostguard restart launcher has no installation directory");
        }
        Path scriptPath = installer.resolveSibling(installer.getFileName() + HANDOFF_SCRIPT_SUFFIX)
                .toAbsolutePath()
                .normalize();
        try {
            Files.writeString(scriptPath, createHandoffCmdScript(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UpdateException("Could not write the installer handoff script", exception);
        }
        String tokenValue = UUID.randomUUID().toString();
        Path tokenPath = installer.resolveSibling(installer.getFileName() + ".handoff-ready");
        try {
            Files.deleteIfExists(tokenPath);
        } catch (IOException exception) {
            throw new UpdateException("Could not clear an earlier installer handoff token", exception);
        }
        List<String> command = createCmdCommand(scriptPath);
        Map<String, String> environment = Map.of(
                INSTALLER_ENV, installer.toAbsolutePath().normalize().toString(),
                PID_ENV, Long.toString(parentPid),
                TOKEN_PATH_ENV, tokenPath.toAbsolutePath().normalize().toString(),
                TOKEN_VALUE_ENV, tokenValue,
                RESTART_LAUNCHER_ENV, normalizedLauncher.toString(),
                INSTALL_DIR_ENV, installDirectory.toString(),
                WORKSPACE_ENV, workspaceRoot.toAbsolutePath().normalize().toString());
        try {
            processStarter.start(command, environment);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(scriptPath);
            } catch (IOException ignored) {
            }
            throw new UpdateException(
                    "Could not start the external installer handoff: " + exception.getMessage(), exception);
        }
        return new HandoffSession() {
            @Override
            public void authorize() throws UpdateException {
                Path temporary = tokenPath.resolveSibling(tokenPath.getFileName() + ".tmp");
                try {
                    Files.writeString(temporary, tokenValue);
                    try {
                        Files.move(temporary, tokenPath, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                        Files.move(temporary, tokenPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    throw new UpdateException("Could not authorize the installer handoff", exception);
                }
            }

            @Override
            public void cancel() {
                try {
                    Files.deleteIfExists(tokenPath);
                    Files.deleteIfExists(tokenPath.resolveSibling(tokenPath.getFileName() + ".tmp"));
                    Files.deleteIfExists(scriptPath);
                } catch (IOException ignored) {
                }
            }
        };
    }

    static String createHandoffCmdScript() {
        return """
                @echo off
                setlocal EnableExtensions DisableDelayedExpansion
                set "PARENT_EXITED=0"
                set "TARGET_PID=%FROSTGUARD_UPDATE_PARENT_PID%"
                set "RESTART_LAUNCHER=%FROSTGUARD_UPDATE_RESTART_LAUNCHER%"
                set "INSTALLER_PATH=%FROSTGUARD_UPDATE_INSTALLER%"
                set "TOKEN_PATH=%FROSTGUARD_UPDATE_TOKEN_PATH%"
                set "TOKEN_VALUE=%FROSTGUARD_UPDATE_TOKEN_VALUE%"
                set "INSTALL_DIRECTORY=%FROSTGUARD_UPDATE_INSTALL_DIR%"
                set "WORKSPACE_ROOT=%FROSTGUARD_WORKSPACE%"
                set "WAIT_COUNT=0"
                set "FAILURE_DETAILS="

                goto :main

                :start_frostguard
                set "FROSTGUARD_UPDATE_INSTALLER="
                set "FROSTGUARD_UPDATE_PARENT_PID="
                set "FROSTGUARD_UPDATE_TOKEN_PATH="
                set "FROSTGUARD_UPDATE_TOKEN_VALUE="
                set "FROSTGUARD_UPDATE_RESTART_LAUNCHER="
                set "FROSTGUARD_UPDATE_INSTALL_DIR="
                if not exist "%RESTART_LAUNCHER%" (
                    set "FAILURE_DETAILS=The Frostguard restart launcher was not found."
                    exit /b 1
                )
                start "" "%RESTART_LAUNCHER%"
                exit /b 0

                :show_update_failure
                if defined WORKSPACE_ROOT if exist "%WORKSPACE_ROOT%" (
                    if not exist "%WORKSPACE_ROOT%\\logs" mkdir "%WORKSPACE_ROOT%\\logs" >nul 2>&1
                    >"%WORKSPACE_ROOT%\\logs\\update-failure.txt" echo %FAILURE_DETAILS%
                )
                rem Fixed mshta popup keeps the failure visible without an encoded script payload.
                mshta "javascript:var s=new ActiveXObject('WScript.Shell');s.Popup('Frostguard could not complete the update. Details were written to logs\\\\update-failure.txt in the workspace when available.',0,'Frostguard update failed',16);close()" >nul 2>&1
                exit /b 0

                :main
                :wait_for_parent
                tasklist /FI "PID eq %TARGET_PID%" 2>nul | find /I "%TARGET_PID%" >nul
                if errorlevel 1 goto parent_exited
                set /a WAIT_COUNT+=1
                if %WAIT_COUNT% GEQ 300 (
                    set "FAILURE_DETAILS=Frostguard did not exit before the update timeout."
                    goto fail
                )
                timeout /t 1 /nobreak >nul
                goto wait_for_parent

                :parent_exited
                set "PARENT_EXITED=1"
                if not exist "%TOKEN_PATH%" (
                    set "FAILURE_DETAILS=The update handoff was not authorized."
                    goto fail
                )
                set /p TOKEN_READ=<"%TOKEN_PATH%"
                if /I not "%TOKEN_READ%"=="%TOKEN_VALUE%" (
                    set "FAILURE_DETAILS=The update handoff authorization was invalid."
                    goto fail
                )
                del /f /q "%TOKEN_PATH%" >nul 2>&1

                msiexec /i "%INSTALLER_PATH%" /passive /norestart INSTALLDIR="%INSTALL_DIRECTORY%"
                set "MSI_EXIT=%ERRORLEVEL%"
                if not "%MSI_EXIT%"=="0" if not "%MSI_EXIT%"=="3010" (
                    set "FAILURE_DETAILS=Windows Installer exited with code %MSI_EXIT%. The previous installation was restored."
                    goto fail
                )
                call :start_frostguard
                if errorlevel 1 goto fail
                exit /b 0

                :fail
                if "%PARENT_EXITED%"=="1" (
                    call :start_frostguard
                    if errorlevel 1 set "FAILURE_DETAILS=%FAILURE_DETAILS% Frostguard could not be restarted."
                )
                call :show_update_failure
                exit /b 1
                """;
    }

    static List<String> createCmdCommand(Path scriptPath) {
        String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
        Path cmd = Path.of(systemRoot, "System32", "cmd.exe");
        return List.of(
                cmd.toString(),
                "/d",
                "/q",
                "/c",
                "call",
                scriptPath.toAbsolutePath().normalize().toString());
    }

    @FunctionalInterface
    interface DetachedProcessStarter {
        void start(List<String> command, Map<String, String> environment) throws IOException;
    }
}

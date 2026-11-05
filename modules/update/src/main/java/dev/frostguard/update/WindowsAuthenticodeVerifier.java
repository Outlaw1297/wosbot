package dev.frostguard.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Verifies an optional Authenticode publisher requirement.
 *
 * <p>Uses an on-disk {@code -File} script instead of {@code -EncodedCommand}.
 * Encoded PowerShell payloads are a common Behavioral Protection tripwire even
 * when the Frostguard binary itself is exempted.
 */
public final class WindowsAuthenticodeVerifier implements InstallerTrustVerifier {
    private static final String INSTALLER_ENV = "FROSTGUARD_UPDATE_INSTALLER";
    private static final String SCRIPT = """
            $signature = Get-AuthenticodeSignature -LiteralPath $env:%s
            if ($null -eq $signature.SignerCertificate) {
                Write-Output ($signature.Status.ToString() + "`t")
                exit 0
            }
            Write-Output ($signature.Status.ToString() + "`t" + $signature.SignerCertificate.Subject)
            """.formatted(INSTALLER_ENV);

    private final CommandRunner runner;

    public WindowsAuthenticodeVerifier() {
        this(new ProcessCommandRunner());
    }

    WindowsAuthenticodeVerifier(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public void verify(Path installer, SignatureRequirement requirement) throws UpdateException {
        if (!Files.isRegularFile(installer)) {
            throw new UpdateException("Verified installer does not exist: " + installer);
        }
        if (requirement == null) {
            return;
        }
        if (!"authenticode".equalsIgnoreCase(requirement.type())
                || requirement.publisher() == null || requirement.publisher().isBlank()) {
            throw new UpdateException("Windows update has an invalid Authenticode trust requirement");
        }
        Path script = null;
        try {
            script = Files.createTempFile("frostguard-authenticode-", ".ps1");
            Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            CommandRunner.CommandResult result = runner.run(List.of(
                            "powershell.exe", "-NoProfile", "-NonInteractive",
                            "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().normalize().toString()),
                    Map.of(INSTALLER_ENV, installer.toAbsolutePath().normalize().toString()), Duration.ofSeconds(30));
            if (result.exitCode() != 0) {
                throw new UpdateException("Authenticode verification command failed with exit " + result.exitCode());
            }
            String[] fields = result.output().split("\\t", 2);
            String status = fields.length > 0 ? fields[0].trim() : "";
            String subject = fields.length > 1 ? fields[1].trim() : "";
            if (!"valid".equalsIgnoreCase(status)) {
                throw new UpdateException(
                        "Installer Authenticode status is " + (status.isBlank() ? "unknown" : status));
            }
            if (!subject.equalsIgnoreCase(requirement.publisher().trim())) {
                throw new UpdateException("Installer signer does not match the required publisher");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UpdateException("Authenticode verification was interrupted", exception);
        } catch (IOException exception) {
            throw new UpdateException("Authenticode verification failed: " + exception.getMessage(), exception);
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (IOException ignored) {
                }
            }
        }
    }
}

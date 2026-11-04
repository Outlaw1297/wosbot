package dev.frostguard.update;

import dev.frostguard.api.runtime.RuntimeChannel;

import java.net.URI;
import java.util.Optional;

public final class UpdateEndpointResolver {
    public static final String STABLE_PROPERTY = "frostguard.update.manifest.stable";
    public static final String NIGHTLY_PROPERTY = "frostguard.update.manifest.nightly";
    public static final String NIGHTLY_DEV_PROPERTY = "frostguard.update.manifest.nightly_dev";

    public Optional<URI> resolve(RuntimeChannel channel) throws UpdateException {
        if (channel == null || channel == RuntimeChannel.DEVELOPMENT) {
            return Optional.empty();
        }
        String property = switch (channel) {
            case STABLE -> STABLE_PROPERTY;
            case NIGHTLY -> NIGHTLY_PROPERTY;
            case NIGHTLY_DEV -> NIGHTLY_DEV_PROPERTY;
            case DEVELOPMENT -> null;
        };
        if (property == null) {
            return Optional.empty();
        }
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new UpdateException("Configured update manifest URL is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new UpdateException("Configured update manifest URL must use HTTPS");
        }
        return Optional.of(uri);
    }
}

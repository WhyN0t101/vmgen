package io.vmgen.api.config;

public final class TemplateDefaults {

    private TemplateDefaults() {
        // Prevent instantiation
    }

    public static final int MIN_RAM_MB = 2048;
    public static final int MIN_CPU_CORES = 2;
    public static final String SSH_USER = "root";
    public static final int SSH_PORT = 22;
}
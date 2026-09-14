package com.javascriptobfuscator.jso;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Options for a single {@link JsoProtectorClient#protect} call.
 *
 * <p>Use {@link #builder()} for an ergonomic call site:
 * <pre>
 *   ProtectOptions.builder()
 *       .files(Map.of("app.js", source))
 *       .preset("balanced")
 *       .label(System.getenv("GIT_COMMIT"))
 *       .optionOverride("LockDomain", true)
 *       .build()
 * </pre>
 */
public final class ProtectOptions {

    private final Map<String, String> files;
    private final String preset;
    private final Map<String, Object> optionOverrides;
    private final String label;
    private final String projectName;
    private final String apiKey;
    private final String apiPassword;
    private final String endpoint;
    private final Duration timeout;

    private ProtectOptions(Builder b) {
        this.files = b.files == null ? Map.of() : Map.copyOf(b.files);
        this.preset = b.preset;
        this.optionOverrides = b.optionOverrides == null ? Map.of() : Map.copyOf(b.optionOverrides);
        this.label = b.label;
        this.projectName = b.projectName;
        this.apiKey = b.apiKey;
        this.apiPassword = b.apiPassword;
        this.endpoint = b.endpoint;
        this.timeout = b.timeout;
    }

    public Map<String, String> files() { return files; }
    public String preset() { return preset; }
    public Map<String, Object> optionOverrides() { return optionOverrides; }
    public String label() { return label; }
    public String projectName() { return projectName; }
    public String apiKey() { return apiKey; }
    public String apiPassword() { return apiPassword; }
    public String endpoint() { return endpoint; }
    public Duration timeout() { return timeout; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<String, String> files;
        private String preset = "balanced";
        private Map<String, Object> optionOverrides;
        private String label;
        private String projectName = "java-session";
        private String apiKey;
        private String apiPassword;
        private String endpoint;
        private Duration timeout;

        public Builder files(Map<String, String> files) { this.files = files; return this; }
        public Builder preset(String preset) { this.preset = preset; return this; }
        public Builder optionOverrides(Map<String, Object> options) { this.optionOverrides = options; return this; }
        public Builder optionOverride(String key, Object value) {
            if (this.optionOverrides == null) this.optionOverrides = new HashMap<>();
            this.optionOverrides.put(key, value);
            return this;
        }
        public Builder label(String label) { this.label = label; return this; }
        public Builder projectName(String name) { this.projectName = name; return this; }
        public Builder apiKey(String key) { this.apiKey = key; return this; }
        public Builder apiPassword(String pwd) { this.apiPassword = pwd; return this; }
        public Builder endpoint(String url) { this.endpoint = url; return this; }
        public Builder timeout(Duration d) { this.timeout = d; return this; }

        public ProtectOptions build() { return new ProtectOptions(this); }
    }
}

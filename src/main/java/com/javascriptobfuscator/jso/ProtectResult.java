package com.javascriptobfuscator.jso;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Outcome of a successful {@link JsoProtectorClient#protect} call.
 *
 * <ul>
 *   <li>{@code files} — protected source keyed by input filename.</li>
 *   <li>{@code buildId} — stable identifier for this protection run. Inject as
 *       a global into your runtime so production crash reports carry the matching BuildId.</li>
 *   <li>{@code polymorphismFingerprint} — short fingerprint over the protected output.
 *       Two consecutive obfuscations of identical input MUST produce different fingerprints
 *       when polymorphism is engaged.</li>
 *   <li>{@code report} — full Report JsonNode: identifier maps, enabled options,
 *       compatibility findings, release metadata.</li>
 *   <li>{@code raw} — complete raw response body.</li>
 * </ul>
 */
public final class ProtectResult {

    private final Map<String, String> files;
    private final String buildId;
    private final String polymorphismFingerprint;
    private final JsonNode report;
    private final JsonNode raw;

    public ProtectResult(Map<String, String> files,
                         String buildId,
                         String polymorphismFingerprint,
                         JsonNode report,
                         JsonNode raw) {
        this.files = files;
        this.buildId = buildId;
        this.polymorphismFingerprint = polymorphismFingerprint;
        this.report = report;
        this.raw = raw;
    }

    public Map<String, String> files() { return files; }
    public String buildId() { return buildId; }
    public String polymorphismFingerprint() { return polymorphismFingerprint; }
    public JsonNode report() { return report; }
    public JsonNode raw() { return raw; }
}

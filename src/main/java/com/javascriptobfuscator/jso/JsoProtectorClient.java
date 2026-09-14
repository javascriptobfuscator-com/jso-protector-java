package com.javascriptobfuscator.jso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Java client for the JavaScript Obfuscator HTTP API.
 *
 * <p>Mirrors the {@code protect()} surface of the jso-protector npm CLI and
 * the Python / Go / .NET / Ruby / PHP / Rust clients so behavior stays in
 * lockstep across runtimes. Uses {@code java.net.http.HttpClient} (JDK 11+)
 * plus Jackson for JSON.
 *
 * <p>Example:
 * <pre>
 *   var client = new JsoProtectorClient();
 *   var result = client.protect(ProtectOptions.builder()
 *           .files(Map.of("app.js", Files.readString(Path.of("dist/app.js"))))
 *           .preset("balanced")
 *           .label(System.getenv("GIT_COMMIT"))
 *           .build());
 *   for (var e : result.files().entrySet()) {
 *       Files.writeString(Path.of("dist-protected/" + e.getKey()), e.getValue());
 *   }
 *   System.out.println("BuildId: " + result.buildId());
 * </pre>
 */
public final class JsoProtectorClient {

    public static final String VERSION = "0.1.0";
    public static final String DEFAULT_ENDPOINT = "https://javascriptobfuscator.com/HttpApi.ashx";

    /** Shared preset table, identical to the other JSO language clients. */
    public static final Map<String, Map<String, Boolean>> PRESETS;
    static {
        Map<String, Map<String, Boolean>> p = new HashMap<>();
        p.put("standard", Map.of(
                "Compress", true,
                "EncodeStrings", true,
                "MoveStringsIntoArray", true,
                "NameMangling", true
        ));
        p.put("balanced", Map.of(
                "Compress", true,
                "EncodeStrings", true,
                "EncryptStrings", true,
                "MoveStringsIntoArray", true,
                "NameMangling", true,
                "DeepObfuscate", true,
                "FlatTransform", true,
                "CodeTransposition", true
        ));
        // Map.of has a 10-entry limit; use a HashMap for "maximum".
        Map<String, Boolean> max = new HashMap<>();
        max.put("Compress", true);
        max.put("EncodeStrings", true);
        max.put("EncryptStrings", true);
        max.put("MoveStringsIntoArray", true);
        max.put("NameMangling", true);
        max.put("DeepObfuscate", true);
        max.put("FlatTransform", true);
        max.put("CodeTransposition", true);
        max.put("ProtectMembers", true);
        max.put("RenameGlobals", true);
        max.put("MoveMembers", true);
        max.put("DeadCodeInsertion", true);
        p.put("maximum", Collections.unmodifiableMap(max));
        PRESETS = Collections.unmodifiableMap(p);
    }

    private final HttpClient httpClient;
    private final ObjectMapper json = new ObjectMapper();

    /** Default client: 180s timeout, JDK 11 HttpClient. */
    public JsoProtectorClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    /** Inject a custom HttpClient (proxy, TLS config, mock-server tests, etc.). */
    public JsoProtectorClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * POST the supplied files to JSO and return the protected output.
     *
     * @throws JsoProtectorException when credentials are missing, the request fails,
     *         or the API returns a non-Succeed Type. Messages are safe to log —
     *         the API key and password are never interpolated.
     */
    public ProtectResult protect(ProtectOptions options) throws JsoProtectorException {
        if (options == null) {
            throw new JsoProtectorException("ProtectOptions is required.", null, null);
        }
        String apiKey = resolveCredential(options.apiKey(), "JSO_API_KEY", "JAVASCRIPT_OBFUSCATOR_API_KEY");
        String apiPwd = resolveCredential(options.apiPassword(), "JSO_API_PASSWORD", "JAVASCRIPT_OBFUSCATOR_API_PASSWORD");
        if (apiKey.isEmpty() || apiPwd.isEmpty()) {
            throw new JsoProtectorException(
                    "JSO API credentials not configured. Set ProtectOptions.apiKey/apiPassword or export JSO_API_KEY / JSO_API_PASSWORD.",
                    null, null);
        }
        if (options.files() == null || options.files().isEmpty()) {
            throw new JsoProtectorException("At least one file is required.", null, null);
        }
        String presetName = (options.preset() == null || options.preset().isBlank())
                ? "balanced"
                : options.preset().toLowerCase();
        Map<String, Boolean> presetOpts = PRESETS.get(presetName);
        if (presetOpts == null) {
            throw new JsoProtectorException("Unknown preset \"" + options.preset() + "\".", null, null);
        }

        ObjectNode payload = json.createObjectNode();
        payload.put("APIKey", apiKey);
        payload.put("APIPwd", apiPwd);
        payload.put("Name", options.projectName() == null ? "java-session" : options.projectName());
        if (options.label() != null && !options.label().isBlank()) {
            payload.put("ReleaseLabel", options.label());
        }
        ArrayNode items = payload.putArray("Items");
        for (Map.Entry<String, String> e : options.files().entrySet()) {
            ObjectNode item = items.addObject();
            item.put("FileName", e.getKey());
            item.put("FileCode", e.getValue() == null ? "" : e.getValue());
        }
        // Preset first, explicit options override.
        for (Map.Entry<String, Boolean> e : presetOpts.entrySet()) {
            payload.put(e.getKey(), e.getValue());
        }
        if (options.optionOverrides() != null) {
            for (Map.Entry<String, Object> e : options.optionOverrides().entrySet()) {
                Object v = e.getValue();
                if (v == null) payload.putNull(e.getKey());
                else if (v instanceof Boolean) payload.put(e.getKey(), (Boolean) v);
                else if (v instanceof Integer) payload.put(e.getKey(), (Integer) v);
                else if (v instanceof Long) payload.put(e.getKey(), (Long) v);
                else if (v instanceof Double) payload.put(e.getKey(), (Double) v);
                else payload.put(e.getKey(), v.toString());
            }
        }

        String bodyText;
        try {
            bodyText = json.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new JsoProtectorException("Failed to encode payload: " + ex.getMessage(), null, null);
        }

        URI endpoint = URI.create(options.endpoint() == null ? DEFAULT_ENDPOINT : options.endpoint());
        HttpRequest req = HttpRequest.newBuilder(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(bodyText))
                .header("Content-Type", "text/json")
                .header("User-Agent", "jso-protector-java/" + VERSION)
                .timeout(options.timeout() == null ? Duration.ofSeconds(180) : options.timeout())
                .build();

        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new JsoProtectorException("Connection failed: " + ex.getMessage(), null, null);
        }

        int status = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();
        if (status < 200 || status >= 300) {
            String snippet = body.length() > 200 ? body.substring(0, 200) : body;
            throw new JsoProtectorException("HTTP " + status + ": " + snippet, null, null);
        }

        JsonNode parsed;
        try {
            parsed = json.readTree(body);
        } catch (Exception ex) {
            throw new JsoProtectorException("Malformed JSON in response: " + ex.getMessage(), null, null);
        }

        String type = parsed.path("Type").asText("");
        if (!"Succeed".equals(type)) {
            String msg = parsed.path("Message").asText("");
            if (msg.isEmpty()) msg = parsed.path("ErrorCode").asText("API request failed");
            String code = parsed.path("ErrorCode").asText(null);
            throw new JsoProtectorException(msg, type, code);
        }

        Map<String, String> files = new HashMap<>();
        for (JsonNode item : parsed.path("Items")) {
            String name = item.path("FileName").asText(null);
            String code = item.path("FileCode").asText(null);
            if (name != null && code != null) files.put(name, code);
        }
        if (files.isEmpty()) {
            throw new JsoProtectorException("API response did not include any protected files.", null, null);
        }
        JsonNode report = parsed.path("Report");
        String buildId = report.path("BuildId").asText(null);
        String fingerprint = report.path("PolymorphismFingerprint").asText(null);
        return new ProtectResult(files, buildId, fingerprint, report.isMissingNode() ? null : report, parsed);
    }

    private static String resolveCredential(String supplied, String... envVarNames) {
        if (supplied != null && !supplied.isBlank()) return supplied.trim();
        for (String name : envVarNames) {
            String v = System.getenv(name);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}

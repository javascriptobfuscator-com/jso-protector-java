# jso-protector — Java client

Java client for the [JavaScript Obfuscator](https://javascriptobfuscator.com/) HTTP API. Mirrors the `protect()` surface of every other JSO language client (Node, Python, Go, .NET, Ruby, PHP, Rust) so behavior stays in lockstep across runtimes.

Targets **JDK 11+** so we can use the built-in `java.net.http.HttpClient` without a third-party HTTP dependency. Uses Jackson for JSON.

## Install

### Maven

```xml
<dependency>
    <groupId>com.javascriptobfuscator</groupId>
    <artifactId>jso-protector</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("com.javascriptobfuscator:jso-protector:0.1.0")
```

## Quick start

```java
import com.javascriptobfuscator.jso.*;
import java.nio.file.*;
import java.util.Map;

var client = new JsoProtectorClient();
var result = client.protect(ProtectOptions.builder()
        .files(Map.of("app.js", Files.readString(Path.of("dist/app.js"))))
        .preset("balanced")
        .label(System.getenv("GIT_COMMIT"))   // null is fine — skips ReleaseLabel
        // apiKey/apiPassword default to JSO_API_KEY / JSO_API_PASSWORD env vars
        .build());

for (var entry : result.files().entrySet()) {
    Files.writeString(Path.of("dist-protected/" + entry.getKey()), entry.getValue());
}
System.out.println("BuildId: " + result.buildId());
System.out.println("Fingerprint: " + result.polymorphismFingerprint());
```

## Credentials

Reads `JSO_API_KEY` / `JSO_API_PASSWORD` (or the long-form `JAVASCRIPT_OBFUSCATOR_API_KEY` / `JAVASCRIPT_OBFUSCATOR_API_PASSWORD`) from the environment before falling back to `ProtectOptions.apiKey()` / `apiPassword()`. Use env vars on shared / CI machines.

## Presets and overrides

```java
ProtectOptions.builder()
    .files(files)
    .preset("balanced")                            // standard | balanced | maximum
    .optionOverride("LockDomain", true)
    .optionOverride("LockDomainList", "example.com")
    .build();
```

Explicit overrides win over preset defaults.

## Custom HttpClient

```java
HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build();
var client = new JsoProtectorClient(http);
```

Useful for tests, custom timeouts, proxies, TLS pinning.

## Error handling

```java
try {
    var result = client.protect(opts);
} catch (JsoProtectorException e) {
    // e.getMessage() is safe to log — API key/password never interpolated.
    log.error("JSO failed: type={} code={} {}", e.type(), e.errorCode(), e.getMessage());
}
```

## Tests

```bash
mvn test
```

JUnit 5 + a custom `HttpClient` stub that captures request bodies and returns canned responses. No network calls.

## License

UNLICENSED. Free companion to the JSO service.

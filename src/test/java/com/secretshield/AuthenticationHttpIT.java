package com.secretshield;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.secretshield.repository.UserRepository;
import com.secretshield.support.PostgresApiTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=synthetic-test-only-signing-key-never-use-outside-tests", "jwt.expiration=3600000",
        "app.frontend-origin=http://localhost:5173", "debug=false", "logging.level.root=WARN",
        "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false"
})
@EnabledOnOs(OS.WINDOWS)
class AuthenticationHttpIT extends PostgresApiTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void manualCheckScriptPassesAgainstRealHttpServerWithDisposableData() throws Exception {
        String fixture = UUID.randomUUID().toString().replace("-", "");
        Path output = Path.of("target", "auth-http-check.log").toAbsolutePath();
        ProcessBuilder command = new ProcessBuilder("powershell", "-NoProfile", "-File",
                Path.of("scripts", "check-auth.ps1").toAbsolutePath().toString(), "-Port", Integer.toString(port),
                "-CreateSyntheticUser", "-FixtureId", fixture);
        // Pin script/browser origin independently of ambient development configuration.
        command.environment().put("FRONTEND_ORIGIN", "http://localhost:5173");
        Process process = command.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Live HTTP checks must finish within timeout");
            assertEquals(0, process.exitValue(), "Manual check script failed; inspect ignored target/auth-http-check.log");
            String report = java.nio.file.Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(report.contains("All 14 authentication checks passed"));
            assertTrue(users.findByEmail(fixture + "@example.invalid").isPresent(), "Synthetic HTTP user must persist");
        } finally {
            if (process.isAlive()) { process.destroyForcibly().waitFor(10, TimeUnit.SECONDS); }
            users.findByEmail(fixture + "@example.invalid").ifPresent(users::delete);
        }
    }
}

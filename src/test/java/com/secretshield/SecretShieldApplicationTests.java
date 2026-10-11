package com.secretshield;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "jwt.secret=synthetic-test-only-signing-key-never-use-outside-tests",
        "debug=false", "logging.level.root=WARN"
})
@ActiveProfiles("test")
class SecretShieldApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.secretshield.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "jwt.secret=synthetic-test-only-signing-key-never-use-outside-tests",
        "jwt.expiration=3600000", "debug=false", "logging.level.root=WARN",
        "app.frontend-origin=http://localhost:5173",
        "spring.security.user.password=synthetic-unused-test-password",
        "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
public abstract class PostgresApiTest {
    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        var settings = DisposablePostgres.settings(System.getenv());
        DisposablePostgres.verify(settings);
        // These dynamic values override shell/application datasource settings.
        properties.add("spring.datasource.url", settings::url);
        properties.add("spring.datasource.username", settings::username);
        properties.add("spring.datasource.password", settings::password);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    protected ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected String responseToken(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}

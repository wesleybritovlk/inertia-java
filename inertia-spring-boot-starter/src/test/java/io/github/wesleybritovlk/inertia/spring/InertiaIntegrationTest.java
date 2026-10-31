package io.github.wesleybritovlk.inertia.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaHeaders;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * End-to-end starter integration over the Inertia protocol: auto-configuration, request
 * scoped engine, return value wiring, middleware rules, Thymeleaf root page, flash errors.
 */
@SpringBootTest(classes = InertiaIntegrationTest.TestApp.class, properties = "inertia.version=v-test")
@AutoConfigureMockMvc
class InertiaIntegrationTest {

    private static final String INERTIA = "true";

    @Autowired
    MockMvc mvc;

    // ---------------------------------------------------------------- root page

    @Test
    void fullVisitRendersBundledRootTemplateWithDataPagePayload() throws Exception {
        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("<div id=\"app\">"),
                        org.hamcrest.Matchers.containsString("data-page=\"app\""),
                        org.hamcrest.Matchers.containsString("type=\"application/json\""),
                        org.hamcrest.Matchers.containsString("\"Users/Index\""))))
                .andExpect(header().string(InertiaHeaders.VARY, InertiaHeaders.INERTIA));
    }

    @Test
    void inertiaVisitAnswersPageJsonProtocol() throws Exception {
        mvc.perform(get("/users").header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string(InertiaHeaders.INERTIA, "true"))
                .andExpect(header().string(InertiaHeaders.VARY, InertiaHeaders.INERTIA))
                .andExpect(jsonPath("$.component").value("Users/Index"))
                .andExpect(jsonPath("$.url").value("/users"))
                .andExpect(jsonPath("$.version").value("v-test"))
                .andExpect(jsonPath("$.props.users[0]").value("ana"))
                .andExpect(jsonPath("$.props.errors").exists());
    }

    @Test
    void lazyPropsStayOutUntilPartialMatchingRequestsThem() throws Exception {
        mvc.perform(get("/users").header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(jsonPath("$.props.secret").doesNotExist());

        mvc.perform(get("/users")
                        .header(InertiaHeaders.INERTIA, INERTIA)
                        .header(InertiaHeaders.PARTIAL_COMPONENT, "Users/Index")
                        .header(InertiaHeaders.PARTIAL_DATA, "secret"))
                .andExpect(jsonPath("$.props.secret").value("only-on-demand"))
                .andExpect(jsonPath("$.props.users").doesNotExist());
    }

    @Test
    void partialComponentMismatchFallsBackToFullResponse() throws Exception {
        mvc.perform(get("/users")
                        .header(InertiaHeaders.INERTIA, INERTIA)
                        .header(InertiaHeaders.PARTIAL_COMPONENT, "Other")
                        .header(InertiaHeaders.PARTIAL_DATA, "users"))
                .andExpect(jsonPath("$.props.users[1]").value("wei"))
                .andExpect(jsonPath("$.props.secret").doesNotExist());
    }

    @Test
    void versionConflictEscalatesToFullPageVisit() throws Exception {
        mvc.perform(get("/users?x=1")
                        .header(InertiaHeaders.INERTIA, INERTIA)
                        .header(InertiaHeaders.VERSION, "stale"))
                .andExpect(status().isConflict())
                .andExpect(header().string(InertiaHeaders.LOCATION, "/users?x=1"))
                .andExpect(header().string(InertiaHeaders.VERSION, "v-test"));
    }

    // ---------------------------------------------------------------- redirects

    @Test
    void externalRedirectsDifferPerVisitKind() throws Exception {
        mvc.perform(get("/external").header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(status().isConflict())
                .andExpect(header().string(InertiaHeaders.LOCATION, "https://sso.example/sign"));

        mvc.perform(get("/external"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://sso.example/sign"));
    }

    @Test
    void fragmentRedirectEscalatesToInertiaRedirect409() throws Exception {
        mvc.perform(get("/fragment").header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(status().isConflict())
                .andExpect(header().string(InertiaHeaders.REDIRECT, "/docs#pricing"));
    }

    @Test
    void putRedirectUpgradesToGet303() throws Exception {
        mvc.perform(put("/update").header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/users"));
    }

    // ---------------------------------------------------------------- flash validation

    @Test
    void validationErrorsSurviveRedirectThroughSessionFlash() throws Exception {
        var firstFlow = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/users").session(firstFlow))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/users"));

        // default bag unwrapped for plain pages
        MvcResult result = mvc.perform(get("/users")
                        .session(firstFlow)
                        .header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.hamcrest.MatcherAssert.assertThat(body, org.hamcrest.Matchers.containsString("\"errors\":{\"name\":\"is required\"}"));

        // second flow: X-Inertia-Error-Bag re-names the stored default bag before it is consumed
        var secondFlow = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/users").session(secondFlow)).andExpect(status().isFound());
        mvc.perform(get("/users")
                        .session(secondFlow)
                        .header(InertiaHeaders.INERTIA, INERTIA)
                        .header(InertiaHeaders.ERROR_BAG, "createUser"))
                .andExpect(jsonPath("$.props.errors.createUser.name").value("is required"));
    }

    @Test
    void sharedPropsCustomizerRunsOnEveryRender() throws Exception {
        mvc.perform(get("/users").header(InertiaHeaders.INERTIA, INERTIA))
                .andExpect(jsonPath("$.props.banner").value("inertia-java-bean"))
                .andExpect(jsonPath("$.sharedProps").exists());
    }

    // --------------------------------------------------------------- test app

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.ComponentScan
    static class TestApp {
    }

    @org.springframework.web.bind.annotation.RestController
    static class UsersController {

        private final Inertia inertia;

        UsersController(Inertia inertia) {
            this.inertia = inertia;
        }

        @GetMapping("/users")
        InertiaPage index() {
            return inertia.render("Users/Index", p -> p
                    .with("users", List.of("ana", "wei"))
                    .withLazy("secret", () -> "only-on-demand"));
        }

        @PostMapping("/users")
        InertiaResult create() {
            org.springframework.validation.BindException binding =
                    new org.springframework.validation.BindException(Integer.class, "invitations");
            binding.rejectValue("name", "NotBlank", "is required");
            ValidationErrors.store(inertia, binding, "default");
            return inertia.redirect("/users");
        }

        @GetMapping("/external")
        InertiaResult external() {
            return inertia.location("https://sso.example/sign");
        }

        @GetMapping("/fragment")
        InertiaResult fragment() {
            return inertia.redirect("/docs#pricing");
        }

        @PutMapping("/update")
        InertiaResult update() {
            return inertia.redirect("/users");
        }
    }

    @TestConfiguration
    static class SharedPropsConfig {
        @Bean
        InertiaSharedPropsCustomizer banner() {
            return inertia -> inertia.shared("banner", "inertia-java-bean");
        }
    }
}

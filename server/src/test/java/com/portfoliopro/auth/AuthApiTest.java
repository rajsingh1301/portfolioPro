package com.portfoliopro.auth;

import com.portfoliopro.risk.RiskSettingsRepository;
import com.portfoliopro.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@DisplayName("Auth API")
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiskSettingsRepository riskSettingsRepository;

    @BeforeEach
    void clean() {
        riskSettingsRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("signup creates the account with $100,000 and its risk settings")
    void signupCreatesFundedAccount() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("trader@example.com", "Password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("trader@example.com"))
                // A string, not a number: rule 1 holds at the boundary.
                .andExpect(jsonPath("$.user.cashBalance").value("100000.00"));

        User saved = userRepository.findByEmail("trader@example.com").orElseThrow();
        assertThat(saved.getCashBalance()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).doesNotContain("Password123");
        // Signup and risk settings share one transaction, so this row must exist.
        assertThat(riskSettingsRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("signup lowercases the email; login matches any case or padding")
    void emailIsNormalized() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Mixed.Case@Example.COM", "Password123")))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail("mixed.case@example.com")).isPresent();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  MIXED.CASE@example.com  ", "Password123")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("signup and login treat a padded, mixed-case email the same way: one account, whichever way it is typed")
    void emailIsNormalisedTheSameAtSignupAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Padded@Example.com  ", "Password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("padded@example.com"));

        for (String typed : new String[] {"padded@example.com", "  PADDED@example.com", "padded@example.com   "}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(typed, "Password123")))
                    .andExpect(status().isOk());
        }
        // The same address typed differently is the same account, so it is a duplicate.
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" PADDED@example.com ", "Password123")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an email that is only whitespace is still refused, and a password's whitespace is left alone")
    void blankEmailRefusedAndPasswordUntouched() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("     ", "Password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());

        // A password with leading and trailing spaces must log in only with those spaces.
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("spaces@example.com", "  Pass word 1  ")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("spaces@example.com", "Pass word 1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("spaces@example.com", "  Pass word 1  ")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a duplicate email is a business rule failure, not a conflict")
    void duplicateEmailReturns422() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", "Password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup@example.com", "Password123")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid input is rejected per field")
    void validationReportsEveryField() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("a wrong password and an unknown email are indistinguishable")
    void badCredentialsReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("real@example.com", "Password123")))
                .andExpect(status().isCreated());

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("real@example.com", "WrongPassword1")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody@example.com", "Password123")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Telling the two apart would let an attacker enumerate registered emails.
        assertThat(codeOf(wrongPassword)).isEqualTo(codeOf(unknownEmail));
    }

    @Test
    @DisplayName("/me needs a real token and returns the caller's own account")
    void meRequiresAValidToken() throws Exception {
        String token = tokenFor("me@example.com");

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.cashBalance").value("100000.00"));
    }

    private String tokenFor(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "Password123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static String codeOf(String json) {
        return json.replaceAll("(?s).*\"code\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static String body(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}

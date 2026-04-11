package com.vn.traffic.chatbot.common.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wave 0 — RED tests for GlobalExceptionHandler hardening.
 * Verifies DataAccessException (500/masked), ConstraintViolationException (400),
 * and NoResourceFoundException (404) handlers.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    private Environment env;

    @BeforeEach
    void setUp() {
        env = mock(Environment.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(env);

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void dataAccessExceptionReturnsMaskedDetailInProdProfile() throws Exception {
        when(env.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(true);

        mockMvc.perform(get("/test/data-access-error")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Failure during data access"));
    }

    @Test
    void dataAccessExceptionReturnsFullDetailInNonProdProfile() throws Exception {
        when(env.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(false);

        mockMvc.perform(get("/test/data-access-error")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void constraintViolationExceptionReturns400() throws Exception {
        mockMvc.perform(get("/test/constraint-violation")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noResourceFoundExceptionReturns404() throws Exception {
        mockMvc.perform(get("/test/no-resource")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test controller that deliberately throws specific exceptions for handler verification.
     */
    @RestController
    static class TestController {

        @GetMapping("/test/data-access-error")
        public String dataAccessError() {
            throw new DataIntegrityViolationException("DB error with internal details: table=kb_source");
        }

        @GetMapping("/test/constraint-violation")
        public String constraintViolation() {
            throw new ConstraintViolationException("Constraint violated", Set.of());
        }

        @GetMapping("/test/no-resource")
        public String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "/test/no-resource");
        }
    }
}

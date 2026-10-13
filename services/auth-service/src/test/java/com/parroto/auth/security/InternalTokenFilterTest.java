package com.parroto.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenFilterTest {

    private static final String TOKEN = "internal_token_for_unit_tests_0123456789";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN, objectMapper);

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void rejectsInternalCallWithoutHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(request("/internal/users/123"), chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"status\":401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsInternalCallWithWrongToken() throws Exception {
        MockHttpServletRequest request = request("/internal/users/123");
        request.addHeader(InternalTokenFilter.HEADER, TOKEN + "x");
        MockFilterChain chain = new MockFilterChain();

        assertThat(run(request, chain).getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsInternalCallWithCorrectToken() throws Exception {
        MockHttpServletRequest request = request("/internal/users/123");
        request.addHeader(InternalTokenFilter.HEADER, TOKEN);
        MockFilterChain chain = new MockFilterChain();

        assertThat(run(request, chain).getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ignoresNonInternalPaths() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run(request("/auth/profile"), chain);
        assertThat(chain.getRequest()).isNotNull();

        MockFilterChain lookalike = new MockFilterChain();
        run(request("/internalx/users"), lookalike);
        assertThat(lookalike.getRequest()).isNotNull();
    }

    @Test
    void failsFastWhenTokenMissing() {
        assertThatThrownBy(() -> new InternalTokenFilter("", objectMapper)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InternalTokenFilter(null, objectMapper)).isInstanceOf(IllegalStateException.class);
    }
}

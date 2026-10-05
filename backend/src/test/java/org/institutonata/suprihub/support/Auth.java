package org.institutonata.suprihub.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Login nos testes de integração. */
public final class Auth {

    public static final String ADMIN_EMAIL = "admin@teste.local";
    public static final String ADMIN_PASSWORD = "Admin@12345";

    private Auth() {
    }

    public static String bearer(MockMvc mvc, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    public static String admin(MockMvc mvc) throws Exception {
        return bearer(mvc, ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}

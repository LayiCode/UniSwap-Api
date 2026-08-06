package com.olamide.UniSwap.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Dto.RegisterRequest;
import com.olamide.UniSwap.Service.EmailVerificationService;
import com.olamide.UniSwap.Service.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Same full-stack harness as ProductControllerIntegrationTest: a real JWT is
// minted and exercised through the security filter chain so the tests prove
// favorites are scoped to the authenticated user, not just that the endpoints
// exist. @Transactional rolls every test back against the shared H2 DB.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FavoriteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailVerificationService emailVerificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registerAndGetToken(String username, String email) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username(username)
                .email(email)
                .password("password123")
                .phoneNumber("08012345678")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String signupCode = emailVerificationService.generateAndSendCode(email, VerificationPurpose.SIGNUP);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + signupCode + "\"}"))
                .andExpect(status().isOk());

        String loginCode = emailVerificationService.generateAndSendCode(email, VerificationPurpose.LOGIN);
        String body = mockMvc.perform(post("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private long createProduct(String token) throws Exception {
        ProductDTO listing = ProductDTO.builder()
                .title("Favorited MacBook")
                .description("Laptop for the favorites test")
                .price(new java.math.BigDecimal("50000.00"))
                .category("Electronics")
                .itemCondition("Neatly Used")
                .build();
        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(listing)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void addFavorite_isRejected_withoutAuth() throws Exception {
        mockMvc.perform(post("/api/favorites/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void favoritesList_isRejected_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_thenList_returnsTheFavoritedProduct() throws Exception {
        String token = registerAndGetToken("favor1", "favor1@student.lautech.edu.ng");
        long productId = createProduct(token);

        mockMvc.perform(post("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.content[0].favorited").value(true));
    }

    @Test
    void favorites_areIsolatedPerUser() throws Exception {
        String ownerToken = registerAndGetToken("favor2a", "favor2a@student.lautech.edu.ng");
        String otherToken = registerAndGetToken("favor2b", "favor2b@student.lautech.edu.ng");
        long productId = createProduct(ownerToken);

        mockMvc.perform(post("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        // The other user sees their own (empty) list, not the owner's.
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void addDuplicate_isRejected_withConflict() throws Exception {
        String token = registerAndGetToken("favor3", "favor3@student.lautech.edu.ng");
        long productId = createProduct(token);

        mockMvc.perform(post("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void addFavorite_forUnknownProduct_returnsNotFound() throws Exception {
        String token = registerAndGetToken("favor4", "favor4@student.lautech.edu.ng");

        mockMvc.perform(post("/api/favorites/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void remove_thenList_isEmpty() throws Exception {
        String token = registerAndGetToken("favor5", "favor5@student.lautech.edu.ng");
        long productId = createProduct(token);

        mockMvc.perform(post("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void removeNonExistentFavorite_returnsNotFound() throws Exception {
        String token = registerAndGetToken("favor6", "favor6@student.lautech.edu.ng");

        mockMvc.perform(delete("/api/favorites/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void browse_marksFavorited_onlyForTheAuthenticatedViewer() throws Exception {
        String token = registerAndGetToken("favor7", "favor7@student.lautech.edu.ng");
        long productId = createProduct(token);

        mockMvc.perform(post("/api/favorites/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Authenticated browse: the saved listing is flagged.
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].favorited").value(true));

        // Detail view is flagged too.
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));

        // Anonymous browse: favorited must default to false (no leak of the
        // owner's private favorites list).
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].favorited").value(false));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));
    }
}

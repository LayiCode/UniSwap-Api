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

// Full-stack test covering the JWT auth chain end to end: register -> get a
// real token -> use it on a protected endpoint -> Spring Security's
// JwtAuthenticationFilter validates it -> @AuthenticationPrincipal resolves
// the real user -> ProductService's ownership checks run against that
// identity. This is the test that proves a client can't fake being someone
// else, not just that the code compiles. @Transactional isolates each test.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailVerificationService emailVerificationService;

    // See AuthControllerIntegrationTest — Spring Boot 4 wires Jackson 3, so
    // this test uses a plain Jackson 2 mapper instead of an @Autowired bean.
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Registers, confirms the signup code, mints a login code, and exchanges it
    // for a real JWT — the same path a user walks in the browser.
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

        // readTree avoids deserializing the full AuthResponseDTO (which
        // contains LocalDateTime fields the plain mapper can't bind).
        return objectMapper.readTree(body).get("token").asText();
    }

    private ProductDTO sampleListing() {
        return ProductDTO.builder()
                .title("Used HP Laptop")
                .description("Barely used, 8GB RAM")
                .price(new java.math.BigDecimal("85000.00"))
                .category("Electronics")
                .itemCondition("Neatly Used")
                .build();
    }

    @Test
    void getAllProducts_isPubliclyAccessible_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createProduct_isRejected_withoutAuth() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleListing())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_succeeds_andAttributesSellerFromToken_notFromRequestBody() throws Exception {
        String token = registerAndGetToken("olamide", "olamide@student.lautech.edu.ng");

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleListing())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerUsername").value("olamide"))
                .andExpect(jsonPath("$.status").value("AVAILABLE")); // never SOLD on create
    }

    @Test
    void updateProduct_isRejected_whenRequesterIsNotTheOwner() throws Exception {
        String ownerToken = registerAndGetToken("owner", "owner@student.lautech.edu.ng");
        String intruderToken = registerAndGetToken("intruder", "intruder@student.lautech.edu.ng");

        String createResponse = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleListing())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long productId = objectMapper.readTree(createResponse).get("id").asLong();

        ProductDTO maliciousUpdate = ProductDTO.builder()
                .title("Hijacked listing")
                .price(new java.math.BigDecimal("1.00"))
                .category("Electronics")
                .itemCondition("Brand New")
                .build();

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isForbidden());
    }

    @Test
    void myListings_isRejected_withoutAuth() throws Exception {
        // Regression for the security fix: GET /api/products/my-listings must
        // NOT be caught by the broad GET /api/products/** permitAll rule.
        mockMvc.perform(get("/api/products/my-listings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myListings_returnsOnlyTheAuthenticatedUsersOwnProducts() throws Exception {
        String ownerToken = registerAndGetToken("seller1", "seller1@student.lautech.edu.ng");
        registerAndGetToken("seller2", "seller2@student.lautech.edu.ng");

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleListing())));

        mockMvc.perform(get("/api/products/my-listings")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sellerUsername").value("seller1"));
    }
}
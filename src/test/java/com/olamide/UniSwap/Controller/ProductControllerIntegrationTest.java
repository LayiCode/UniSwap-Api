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
import org.springframework.mock.web.MockMultipartFile;
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

    private void createProduct(String token, String title, String description,
                               String price, String category, String condition) throws Exception {
        ProductDTO listing = ProductDTO.builder()
                .title(title)
                .description(description)
                .price(new java.math.BigDecimal(price))
                .category(category)
                .itemCondition(condition)
                .build();
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(listing)))
                .andExpect(status().isCreated());
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

    @Test
    void getAllProducts_combinesSearchCategoryAndPriceRange() throws Exception {
        String token = registerAndGetToken("filter1", "filter1@student.lautech.edu.ng");
        createProduct(token, "MacBook Pro", "Apple laptop 16GB", "850000.00", "Electronics", "Brand New");
        createProduct(token, "Dell Laptop", "Dell XPS 13 laptop", "250000.00", "Electronics", "Neatly Used");
        createProduct(token, "HP Printer", "Office printer", "45000.00", "Electronics", "Neatly Used");

        // search matches title AND description; combined with category + price range
        mockMvc.perform(get("/api/products")
                        .param("search", "laptop")
                        .param("category", "Electronics")
                        .param("minPrice", "100000")
                        .param("maxPrice", "900000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2)); // MacBook + Dell
    }

    @Test
    void getAllProducts_filtersByCondition_andSortsByPriceAscending() throws Exception {
        String token = registerAndGetToken("filter2", "filter2@student.lautech.edu.ng");
        createProduct(token, "MacBook Pro", "Apple laptop", "850000.00", "Electronics", "Brand New");
        createProduct(token, "Dell Laptop", "Dell XPS", "250000.00", "Electronics", "Neatly Used");
        createProduct(token, "HP Printer", "Office printer", "45000.00", "Electronics", "Neatly Used");

        mockMvc.perform(get("/api/products")
                        .param("condition", "Neatly Used")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("HP Printer"))
                .andExpect(jsonPath("$.content[1].title").value("Dell Laptop"));
    }

    @Test
    void getAllProducts_rejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "bogus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllProducts_rejectsMinPriceAboveMaxPrice() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "100")
                        .param("maxPrice", "50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_withLeanFrontendPayload_succeeds() throws Exception {
        // Regression: the browser only sends the five form fields (see
        // ProductForm/ProductInput). Jackson 3 rejects a missing PRIMITIVE
        // field in a @RequestBody, so the boolean flags must be nullable
        // (Boolean) rather than primitives, or every listing created from the
        // app would 400.
        String token = registerAndGetToken("lean1", "lean1@student.lautech.edu.ng");

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lean Item\",\"description\":null,\"price\":100,"
                                + "\"category\":\"Books\",\"itemCondition\":\"Brand New\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Lean Item"))
                .andExpect(jsonPath("$.favorited").value(false));
    }

    // Smallest valid PNG (1x1 transparent) — FileStorageService verifies the
    // magic bytes, so the payload must be a real image, not random data.
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F,
            0x15, (byte) 0xC4, (byte) 0x89, 0, 0, 0, 0x0D, 0x49, 0x44, 0x41, 0x54,
            0x78, (byte) 0x9C, 0x63, 0, 1, 0, 0, 5, 0, 1, 0x0D, 0x0A, 0x2D,
            (byte) 0xB4, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42,
            0x60, (byte) 0x82
    };

    private long createProductAndGetId(String token, String title) throws Exception {
        ProductDTO listing = ProductDTO.builder()
                .title(title)
                .description("test")
                .price(new java.math.BigDecimal("100.00"))
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
    void uploadImages_storesAllPhotos_andSetsFirstAsCover() throws Exception {
        String token = registerAndGetToken("gallery1", "gallery1@student.lautech.edu.ng");
        long productId = createProductAndGetId(token, "Multi Photo Item");

        String body = mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(new MockMultipartFile("files", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("files", "b.png", "image/png", PNG))
                        .file(new MockMultipartFile("files", "c.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        // Cover (imageUrl) must mirror the first gallery entry.
        org.assertj.core.api.Assertions
                .assertThat(objectMapper.readTree(body).get("imageUrl").asText())
                .isEqualTo(objectMapper.readTree(body).get("imageUrls").get(0).asText());

        // Detail lookup exposes the full gallery too.
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrls.length()").value(3))
                .andExpect(jsonPath("$.imageUrl").isNotEmpty());
    }

    @Test
    void uploadImages_rejectsMoreThanFiveFiles() throws Exception {
        String token = registerAndGetToken("gallery2", "gallery2@student.lautech.edu.ng");
        long productId = createProductAndGetId(token, "Too Many Photos");

        MockMultipartFile[] six = new MockMultipartFile[6];
        for (int i = 0; i < six.length; i++) {
            six[i] = new MockMultipartFile("files", "img" + i + ".png", "image/png", PNG);
        }

        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(six[0]).file(six[1]).file(six[2])
                        .file(six[3]).file(six[4]).file(six[5])
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImages_isForbidden_forNonOwner() throws Exception {
        String ownerToken = registerAndGetToken("gallery3", "gallery3@student.lautech.edu.ng");
        String otherToken = registerAndGetToken("gallery4", "gallery4@student.lautech.edu.ng");
        long productId = createProductAndGetId(ownerToken, "Not Yours");

        mockMvc.perform(multipart("/api/products/{id}/images", productId)
                        .file(new MockMultipartFile("files", "a.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}
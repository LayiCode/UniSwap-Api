package com.olamide.UniSwap.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Dto.RegisterRequest;
import com.olamide.UniSwap.Repository.UserRepository;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Full-stack harness: real JWTs through the security filter chain, proving
// the moderation endpoints are locked to ROLE_ADMIN and that resolving a
// report can actually take a listing down.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserRepository userRepository;

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

        String signupCode = emailVerificationService.generateAndSendCode(email, VerificationPurpose.SIGNUP).code();
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + signupCode + "\"}"))
                .andExpect(status().isOk());

        String loginCode = emailVerificationService.generateAndSendCode(email, VerificationPurpose.LOGIN).code();
        String body = mockMvc.perform(post("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    private void makeAdmin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setAdmin(true);
            userRepository.save(user);
        });
    }

    private long createProduct(String token, String title) throws Exception {
        ProductDTO listing = ProductDTO.builder()
                .title(title)
                .description("For the report test")
                .price(new BigDecimal("20000.00"))
                .category("Books")
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

    private long createReport(String token, long productId, String reason, String details) throws Exception {
        String body = mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"reason\":\"" + reason
                                + "\",\"details\":\"" + details + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void create_isRejected_withoutAuth() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotReportOwnListing() throws Exception {
        String sellerToken = registerAndGetToken("rep1a", "rep1a@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "My Own Book");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateOpenReport_isConflict() throws Exception {
        String sellerToken = registerAndGetToken("rep2a", "rep2a@student.lautech.edu.ng");
        String reporterToken = registerAndGetToken("rep2b", "rep2b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Suspicious Watch");

        createReport(reporterToken, productId, "SPAM", "looks like spam");
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void moderationQueue_isAdminOnly() throws Exception {
        String sellerToken = registerAndGetToken("rep3a", "rep3a@student.lautech.edu.ng");
        String reporterToken = registerAndGetToken("rep3b", "rep3b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "To Report");
        long reportId = createReport(reporterToken, productId, "SCAM", "too good to be true");

        // A normal user cannot read the queue or decide reports.
        mockMvc.perform(get("/api/reports")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isForbidden());

        // An admin can.
        makeAdmin("rep3b@student.lautech.edu.ng");
        mockMvc.perform(get("/api/reports")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productTitle").value("To Report"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }

    @Test
    void resolve_keepsListing_browseable() throws Exception {
        String sellerToken = registerAndGetToken("rep4a", "rep4a@student.lautech.edu.ng");
        String reporterToken = registerAndGetToken("rep4b", "rep4b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Legit Item");
        long reportId = createReport(reporterToken, productId, "DUPLICATE", "false alarm");
        makeAdmin("rep4b@student.lautech.edu.ng");

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"removeProduct\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Listing untouched and still on the feed.
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"));
    }

    @Test
    void resolveWithRemove_hidesListing_fromEveryoneExceptOwnerAndAdmin() throws Exception {
        String sellerToken = registerAndGetToken("rep5a", "rep5a@student.lautech.edu.ng");
        String reporterToken = registerAndGetToken("rep5b", "rep5b@student.lautech.edu.ng");
        String adminToken = registerAndGetToken("rep5c", "rep5c@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Scam Textbook");
        long reportId = createReport(reporterToken, productId, "SCAM", "fake seller");
        makeAdmin("rep5c@student.lautech.edu.ng");

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"removeProduct\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Gone from the marketplace feed.
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        // Anonymous and other users get a 404 on the detail page...
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isNotFound());

        // ...but the seller can still manage it and the admin can audit it.
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void dismiss_marksReportDismissed() throws Exception {
        String sellerToken = registerAndGetToken("rep6a", "rep6a@student.lautech.edu.ng");
        String reporterToken = registerAndGetToken("rep6b", "rep6b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Fine Item");
        long reportId = createReport(reporterToken, productId, "OTHER", "not really a problem");
        makeAdmin("rep6b@student.lautech.edu.ng");

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        mockMvc.perform(get("/api/reports?status=DISMISSED")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void decidedReport_cannotBeDecidedAgain() throws Exception {
        String sellerToken = registerAndGetToken("rep7a", "rep7a@student.lautech.edu.ng");
        String reporterToken = registerAndGetToken("rep7b", "rep7b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Decided Item");
        long reportId = createReport(reporterToken, productId, "SPAM", "whatever");
        makeAdmin("rep7b@student.lautech.edu.ng");

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/reports/{id}", reportId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownStatusFilter_isBadRequest() throws Exception {
        String token = registerAndGetToken("rep8a", "rep8a@student.lautech.edu.ng");
        makeAdmin("rep8a@student.lautech.edu.ng");
        mockMvc.perform(get("/api/reports?status=BOGUS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}

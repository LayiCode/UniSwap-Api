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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Full-stack harness identical to the favorites/chat tests: real JWTs through
// the security filter chain, proving purchase requests are scoped to the
// buyer and the listing owner (and nobody else can decide them).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseRequestControllerIntegrationTest {

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

    private long createProduct(String token, String title) throws Exception {
        ProductDTO listing = ProductDTO.builder()
                .title(title)
                .description("For the purchase-request test")
                .price(new BigDecimal("50000.00"))
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

    private long createRequest(String token, long productId, String message) throws Exception {
        String body = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"message\":\"" + message + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void create_isRejected_withoutAuth() throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_isRejected_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/purchases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCannotRequestOwnListing() throws Exception {
        String sellerToken = registerAndGetToken("buyer1a", "buyer1a@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Own MacBook");

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestSoldProduct_isConflict() throws Exception {
        String sellerToken = registerAndGetToken("buyer1b", "buyer1b@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer1c", "buyer1c@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Sold MacBook");

        mockMvc.perform(patch("/api/products/{id}/sold", productId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_showsUpInSentAndReceivedLists() throws Exception {
        String sellerToken = registerAndGetToken("buyer2a", "buyer2a@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer2b", "buyer2b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Dell Laptop");

        createRequest(buyerToken, productId, "Can I pick it up tomorrow?");

        // Buyer's sent list.
        mockMvc.perform(get("/api/purchases?scope=sent")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].productId").value(productId))
                .andExpect(jsonPath("$.content[0].buyerUsername").value("buyer2b"))
                .andExpect(jsonPath("$.content[0].message").value("Can I pick it up tomorrow?"));

        // Seller's received list (default scope).
        mockMvc.perform(get("/api/purchases")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productTitle").value("Dell Laptop"))
                .andExpect(jsonPath("$.content[0].sellerUsername").value("buyer2a"));
    }

    @Test
    void duplicatePendingRequest_isConflict() throws Exception {
        String sellerToken = registerAndGetToken("buyer3a", "buyer3a@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer3b", "buyer3b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "HP Printer");

        createRequest(buyerToken, productId, "first");
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"message\":\"second\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonSellerCannotAcceptAnotherSellersRequest() throws Exception {
        String sellerToken = registerAndGetToken("buyer4a", "buyer4a@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer4b", "buyer4b@student.lautech.edu.ng");
        String strangerToken = registerAndGetToken("buyer4c", "buyer4c@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "iPad");
        long requestId = createRequest(buyerToken, productId, null);

        mockMvc.perform(post("/api/purchases/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerAccept_marksProductSold_andDeclinesOtherPending() throws Exception {
        String sellerToken = registerAndGetToken("buyer5a", "buyer5a@student.lautech.edu.ng");
        String buyer1Token = registerAndGetToken("buyer5b", "buyer5b@student.lautech.edu.ng");
        String buyer2Token = registerAndGetToken("buyer5c", "buyer5c@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Samsung Phone");
        long r1 = createRequest(buyer1Token, productId, "me first");
        long r2 = createRequest(buyer2Token, productId, "me second");

        mockMvc.perform(post("/api/purchases/{id}/accept", r1)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // The product is now sold.
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));

        // The first buyer's request was accepted, the second auto-declined.
        mockMvc.perform(get("/api/purchases?scope=sent")
                        .header("Authorization", "Bearer " + buyer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACCEPTED"));
        mockMvc.perform(get("/api/purchases?scope=sent")
                        .header("Authorization", "Bearer " + buyer2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DECLINED"));

        // The sold request cannot be decided again.
        mockMvc.perform(post("/api/purchases/{id}/decline", r2)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void sellerDecline_leavesListingAvailable() throws Exception {
        String sellerToken = registerAndGetToken("buyer6a", "buyer6a@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer6b", "buyer6b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Keyboard");
        long requestId = createRequest(buyerToken, productId, null);

        mockMvc.perform(post("/api/purchases/{id}/decline", requestId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        // The buyer may request again after a decline.
        createRequest(buyerToken, productId, "try again");
    }

    @Test
    void buyerCanCancelOwnPendingRequest() throws Exception {
        String sellerToken = registerAndGetToken("buyer7a", "buyer7a@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer7b", "buyer7b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Headphones");
        long requestId = createRequest(buyerToken, productId, "changed my mind");

        mockMvc.perform(post("/api/purchases/{id}/cancel", requestId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Someone who isn't the buyer can't cancel it.
        mockMvc.perform(post("/api/purchases/{id}/cancel", requestId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownScope_isBadRequest() throws Exception {
        String token = registerAndGetToken("buyer8a", "buyer8a@student.lautech.edu.ng");
        mockMvc.perform(get("/api/purchases?scope=bogus")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detail_reportsPurchaseRequested_onlyForTheRequester() throws Exception {
        String sellerToken = registerAndGetToken("buyer9a", "buyer9a@student.lautech.edu.ng");
        String buyerToken = registerAndGetToken("buyer9b", "buyer9b@student.lautech.edu.ng");
        long productId = createProduct(sellerToken, "Monitor");
        createRequest(buyerToken, productId, null);

        // The requester sees their pending request flagged.
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseRequested").value(true));

        // The seller and anonymous viewers do not.
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseRequested").value(false));
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseRequested").value(false));
    }
}

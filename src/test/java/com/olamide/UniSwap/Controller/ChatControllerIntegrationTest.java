package com.olamide.UniSwap.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olamide.UniSwap.Dto.RegisterRequest;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.UserRepository;
import com.olamide.UniSwap.Service.EmailVerificationService;
import com.olamide.UniSwap.Service.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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

    private long idFor(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    @Test
    void chatEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/chat/messages").param("with", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":1,\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_deliversToReceiverThread_andBuildsConversations() throws Exception {
        String buyerToken = registerAndGetToken("buyer", "buyer@example.com");
        String sellerToken = registerAndGetToken("seller", "seller@example.com");
        long sellerId = idFor("seller@example.com");
        long buyerId = idFor("buyer@example.com");

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":" + sellerId + ",\"message\":\"Is the laptop still available?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderUsername").value("buyer"))
                .andExpect(jsonPath("$.receiverUsername").value("seller"))
                .andExpect(jsonPath("$.read").value(false));

        mockMvc.perform(get("/api/chat/messages")
                        .header("Authorization", "Bearer " + sellerToken)
                        .param("with", String.valueOf(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Is the laptop still available?"));

        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].otherUsername").value("buyer"))
                .andExpect(jsonPath("$[0].lastMessage").value("Is the laptop still available?"));
    }

    @Test
    void getMessages_marksThemRead_andClearsUnreadCount() throws Exception {
        String buyerToken = registerAndGetToken("readbuyer", "readbuyer@example.com");
        registerAndGetToken("readseller", "readseller@example.com");
        long sellerId = idFor("readseller@example.com");
        long buyerId = idFor("readbuyer@example.com");

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":" + sellerId + ",\"message\":\"hello\"}"))
                .andExpect(status().isCreated());

        String sellerLogin = mockMvc.perform(post("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"readseller@example.com\",\"code\":\"" +
                                emailVerificationService.generateAndSendCode("readseller@example.com", VerificationPurpose.LOGIN) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sellerToken = objectMapper.readTree(sellerLogin).get("token").asText();

        mockMvc.perform(get("/api/chat/unread-count")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/chat/messages")
                        .header("Authorization", "Bearer " + sellerToken)
                        .param("with", String.valueOf(buyerId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chat/unread-count")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void sendMessage_toSelf_returns400() throws Exception {
        String token = registerAndGetToken("selfy", "selfy@example.com");
        long ownId = idFor("selfy@example.com");

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":" + ownId + ",\"message\":\"hi me\"}"))
                .andExpect(status().isBadRequest());
    }
}

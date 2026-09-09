package com.heritage.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.entity.User;
import com.heritage.platform.entity.UserSecurityQuestion;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.UserRepository;
import com.heritage.platform.repository.UserSecurityQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MyProfilePasswordChangeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSecurityQuestionRepository userSecurityQuestionRepository;

    @BeforeEach
    void setUp() {
        userSecurityQuestionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void profilePasswordChangeFlowWorksWithTwoCorrectSecurityAnswers() throws Exception {
        String username = "profile-password-user-" + System.nanoTime();
        String oldPassword = "password123";
        String newPassword = "newPassword123";
        createUserWithSecurityQuestions(username, oldPassword, List.of("Alpha", "Beta", "Gamma"));

        String token = loginAndExtractToken(username, oldPassword);

        mockMvc.perform(get("/api/my/profile/password-security-questions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].questionText").value("Question 1"));

        mockMvc.perform(post("/api/my/profile/password/change")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "newPassword", newPassword,
                                "answers", List.of("Alpha", "Wrong", "Gamma")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "password", newPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void profilePasswordChangeRejectsSamePassword() throws Exception {
        String username = "profile-password-same-user-" + System.nanoTime();
        String oldPassword = "password123";
        createUserWithSecurityQuestions(username, oldPassword, List.of("Alpha", "Beta", "Gamma"));

        String token = loginAndExtractToken(username, oldPassword);

        mockMvc.perform(post("/api/my/profile/password/change")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "newPassword", oldPassword,
                                "answers", List.of("Alpha", "Beta", "Wrong")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("must be different")));
    }

    private void createUserWithSecurityQuestions(String username, String password, List<String> answers) {
        User user = userRepository.save(new User(
                username,
                passwordEncoder.encode(password),
                "Security User",
                null,
                UserRole.USER,
                true
        ));

        userSecurityQuestionRepository.saveAll(List.of(
                UserSecurityQuestion.create(user, 1, "Question 1", passwordEncoder.encode(answers.get(0))),
                UserSecurityQuestion.create(user, 2, "Question 2", passwordEncoder.encode(answers.get(1))),
                UserSecurityQuestion.create(user, 3, "Question 3", passwordEncoder.encode(answers.get(2)))
        ));
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}

package com.heritage.platform;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityQuestionFlowTests {

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
    void registerFailsWhenSecurityQuestionCountIsNotThree() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", "user-" + System.nanoTime(),
                                "password", "password123",
                                "nickname", "New User",
                                "email", "new-user@example.com",
                                "phone", "12345678",
                                "securityQuestions", List.of(
                                        payload("questionText", "Q1?", "answer", "A1"),
                                        payload("questionText", "Q2?", "answer", "A2")
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Exactly 3 security questions are required.")));
    }

    @Test
    void registerFailsWhenAnySecurityAnswerIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", "user-" + System.nanoTime(),
                                "password", "password123",
                                "nickname", "New User",
                                "email", "new-user@example.com",
                                "phone", "12345678",
                                "securityQuestions", List.of(
                                        payload("questionText", "Q1?", "answer", "A1"),
                                        payload("questionText", "Q2?", "answer", ""),
                                        payload("questionText", "Q3?", "answer", "A3")
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Security answer cannot be empty.")));
    }

    @Test
    void passwordRecoverySucceedsWhenAtLeastTwoAnswersMatchExactly() throws Exception {
        String username = "recover-user-" + System.nanoTime();
        String originalPassword = "password123";
        String updatedPassword = "newPassword123";
        createUserWithSecurityQuestions(username, originalPassword, List.of("Alpha", "Beta", "Gamma"));

        mockMvc.perform(post("/api/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "newPassword", updatedPassword,
                                "answers", List.of("Alpha", "Wrong", "Gamma")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "password", updatedPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void passwordRecoveryFailsWhenOnlyOneAnswerMatches() throws Exception {
        String username = "recover-fail-user-" + System.nanoTime();
        createUserWithSecurityQuestions(username, "password123", List.of("Alpha", "Beta", "Gamma"));

        mockMvc.perform(post("/api/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "newPassword", "newPassword123",
                                "answers", List.of("Alpha", "Wrong", "WrongAgain")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("At least 2 answers must match exactly.")));
    }

    @Test
    void passwordRecoveryFailsWhenNewPasswordEqualsCurrentPassword() throws Exception {
        String username = "recover-same-password-user-" + System.nanoTime();
        String password = "password123";
        createUserWithSecurityQuestions(username, password, List.of("Alpha", "Beta", "Gamma"));

        mockMvc.perform(post("/api/auth/password-recovery/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "newPassword", password,
                                "answers", List.of("Alpha", "Beta", "Wrong")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Please sign out and sign in directly.")));
    }

    private User createUserWithSecurityQuestions(String username, String password, List<String> answers) {
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
        return user;
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}

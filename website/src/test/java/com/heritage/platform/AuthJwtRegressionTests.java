package com.heritage.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heritage.platform.entity.Category;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.CategoryRepository;
import com.heritage.platform.repository.CommentRepository;
import com.heritage.platform.repository.ContributorApplicationRepository;
import com.heritage.platform.repository.PostRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthJwtRegressionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ContributorApplicationRepository contributorApplicationRepository;

    @Autowired
    private UserSecurityQuestionRepository userSecurityQuestionRepository;

    private Category category;
    private User reviewer;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        contributorApplicationRepository.deleteAll();
        postRepository.deleteAll();
        categoryRepository.deleteAll();
        userSecurityQuestionRepository.deleteAll();
        userRepository.deleteAll();

        category = categoryRepository.save(Category.create("测试分类", "Test category"));
        reviewer = userRepository.save(new User(
                "reviewer-" + System.nanoTime(),
                passwordEncoder.encode("password123"),
                "Reviewer",
                null,
                UserRole.ADMIN,
                true
        ));
    }

    @Test
    void loginResponseContainsFrontendRequiredFields() throws Exception {
        String username = "jwt-user-" + System.nanoTime();
        String password = "password123";
        String nickname = "JWT User";

        userRepository.save(new User(
                username,
                passwordEncoder.encode(password),
                nickname,
                null,
                UserRole.USER,
                true
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Signed in successfully."))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void loginTokenCanAccessAuthenticatedCommentFlow() throws Exception {
        String username = "commenter-" + System.nanoTime();
        String password = "password123";
        String nickname = "Commenter";

        User commenter = userRepository.save(new User(
                username,
                passwordEncoder.encode(password),
                nickname,
                null,
                UserRole.USER,
                true
        ));
        Post published = createPublishedPost(commenter);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String token = extractToken(loginResult);

        mockMvc.perform(post("/api/posts/{postId}/comments", published.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("content", "JWT comment works"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("JWT comment works"))
                .andExpect(jsonPath("$.data.authorId").value(commenter.getId()))
                .andExpect(jsonPath("$.data.authorNickname").value(nickname));

        Post updatedPost = postRepository.findById(published.getId()).orElseThrow();
        assertThat(updatedPost.getCommentCount()).isEqualTo(1);
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        String username = "inactive-login-" + System.nanoTime();
        String password = "password123";

        userRepository.save(new User(
                username,
                passwordEncoder.encode(password),
                "Inactive Login",
                null,
                UserRole.USER,
                false
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This account has been disabled."));
    }

    private Post createPublishedPost(User author) {
        Post post = Post.create(
                "Published post " + System.nanoTime(),
                "Content",
                "https://example.com/cover.jpg",
                "Heritage item",
                "Suzhou",
                author,
                category
        );
        post.submitForReview();
        post.approve(reviewer);
        return postRepository.save(post);
    }

    private String extractToken(MvcResult result) throws Exception {
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

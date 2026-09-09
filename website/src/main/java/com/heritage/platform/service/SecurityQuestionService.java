package com.heritage.platform.service;

import com.heritage.platform.common.BadRequestException;
import com.heritage.platform.common.ResourceNotFoundException;
import com.heritage.platform.dto.request.SecurityQuestionRequest;
import com.heritage.platform.dto.response.SecurityQuestionResponse;
import com.heritage.platform.entity.User;
import com.heritage.platform.entity.UserSecurityQuestion;
import com.heritage.platform.repository.UserRepository;
import com.heritage.platform.repository.UserSecurityQuestionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SecurityQuestionService {

    public static final int REQUIRED_QUESTION_COUNT = 3;
    private static final int REQUIRED_MATCH_COUNT = 2;

    private final UserSecurityQuestionRepository userSecurityQuestionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthContextService authContextService;

    public SecurityQuestionService(UserSecurityQuestionRepository userSecurityQuestionRepository,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   AuthContextService authContextService) {
        this.userSecurityQuestionRepository = userSecurityQuestionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authContextService = authContextService;
    }

    @Transactional
    public void replaceQuestionsForUser(User user, List<SecurityQuestionRequest> securityQuestions) {
        validateQuestionSet(securityQuestions);
        userSecurityQuestionRepository.deleteAllByUser(user);
        userSecurityQuestionRepository.saveAll(toEntities(user, securityQuestions));
    }

    @Transactional(readOnly = true)
    public List<SecurityQuestionResponse> getQuestionsByUsername(String username) {
        User user = findUserByUsername(username);
        List<UserSecurityQuestion> questions = userSecurityQuestionRepository.findAllByUserOrderByQuestionOrderAsc(user);
        assertQuestionConfigured(questions);
        return toResponses(questions);
    }

    @Transactional
    public void resetPasswordByUsername(String username, List<String> answers, String newPassword) {
        User user = findUserByUsername(username);
        List<UserSecurityQuestion> questions = userSecurityQuestionRepository.findAllByUserOrderByQuestionOrderAsc(user);
        assertQuestionConfigured(questions);
        validateAnswerSet(answers);

        int matches = countMatches(answers, questions);

        if (matches < REQUIRED_MATCH_COUNT) {
            throw new BadRequestException("Security answer verification failed. At least 2 answers must match exactly.");
        }

        ensureNewPasswordDifferent(user, newPassword, true);
        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<SecurityQuestionResponse> getMySecurityQuestionsForPasswordChange() {
        User user = authContextService.requireActiveUser();
        List<UserSecurityQuestion> questions = userSecurityQuestionRepository.findAllByUserOrderByQuestionOrderAsc(user);
        assertQuestionConfigured(questions);
        return toResponses(questions);
    }

    @Transactional
    public void changeMyPasswordBySecurityQuestions(List<String> answers, String newPassword) {
        User user = authContextService.requireActiveUser();
        List<UserSecurityQuestion> questions = userSecurityQuestionRepository.findAllByUserOrderByQuestionOrderAsc(user);
        assertQuestionConfigured(questions);
        validateAnswerSet(answers);

        int matches = countMatches(answers, questions);
        if (matches < REQUIRED_MATCH_COUNT) {
            throw new BadRequestException("Security answer verification failed. At least 2 answers must match exactly.");
        }

        ensureNewPasswordDifferent(user, newPassword, false);
        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User findUserByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ResourceNotFoundException("The user could not be found."));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("This account has been disabled.");
        }
        return user;
    }

    private void validateQuestionSet(List<SecurityQuestionRequest> securityQuestions) {
        if (securityQuestions == null || securityQuestions.size() != REQUIRED_QUESTION_COUNT) {
            throw new BadRequestException("Exactly 3 security questions are required.");
        }
        for (SecurityQuestionRequest question : securityQuestions) {
            if (question == null) {
                throw new BadRequestException("Security question cannot be empty.");
            }
            String questionText = question.questionText();
            String answer = question.answer();
            if (questionText == null || questionText.trim().isEmpty()) {
                throw new BadRequestException("Security question cannot be empty.");
            }
            if (answer == null || answer.trim().isEmpty()) {
                throw new BadRequestException("Security answer cannot be empty.");
            }
        }
    }

    private void validateAnswerSet(List<String> answers) {
        if (answers == null || answers.size() != REQUIRED_QUESTION_COUNT) {
            throw new BadRequestException("Exactly 3 security answers are required.");
        }
        for (String answer : answers) {
            if (answer == null || answer.trim().isEmpty()) {
                throw new BadRequestException("Security answer cannot be empty.");
            }
        }
    }

    private List<UserSecurityQuestion> toEntities(User user, List<SecurityQuestionRequest> securityQuestions) {
        List<UserSecurityQuestion> entities = new ArrayList<>(REQUIRED_QUESTION_COUNT);
        for (int i = 0; i < REQUIRED_QUESTION_COUNT; i++) {
            SecurityQuestionRequest question = securityQuestions.get(i);
            entities.add(UserSecurityQuestion.create(
                    user,
                    i + 1,
                    question.questionText().trim(),
                    passwordEncoder.encode(question.answer())
            ));
        }
        return entities;
    }

    private void assertQuestionConfigured(List<UserSecurityQuestion> questions) {
        if (questions.size() != REQUIRED_QUESTION_COUNT) {
            throw new BadRequestException("Security questions are not configured for this account.");
        }
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BadRequestException("Username cannot be empty.");
        }
        return username.trim();
    }

    private List<SecurityQuestionResponse> toResponses(List<UserSecurityQuestion> questions) {
        return questions.stream()
                .map(question -> new SecurityQuestionResponse(question.getQuestionOrder(), question.getQuestionText()))
                .toList();
    }

    private int countMatches(List<String> answers, List<UserSecurityQuestion> questions) {
        int matches = 0;
        for (int i = 0; i < REQUIRED_QUESTION_COUNT; i++) {
            String answer = answers.get(i);
            String answerHash = questions.get(i).getAnswerHash();
            if (passwordEncoder.matches(answer, answerHash)) {
                matches++;
            }
        }
        return matches;
    }

    private void ensureNewPasswordDifferent(User user, String newPassword, boolean suggestLogin) {
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            if (suggestLogin) {
                throw new BadRequestException("The new password matches your current password. Please sign out and sign in directly.");
            }
            throw new BadRequestException("The new password must be different from your current password.");
        }
    }
}

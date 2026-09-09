package com.heritage.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "user_security_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_security_questions_user_order",
                columnNames = {"user_id", "question_order"}
        )
)
@Check(constraints = "question_order between 1 and 3")
public class UserSecurityQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "question_text", nullable = false, length = 255)
    private String questionText;

    @Column(name = "answer_hash", nullable = false, length = 255)
    private String answerHash;

    protected UserSecurityQuestion() {
    }

    private UserSecurityQuestion(User user, Integer questionOrder, String questionText, String answerHash) {
        this.user = user;
        this.questionOrder = questionOrder;
        this.questionText = questionText;
        this.answerHash = answerHash;
    }

    public static UserSecurityQuestion create(User user, Integer questionOrder, String questionText, String answerHash) {
        return new UserSecurityQuestion(user, questionOrder, questionText, answerHash);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Integer getQuestionOrder() {
        return questionOrder;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getAnswerHash() {
        return answerHash;
    }
}

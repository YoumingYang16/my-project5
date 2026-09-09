package com.heritage.platform.repository;

import com.heritage.platform.entity.User;
import com.heritage.platform.entity.UserSecurityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSecurityQuestionRepository extends JpaRepository<UserSecurityQuestion, Long> {

    List<UserSecurityQuestion> findAllByUserOrderByQuestionOrderAsc(User user);

    List<UserSecurityQuestion> findAllByUserIdOrderByQuestionOrderAsc(Long userId);

    void deleteAllByUser(User user);
}

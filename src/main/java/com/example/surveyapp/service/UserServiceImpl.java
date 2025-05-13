package com.example.surveyapp.service;

import com.example.surveyapp.model.User;
import com.example.surveyapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Transactional
    @Override
    public void registerUser(User user) throws Exception {
        logger.info("Registering user with email: {}", user.getEmail());
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            logger.warn("User with email {} already exists", user.getEmail());
            throw new Exception("Bu e-posta adresiyle bir kullanıcı zaten kayıtlı!");
        }
        userRepository.save(user);
        logger.info("User registered successfully: {}", user.getEmail());
    }

    @Override
    public User loginUser(String email, String password) throws Exception {
        logger.debug("Logging in user with email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("Kullanıcı bulunamadı!"));
        logger.debug("Found user: {}", user.getEmail());
        logger.debug("Stored password: {}, Provided password: {}", user.getPassword(), password);
        if (!user.getPassword().equals(password)) {
            logger.warn("Invalid password for user: {}", email);
            throw new Exception("Geçersiz şifre!");
        }
        logger.info("User authenticated successfully: {}", email);
        return user;
    }

    @Override
    public User findUserByEmail(String email) {
        logger.debug("Finding user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + email));
    }

    @Transactional
    @Override
    public void updateUserPoints(User user, int pointsToAdd) {
        logger.info("Updating points for user: {}, adding {} points", user.getEmail(), pointsToAdd);
        user.setPoints(user.getPoints() + pointsToAdd);
        userRepository.save(user);
        logger.info("User points updated successfully: {} now has {} points", user.getEmail(), user.getPoints());
    }

    @Override
    public List<User> findAllUsers() {
        logger.info("Fetching all users");
        List<User> users = userRepository.findAll();
        logger.info("Found {} users", users.size());
        return users;
    }
    
    @Transactional
    @Override
    public void deleteUser(Long userId) {
        logger.info("Deleting user with ID: {}", userId);
        if (!userRepository.existsById(userId)) {
            logger.warn("User with ID {} not found", userId);
            throw new RuntimeException("Kullanıcı bulunamadı: ID " + userId);
        }
        userRepository.deleteById(userId);
        logger.info("User with ID {} deleted successfully", userId);
    }
}
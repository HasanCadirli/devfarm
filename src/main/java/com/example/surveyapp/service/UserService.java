package com.example.surveyapp.service;

import com.example.surveyapp.model.User;

import java.util.List;

public interface UserService {
    void registerUser(User user) throws Exception;
    User loginUser(String email, String password) throws Exception;
    User findUserByEmail(String email);
    void updateUserPoints(User user, int pointsToAdd);
    List<User> findAllUsers();
    void deleteUser(Long userId);
}
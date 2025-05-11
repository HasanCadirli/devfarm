package com.example.surveyapp.controller;

import com.example.surveyapp.model.Survey;
import com.example.surveyapp.model.User;
import com.example.surveyapp.service.SurveyService;
import com.example.surveyapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private SurveyService surveyService;

    @GetMapping
    public String showAdminPanel(Model model, HttpSession session) {
        logger.info("GET /admin - Showing admin panel");
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }

        User user = userService.findUserByEmail(loggedInUserEmail);
        if (!"admin".equals(user.getRole())) {
            logger.warn("User {} does not have admin privileges, redirecting to surveys", loggedInUserEmail);
            return "redirect:/surveys?error=" + URLEncoder.encode("Bu sayfaya erişim yetkiniz yok.", StandardCharsets.UTF_8);
        }

        // Tüm kullanıcıları getir (admin hariç)
        List<User> users = userService.findAllUsers().stream()
                .filter(u -> !"admin".equals(u.getRole()))
                .collect(Collectors.toList());

        // Tüm anketleri getir
        List<Survey> surveys = surveyService.getAllActiveSurveys();

        model.addAttribute("users", users);
        model.addAttribute("surveys", surveys);
        model.addAttribute("loggedInUser", loggedInUserEmail);
        return "admin-panel";
    }

    @PostMapping("/deactivate-survey")
    public String deactivateSurvey(@RequestParam("surveyId") Long surveyId, HttpSession session) {
        logger.info("POST /admin/deactivate-survey - Deactivating survey with ID: {}", surveyId);
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }

        User user = userService.findUserByEmail(loggedInUserEmail);
        if (!"admin".equals(user.getRole())) {
            logger.warn("User {} does not have admin privileges", loggedInUserEmail);
            return "redirect:/surveys?error=" + URLEncoder.encode("Bu işlemi gerçekleştirme yetkiniz yok.", StandardCharsets.UTF_8);
        }

        try {
            surveyService.endSurvey(surveyId, user);
            return "redirect:/admin?success=" + URLEncoder.encode("Anket başarıyla pasif yapıldı!", StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            logger.error("Error deactivating survey: {}", e.getMessage(), e);
            return "redirect:/admin?error=" + URLEncoder.encode("Anket pasif yapılırken bir hata oluştu: " + e.getMessage(), StandardCharsets.UTF_8);
        }
    }


}
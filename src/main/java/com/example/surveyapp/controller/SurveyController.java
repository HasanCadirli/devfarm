package com.example.surveyapp.controller;

import com.example.surveyapp.model.Option;
import com.example.surveyapp.model.Question;
import com.example.surveyapp.model.Survey;
import com.example.surveyapp.model.User;
import com.example.surveyapp.model.Vote;
import com.example.surveyapp.repository.VoteRepository;
import com.example.surveyapp.service.SurveyService;
import com.example.surveyapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@Controller
@RequestMapping("/surveys")
public class SurveyController {

    private static final Logger logger = LoggerFactory.getLogger(SurveyController.class);

    @Autowired
    private SurveyService surveyService;

    @Autowired
    private UserService userService;

    @Autowired
    private VoteRepository voteRepository;

    @GetMapping
    public String listSurveys(@RequestParam(value = "success", required = false) String success, Model model, HttpSession session) {
        logger.info("GET /surveys - Listing surveys");
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }
        List<Survey> surveys = surveyService.getAllActiveSurveys();
        logger.info("Loaded {} surveys to display", surveys.size());
        User user = userService.findUserByEmail(loggedInUser);
        model.addAttribute("surveys", surveys);
        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("userPoints", user.getPoints());
        model.addAttribute("user", user); // Role için user nesnesini ekle
        model.addAttribute("success", success != null ? URLDecoder.decode(success, StandardCharsets.UTF_8) : null);
        logger.debug("Decoded success message: {}", success != null ? URLDecoder.decode(success, StandardCharsets.UTF_8) : "null");
        return "survey-list";
    }

    @GetMapping("/create")
    public String showCreateSurveyForm(Model model, HttpSession session) {
        logger.info("GET /surveys/create - Showing create survey form");
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }
        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("survey", new Survey());
        return "survey-create";
    }

    @PostMapping("/create")
    public String createSurvey(@ModelAttribute Survey survey, Model model, HttpSession session) {
        logger.info("POST /surveys/create - Creating survey with title: {}", survey.getTitle());
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }
        try {
            User user = userService.findUserByEmail(loggedInUserEmail);
            survey.setCreatedBy(user);
            survey.setActive(true);

            logger.debug("Received survey data: Title={}, Description={}, Questions={}", survey.getTitle(), survey.getDescription(), survey.getQuestions());
            if (survey.getQuestions() != null) {
                for (Question question : survey.getQuestions()) {
                    logger.debug("Processing question: Text={}, Options={}", question.getText(), question.getOptions());
                    question.setSurvey(survey);
                    if (question.getOptions() != null) {
                        // Boş seçenekleri filtrele
                        question.setOptions(question.getOptions().stream()
                                .filter(option -> option != null && option.getText() != null && !option.getText().trim().isEmpty())
                                .peek(option -> {
                                    option.setQuestion(question);
                                    option.setVoteCount(0);
                                })
                                .collect(Collectors.toList()));
                        logger.debug("Filtered options for question: {}", question.getOptions());
                    }
                }
            }
            surveyService.createSurvey(survey, user);
            logger.info("Survey created successfully with title: {} and {} questions", survey.getTitle(), survey.getQuestions() != null ? survey.getQuestions().size() : 0);
            return "redirect:/surveys?success=" + URLEncoder.encode("Anket başarıyla oluşturuldu!", StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error creating survey: {}", e.getMessage(), e);
            model.addAttribute("error", "Anket oluşturma sırasında bir hata oluştu: " + e.getMessage());
            model.addAttribute("survey", survey);
            return "survey-create";
        }
    }

    @GetMapping("/{id}")
    public String showSurveyDetails(@PathVariable(name = "id") Long id, Model model, HttpSession session) {
        logger.info("GET /surveys/{} - Starting simplified survey detail view", id);
        
        try {
            // Kullanıcı kontrolü
            String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
            if (loggedInUserEmail == null) {
                logger.warn("No logged-in user, redirecting to login");
                return "redirect:/login?error=" + URLEncoder.encode("Lütfen giriş yapın", StandardCharsets.UTF_8);
            }
            
            // Temel işlemler
            User currentUser = userService.findUserByEmail(loggedInUserEmail);
            Survey survey = null;
            
            try {
                // Anket yükleme
                survey = surveyService.getSurveyById(id);
                
                if (survey == null) {
                    logger.warn("Survey not found with ID: {}", id);
                    return "redirect:/surveys?error=" + URLEncoder.encode("Anket bulunamadı.", StandardCharsets.UTF_8);
                }
                
                logger.info("Found survey: {}, with {} questions", 
                    survey.getTitle(), 
                    survey.getQuestions() != null ? survey.getQuestions().size() : 0);
            } catch (Exception e) {
                logger.error("Error retrieving survey: {}", e.getMessage());
                return "redirect:/surveys?error=" + URLEncoder.encode("Anket yüklenirken hata oluştu: " + e.getMessage(), StandardCharsets.UTF_8);
            }
            
            // Model verilerini ekle
            model.addAttribute("survey", survey);
            model.addAttribute("loggedInUser", loggedInUserEmail);
            model.addAttribute("isOwner", survey.getCreatedBy() != null && 
                    currentUser != null && 
                    survey.getCreatedBy().getId().equals(currentUser.getId()));
            
            return "survey-detail";
        } catch (Exception e) {
            logger.error("Unexpected error in showSurveyDetails: {}", e.getMessage(), e);
            return "redirect:/surveys?error=" + URLEncoder.encode("Beklenmeyen bir hata oluştu.", StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/{id}/vote")
    public String voteSurvey(@PathVariable(name = "id") Long id, @RequestParam Map<String, String> allParams, HttpSession session) {
        logger.info("POST /surveys/{}/vote - Starting simplified vote handling", id);
        
        try {
            // Kullanıcı kontrolü
            String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
            if (loggedInUserEmail == null) {
                logger.warn("No logged-in user, redirecting to login");
                return "redirect:/login?error=" + URLEncoder.encode("Lütfen giriş yapın", StandardCharsets.UTF_8);
            }
            
            User user = userService.findUserByEmail(loggedInUserEmail);
            if (user == null) {
                logger.error("User not found: {}", loggedInUserEmail);
                return "redirect:/login?error=" + URLEncoder.encode("Kullanıcı bilgileri bulunamadı", StandardCharsets.UTF_8);
            }
            
            // Anket kontrolü 
            Survey survey = null;
            try {
                survey = surveyService.getSurveyById(id);
                if (survey == null) {
                    logger.warn("Survey not found with ID: {}", id);
                    return "redirect:/surveys?error=" + URLEncoder.encode("Anket bulunamadı", StandardCharsets.UTF_8);
                }
                
                if (!survey.getActive()) {
                    logger.warn("Attempted to vote on inactive survey: {}", id);
                    return "redirect:/surveys/" + id + "?error=" + URLEncoder.encode("Bu anket artık aktif değil", StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                logger.error("Error retrieving survey: {}", e.getMessage());
                return "redirect:/surveys?error=" + URLEncoder.encode("Anket yüklenirken hata oluştu", StandardCharsets.UTF_8);
            }
            
            // Oy işlemi - çok basit
            boolean voteRegistered = false;
            StringBuilder errors = new StringBuilder();
            
            for (String key : allParams.keySet()) {
                if (key != null && key.startsWith("option-")) {
                    try {
                        Long questionId = Long.parseLong(key.replace("option-", ""));
                        Long optionId = Long.parseLong(allParams.get(key));
                        
                        logger.info("Processing vote - Question ID: {}, Option ID: {}", questionId, optionId);
                        surveyService.vote(questionId, optionId, user);
                        voteRegistered = true;
                    } catch (Exception e) {
                        logger.error("Error processing vote: {}", e.getMessage());
                        errors.append(e.getMessage()).append("; ");
                    }
                }
            }
            
            if (!voteRegistered) {
                String errorMsg = errors.length() > 0 ? 
                        "Oy verme sırasında hatalar oluştu: " + errors.toString() : 
                        "Hiçbir oy kaydedilemedi";
                logger.warn("{} for survey: {}, user: {}", errorMsg, id, user.getEmail());
                return "redirect:/surveys/" + id + "?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
            }
            
            // Başarılı
            logger.info("Vote successful for survey: {}, user: {}", id, user.getEmail());
            return "redirect:/surveys/" + id + "?success=" + URLEncoder.encode("Oyunuz başarıyla kaydedildi!", StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Unexpected error during voting: {}", e.getMessage(), e);
            return "redirect:/surveys/" + id + "?error=" + URLEncoder.encode("Beklenmeyen bir hata oluştu", StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/{id}/results")
    public String showSurveyResults(@PathVariable(name = "id") Long id, Model model, HttpSession session) {
        logger.info("GET /surveys/{}/results - Showing survey results", id);
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }

        Survey survey = surveyService.getSurveyById(id);
        if (survey == null) {
            logger.warn("Survey not found with ID: {}", id);
            return "redirect:/surveys?error=" + URLEncoder.encode("Anket bulunamadı.", StandardCharsets.UTF_8);
        }

        // Yetki kontrolü: Sadece anket sahibi sonuçları görebilir
        if (!survey.getCreatedBy().getEmail().equals(loggedInUserEmail)) {
            logger.warn("User {} is not authorized to view results of survey {}", loggedInUserEmail, id);
            return "redirect:/surveys/" + id + "?error=" + URLEncoder.encode("Bu anketin sonuçlarını görme yetkiniz yok.", StandardCharsets.UTF_8);
        }

        // Anket sonuçlarını al
        List<Vote> votes = surveyService.getVotesBySurvey(id);
        User user = userService.findUserByEmail(loggedInUserEmail);
        model.addAttribute("survey", survey);
        model.addAttribute("votes", votes);
        model.addAttribute("loggedInUser", loggedInUserEmail);
        model.addAttribute("userPoints", user.getPoints());
        return "survey-results";
    }

    @GetMapping("/my-surveys")
    public String listMySurveys(Model model, HttpSession session) {
        logger.info("GET /surveys/my-surveys - Listing my surveys");
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }

        User user = userService.findUserByEmail(loggedInUserEmail);
        List<Survey> mySurveys = surveyService.getSurveysByUser(user);
        logger.info("Loaded {} surveys for user {}", mySurveys.size(), loggedInUserEmail);
        model.addAttribute("surveys", mySurveys);
        model.addAttribute("loggedInUser", loggedInUserEmail);
        model.addAttribute("userPoints", user.getPoints());
        return "my-surveys";
    }

    @PostMapping("/{id}/end")
    public String endSurvey(@PathVariable(name = "id") Long id, HttpSession session) {
        logger.info("POST /surveys/{}/end - Ending survey", id);
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }

        try {
            User user = userService.findUserByEmail(loggedInUserEmail);
            surveyService.endSurvey(id, user);
            logger.info("Survey {} ended by user {}", id, loggedInUserEmail);
            return "redirect:/surveys/my-surveys?success=" + URLEncoder.encode("Anket başarıyla sonlandırıldı!", StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            logger.error("Error ending survey: {}", e.getMessage(), e);
            return "redirect:/surveys/" + id + "?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }
}
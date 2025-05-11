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
    public String showSurveyDetails(@PathVariable Long id, Model model, HttpSession session) {
        logger.info("GET /surveys/{} - Showing survey details", id);
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }
        Survey survey = surveyService.getSurveyById(id);
        if (survey == null) {
            logger.warn("Survey not found with ID: {}", id);
            return "redirect:/surveys?error=" + URLEncoder.encode("Anket bulunamadı.", StandardCharsets.UTF_8);
        }
        if (survey.getQuestions() != null) {
            for (Question question : survey.getQuestions()) {
                logger.debug("Question ID: {}, Text: {}", question.getId(), question.getText());
                if (question.getOptions() != null) {
                    for (Option option : question.getOptions()) {
                        logger.debug("Option ID: {}, Text: {}, VoteCount: {}", option.getId(), option.getText(), option.getVoteCount());
                    }
                }
            }
        }
        User user = userService.findUserByEmail(loggedInUser);
        model.addAttribute("survey", survey);
        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("userPoints", user.getPoints());
        model.addAttribute("isOwner", survey.getCreatedBy().getEmail().equals(loggedInUser));
        return "survey-detail";
    }

    @PostMapping("/{id}/vote")
    public String voteSurvey(@PathVariable Long id, @RequestParam Map<String, String> allParams, HttpSession session) {
        logger.info("POST /surveys/{}/vote - Voting on survey", id);
        String loggedInUserEmail = (String) session.getAttribute("loggedInUser");
        if (loggedInUserEmail == null) {
            logger.warn("No logged-in user found, redirecting to login");
            return "redirect:/login?error=" + URLEncoder.encode("Lütfen önce giriş yapın.", StandardCharsets.UTF_8);
        }
        User user = userService.findUserByEmail(loggedInUserEmail);
        Survey survey = surveyService.getSurveyById(id);
        if (survey == null) {
            logger.warn("Survey not found with ID: {}", id);
            return "redirect:/surveys?error=" + URLEncoder.encode("Anket bulunamadı.", StandardCharsets.UTF_8);
        }

        try {
            // Kullanıcının bu ankete daha önce oy verip vermediğini kontrol edelim
            boolean hasVoted = false;
            for (Question question : survey.getQuestions()) {
                if (voteRepository.existsByUserAndQuestion(user, question)) {
                    hasVoted = true;
                    break;
                }
            }

            for (String key : allParams.keySet()) {
                if (key.startsWith("option-")) {
                    Long questionId = Long.parseLong(key.replace("option-", ""));
                    Long optionId = Long.parseLong(allParams.get(key));
                    surveyService.vote(questionId, optionId, user);
                    logger.info("User {} voted for option {} in question {}", user.getEmail(), optionId, questionId);
                }
            }

            // Eğer kullanıcı bu ankete ilk kez oy verdiyse puan ekle
            if (!hasVoted) {
                int pointsPerQuestion = 5;
                int totalPointsToAdd = survey.getQuestions() != null ? survey.getQuestions().size() * pointsPerQuestion : 0;
                if (totalPointsToAdd > 0) {
                    userService.updateUserPoints(user, totalPointsToAdd);
                    logger.info("User {} earned {} points for voting on survey {} with {} questions", user.getEmail(), totalPointsToAdd, id, survey.getQuestions().size());
                    return "redirect:/surveys/" + id + "?success=" + URLEncoder.encode("Oy başarıyla kullanıldı! " + totalPointsToAdd + " puan kazandınız!", StandardCharsets.UTF_8);
                }
            }

            return "redirect:/surveys/" + id + "?success=" + URLEncoder.encode("Oy başarıyla kullanıldı!", StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            logger.error("Error during voting: {}", e.getMessage(), e);
            return "redirect:/surveys/" + id + "?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/{id}/results")
    public String showSurveyResults(@PathVariable Long id, Model model, HttpSession session) {
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
    public String endSurvey(@PathVariable Long id, HttpSession session) {
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
package com.example.surveyapp.service;

import com.example.surveyapp.model.Option;
import com.example.surveyapp.model.Question;
import com.example.surveyapp.model.Survey;
import com.example.surveyapp.model.User;
import com.example.surveyapp.model.Vote;
import com.example.surveyapp.repository.OptionRepository;
import com.example.surveyapp.repository.QuestionRepository;
import com.example.surveyapp.repository.SurveyRepository;
import com.example.surveyapp.repository.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SurveyServiceImpl implements SurveyService {

    private static final Logger logger = LoggerFactory.getLogger(SurveyServiceImpl.class);

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Override
    public Survey createSurvey(Survey survey, User user) {
        logger.info("Creating survey: {} by user: {}", survey.getTitle(), user.getEmail());
        Survey savedSurvey = surveyRepository.save(survey);
        logger.info("Survey created successfully with ID: {}", savedSurvey.getId());
        return savedSurvey;
    }

    @Override
    public List<Survey> getAllActiveSurveys() {
        logger.info("Fetching all active surveys");
        List<Survey> surveys = surveyRepository.findByActiveTrue();
        logger.info("Found {} active surveys", surveys.size());
        if (surveys.isEmpty()) {
            logger.warn("No active surveys found in the database");
        } else {
            surveys.forEach(survey -> logger.debug("Survey: ID={}, Title={}, Active={}", survey.getId(), survey.getTitle(), survey.getActive()));
        }
        return surveys;
    }

    @Override
    public Survey getSurveyById(Long id) {
        logger.info("Fetching survey with ID: {}", id);
        return surveyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Survey not found with ID: " + id));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public void vote(Long questionId, Long optionId, User user) {
        logger.info("Voting for question ID: {} with option ID: {} by user: {}", questionId, optionId, user.getEmail());

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found with ID: " + questionId));
        if (voteRepository.existsByUserAndQuestion(user, question)) {
            logger.warn("User {} has already voted for question ID: {}", user.getEmail(), questionId);
            throw new RuntimeException("Bu soruya zaten oy verdiniz!");
        }

        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new RuntimeException("Option not found with ID: " + optionId));

        option.setVoteCount(option.getVoteCount() + 1);
        optionRepository.save(option);
        logger.info("Vote recorded for option ID: {}", optionId);

        Vote vote = new Vote();
        vote.setUser(user);
        vote.setQuestion(question);
        vote.setOption(option);
        voteRepository.save(vote);
        logger.info("Vote saved to Vote table for user: {}, question: {}, option: {}", user.getEmail(), questionId, optionId);
    }

    @Override
    public List<Vote> getVotesBySurvey(Long surveyId) {
        logger.info("Fetching votes for survey ID: {}", surveyId);
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Survey not found with ID: " + surveyId));
        List<Vote> votes = voteRepository.findByQuestionSurveyId(surveyId);
        logger.info("Found {} votes for survey ID: {}", votes.size(), surveyId);
        return votes;
    }

    @Override
    public List<Survey> getSurveysByUser(User user) {
        logger.info("Fetching surveys for user: {}", user.getEmail());
        List<Survey> surveys = surveyRepository.findByCreatedBy(user);
        logger.info("Found {} surveys for user: {}", surveys.size(), user.getEmail());
        if (surveys.isEmpty()) {
            logger.warn("No surveys found for user: {}", user.getEmail());
        } else {
            surveys.forEach(survey -> logger.debug("Survey: ID={}, Title={}, Active={}", survey.getId(), survey.getTitle(), survey.getActive()));
        }
        return surveys;
    }

    @Override
    public void endSurvey(Long surveyId, User user) {
        logger.info("Ending survey ID: {} by user: {}", surveyId, user.getEmail());
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Survey not found with ID: " + surveyId));

        if (!survey.getCreatedBy().getEmail().equals(user.getEmail()) && !"admin".equals(user.getRole())) {
            logger.warn("User {} is not authorized to end survey {}", user.getEmail(), surveyId);
            throw new RuntimeException("Bu anketi sonlandırma yetkiniz yok.");
        }

        survey.setActive(false);
        surveyRepository.save(survey);
        logger.info("Survey ID: {} ended successfully by user: {}", surveyId, user.getEmail());
    }
}
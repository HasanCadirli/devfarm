package com.example.surveyapp.config;

import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class WebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{AppConfig.class, LocalizationConfig.class};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return null; // AppConfig burada tekrarlanmamalı
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }
}
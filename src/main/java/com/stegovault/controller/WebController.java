package com.stegovault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// This controller redirects the root URL "/" to landing.html
@Controller
public class WebController {

    // When someone visits http://localhost:8080/
    // redirect them to landing.html instead of index.html
    @GetMapping("/")
    public String home() {
        return "redirect:/landing.html";
    }
}
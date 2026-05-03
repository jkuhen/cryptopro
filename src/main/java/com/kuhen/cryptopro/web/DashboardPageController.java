package com.kuhen.cryptopro.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardPageController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "redirect:/dashboard.html";
    }
}


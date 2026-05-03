package com.kuhen.cryptopro.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model")
public class ModelInfoController {

    private final ModelInfoProvider modelInfoProvider;

    public ModelInfoController(ModelInfoProvider modelInfoProvider) {
        this.modelInfoProvider = modelInfoProvider;
    }

    @GetMapping("/info")
    public ModelInfoResponse info() {
        return modelInfoProvider.currentModelInfo();
    }
}


package com.kova.aiticketinghub.ticket.ai;
import com.kova.aiticketinghub.ticketing.contracts.ai.AiRuntimeConfiguration;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/ai")
public class AiInternalController {
    private final AiSettingsService settings;
    public AiInternalController(AiSettingsService settings) { this.settings = settings; }
    @GetMapping("/configuration")
    public AiRuntimeConfiguration configuration() { return settings.runtime(); }
}

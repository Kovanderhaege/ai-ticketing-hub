package com.kova.aiticketinghub.ticket.ai;

import com.kova.aiticketinghub.ticketing.contracts.ai.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/admin/ai")
public class AiAdminController {
    private final AiSettingsService settings;
    private final AiAnalysisGateway gateway;
    public AiAdminController(AiSettingsService settings, AiAnalysisGateway gateway) {
        this.settings = settings; this.gateway = gateway;
    }
    @GetMapping("/configuration")
    public AiSettingsService.View get() { return settings.view(); }

    @PutMapping("/configuration")
    public AiSettingsService.View save(@Valid @RequestBody AiSettingsDraft draft, Authentication authentication) {
        return settings.save(draft, authentication.getName());
    }
    @PostMapping("/models")
    public List<String> models(@Valid @RequestBody AiSettingsDraft draft) {
        var configuration = settings.prepare(draft, false);
        if (!"OLLAMA".equals(configuration.provider())) throw new IllegalArgumentException();
        return gateway.models(configuration);
    }
    @PostMapping("/test-connection")
    public AiDiagnosticResult testConnection(@Valid @RequestBody AiSettingsDraft draft) {
        return gateway.test(settings.prepare(draft, true), false);
    }
    @PostMapping("/test-analysis")
    public AiDiagnosticResult testAnalysis(@Valid @RequestBody AiSettingsDraft draft) {
        return gateway.test(settings.prepare(draft, true), true);
    }
}

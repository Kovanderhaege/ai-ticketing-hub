package com.kova.aiticketinghub.analysis_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = com.kova.aiticketinghub.analysis.AnalysisServiceApplication.class,
        properties = "spring.kafka.listener.auto-startup=false")
class AnalysisServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.rishank.securedigitallocker;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@SpringBootTest
class SecuredigitallockerApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void doesNotRenderEmailOtpUnlockFormWhenMailDeliveryErrorIsPresent() {
		SpringTemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(new StringTemplateResolver());
		templateEngine.setDialect(new SpringStandardDialect());
		String template = "<form th:if=\"${link.accessMode == 'PIN' or (error == null and (codeSent == true or codeAlreadySent == true))}\">Access code</form>";

		Context context = new Context();
		context.setVariable("link", java.util.Map.of("accessMode", "EMAIL_OTP"));
		context.setVariable("error", "Email delivery is unavailable.");

		String rendered = templateEngine.process(template, context);

		assertFalse(rendered.contains("<form"));
	}

}

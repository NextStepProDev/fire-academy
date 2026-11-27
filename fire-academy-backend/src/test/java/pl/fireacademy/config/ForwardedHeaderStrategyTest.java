package pl.fireacademy.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;
import pl.fireacademy.BaseIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backend sits behind nginx and builds absolute URLs from the X-Forwarded-* headers nginx sets
 * (see the top of fire-academy-frontend/nginx.conf). That only happens while Spring's
 * ForwardedHeaderFilter is registered, which application.yml asks for with the "framework" strategy.
 * Boot 4.2 renamed the property (server.forward-headers-strategy → server.request.header.forwarded
 * .strategy); a typo or a future rename would drop the filter without a single error, and everything
 * Spring derives from the request (scheme, host, isSecure) would describe nginx's internal hop
 * instead of the public https address.
 */
class ForwardedHeaderStrategyTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldRegisterForwardedHeaderFilterWhenFrameworkStrategyIsConfigured() {
        boolean registered = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
            .anyMatch(bean -> bean.getFilter() instanceof ForwardedHeaderFilter);
        assertTrue(registered, "ForwardedHeaderFilter is not registered: check the forwarded strategy in application.yml");
    }
}

package pl.fireacademy.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import pl.fireacademy.domain.auth.AuthToken;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.auth.TokenType;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.security.JwtService;

import java.io.IOException;
import java.time.Instant;

@Component
@org.springframework.context.annotation.Profile("oauth2")
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.site-url:http://localhost:5174}")
    private String frontendUrl;

    private final JwtService jwtService;
    private final AuthTokenRepository authTokenRepository;

    public OAuth2SuccessHandler(JwtService jwtService, AuthTokenRepository authTokenRepository) {
        this.jwtService = jwtService;
        this.authTokenRepository = authTokenRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        String refreshTokenHash = jwtService.hashToken(refreshToken);
        Instant refreshExpiration = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());
        AuthToken storedRefreshToken = new AuthToken(user, refreshTokenHash, TokenType.REFRESH_TOKEN, refreshExpiration);
        authTokenRepository.save(storedRefreshToken);

        String baseUrl = frontendUrl.split(",")[0].trim();
        String targetUrl = UriComponentsBuilder.fromUriString(baseUrl + "/oauth-callback")
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .queryParam("expiresIn", jwtService.getAccessTokenExpirationSeconds())
            .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

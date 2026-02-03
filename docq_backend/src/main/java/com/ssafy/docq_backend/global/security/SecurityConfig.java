package com.ssafy.docq_backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import com.ssafy.docq_backend.global.security.filter.GuestLoginFilter;
import com.ssafy.docq_backend.global.security.filter.JsonUsernamePasswordAuthenticationFilter;
import com.ssafy.docq_backend.global.security.handler.CustomAuthenticationFailureHandler;
import com.ssafy.docq_backend.global.security.handler.CustomAuthenticationSuccessHandler;
import com.ssafy.docq_backend.global.security.provider.GuestAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final GuestAuthenticationProvider guestAuthenticationProvider;
    private final CustomAuthenticationSuccessHandler successHandler;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        AuthenticationManager authenticationManager = authenticationManager();

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configure(http))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // 1. Public Endpoints
                        .requestMatchers("/auth/login", "/auth/signup", "/guests/login").permitAll()
                        .requestMatchers("/ws-docq/**", "/actuator/**", "/error").permitAll()

                        // 2. Shared Authenticated Routes (Game, Room, Guest Info)
                        // 회원/게스트 모두 게임 참여 및 진행 가능
                        .requestMatchers("/rooms/**", "/game/**", "/guests/**").authenticated()

                        // 3. Board Read Capabilities (Shared)
                        // 게시글 목록, 상세, 퀴즈 미리보기, 댓글 목록 조회
                        .requestMatchers(HttpMethod.GET, "/boards/**").authenticated()

                        // 4. Restricted Write Capabilities (User Only)
                        // 게시글 작성/삭제, 좋아요, 댓글 작성/삭제
                        .requestMatchers("/boards/**").hasRole("USER")
                        // 댓글 (Top-level 경로가 있다면)
                        .requestMatchers("/comments/**").hasRole("USER")
                        // 퀴즈 생성/저장
                        .requestMatchers("/quizzes/**").hasRole("USER")
                        // 유저 프로필
                        .requestMatchers("/users/**").hasRole("USER")

                        // 5. Default
                        .anyRequest().authenticated())
                // 필터 추가
                .addFilterBefore(jsonWithUsernamePasswordFilter(authenticationManager),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(guestLoginFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);

        // GuestAuthenticationProvider는 @Component로 등록되어 주입받음
        return new ProviderManager(List.of(provider, guestAuthenticationProvider));
    }

    @Bean
    public JsonUsernamePasswordAuthenticationFilter jsonWithUsernamePasswordFilter(
            AuthenticationManager authenticationManager) {
        JsonUsernamePasswordAuthenticationFilter filter = new JsonUsernamePasswordAuthenticationFilter(
                new AntPathRequestMatcher("/auth/login", "POST"),
                objectMapper);
        filter.setAuthenticationManager(authenticationManager);
        filter.setAuthenticationSuccessHandler(successHandler);
        filter.setAuthenticationFailureHandler(failureHandler);

        // SecurityContext 영속성을 위해 Repository 설정 (세션에 저장되도록)
        SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
        filter.setSecurityContextRepository(contextRepository);

        return filter;
    }

    @Bean
    public GuestLoginFilter guestLoginFilter(AuthenticationManager authenticationManager) {
        GuestLoginFilter filter = new GuestLoginFilter(
                new AntPathRequestMatcher("/guests/login", "POST"),
                objectMapper);
        filter.setAuthenticationManager(authenticationManager);
        filter.setAuthenticationSuccessHandler(successHandler);
        filter.setAuthenticationFailureHandler(failureHandler);

        // SecurityContext 영속성을 위해 Repository 설정
        SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
        filter.setSecurityContextRepository(contextRepository);

        return filter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

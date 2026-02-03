package com.ssafy.docq_backend.global.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.global.security.token.GuestAuthenticationToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

public class GuestLoginFilter extends AbstractAuthenticationProcessingFilter {

    private final ObjectMapper objectMapper;

    public GuestLoginFilter(AntPathRequestMatcher defaultFilterProcessesUrl, ObjectMapper objectMapper) {
        super(defaultFilterProcessesUrl);
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException, ServletException {

        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }

        GuestDto.GuestLoginRequest loginRequest = objectMapper.readValue(request.getInputStream(),
                GuestDto.GuestLoginRequest.class);

        GuestAuthenticationToken authRequest = new GuestAuthenticationToken(loginRequest.getNickname());

        return this.getAuthenticationManager().authenticate(authRequest);
    }
}

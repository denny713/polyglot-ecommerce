package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.response.LoginResponse;
import com.ecommerce.auth.model.AuthToken;
import jakarta.enterprise.context.ApplicationScoped;

/** Converts the {@link AuthToken} domain model into a response DTO. */
@ApplicationScoped
public class LoginResponseMapper {

    public LoginResponse toResponse(AuthToken token) {
        return new LoginResponse(
                token.accessToken(),
                token.refreshToken(),
                token.tokenType(),
                token.expiresInSeconds(),
                token.refreshExpiresInSeconds(),
                token.scope());
    }
}

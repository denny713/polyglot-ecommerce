package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.response.LoginResponse;
import com.ecommerce.auth.model.AuthToken;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mengubah model domain {@link AuthToken} menjadi DTO response.
 *
 * <p>Padanan mapper request/response di project Spring Boot-mu — ditulis manual
 * karena field-nya sedikit; kalau nanti banyak, tinggal ganti isinya dengan
 * MapStruct tanpa mengubah pemanggilnya.
 */
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

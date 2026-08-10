package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.request.LoginRequest;
import com.ecommerce.auth.dto.response.LoginResponse;
import com.ecommerce.auth.mapper.LoginResponseMapper;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;
import com.ecommerce.auth.service.AuthenticationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint autentikasi — padanan {@code @RestController} di Spring Boot.
 *
 * <p>
 * Tugasnya cuma tiga: menerima request, menerjemahkan DTO ke model domain,
 * dan membungkus hasilnya jadi response HTTP. Tidak ada logika bisnis di sini,
 * dan tidak ada blok {@code try/catch} — kegagalan ditangani exception mapper
 * di package {@code com.ecommerce.auth.exception.handler}.
 */
@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthController {

    private final AuthenticationService authenticationService;
    private final LoginResponseMapper loginResponseMapper;

    @Inject
    public AuthController(AuthenticationService authenticationService,
            LoginResponseMapper loginResponseMapper) {
        this.authenticationService = authenticationService;
        this.loginResponseMapper = loginResponseMapper;
    }

    /**
     * Login dengan username dan password yang terdaftar di Keycloak.
     *
     * <pre>{@code
     * curl --location 'http://localhost:7110/api/auth/login' \
     *   --header 'Content-Type: application/json' \
     *   --data '{"username":"adminapp","password":"P@ssw0rd"}'
     * }</pre>
     */
    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        AuthToken token = authenticationService.doLogin(
                new UserCredentials(request.username(), request.password()));

        return Response.ok(loginResponseMapper.toResponse(token)).build();
    }
}

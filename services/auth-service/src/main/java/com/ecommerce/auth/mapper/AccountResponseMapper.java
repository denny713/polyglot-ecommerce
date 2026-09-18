package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.response.AccountResponse;
import com.ecommerce.auth.model.Account;
import jakarta.enterprise.context.ApplicationScoped;

/** Converts the {@link Account} domain model into the response DTO. */
@ApplicationScoped
public class AccountResponseMapper {

    public AccountResponse toResponse(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }

        return new AccountResponse(
                account.id(),
                account.username(),
                account.email(),
                account.firstName(),
                account.lastName(),
                account.enabled());
    }
}

package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.response.AccountResponse;
import com.ecommerce.auth.model.Account;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Converts the {@link Account} domain model into the response DTO.
 *
 * <p>
 * The account counterpart of {@link LoginResponseMapper}, and written by hand
 * for the same reason: there are only a few fields, and the body can be swapped
 * for MapStruct later without changing any caller.
 */
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

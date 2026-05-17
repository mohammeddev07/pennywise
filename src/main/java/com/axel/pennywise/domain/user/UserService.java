package com.axel.pennywise.domain.user;

import com.axel.pennywise.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repo;

    @Transactional
    public UserEntity getOrCreate(Authentication auth, String subject, String email) {
        log.debug("Getting or creating user: subject={}, email={}", subject, email);
        return repo.findByAuthSubjectAndDeletedAtIsNull(subject)
                .map(existing -> {
                    if (email != null && !email.isBlank() && existing.getEmail() == null) {
                        existing.setEmail(email);
                        return repo.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("Creating new user: subject={}, email={}", subject, email);
                    UserEntity u = new UserEntity();
                    u.setAuthSubject(subject);
                    u.setEmail(email);
                    UserEntity saved = repo.save(u);
                    log.info("User created: userId={}, subject={}", saved.getId(), subject);
                    return saved;
                });
    }

    public UserEntity require(UserEntity u) {
        if (u == null) {
            log.warn("User authorization failed: user is null");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }
        return u;
    }

    @Transactional
    public UserEntity updateDefaultCurrency(UserEntity user, String currencyCode) {
        String normalized = null;
        if (currencyCode != null && !currencyCode.isBlank()) {
            normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        }
        user.setDefaultCurrencyCode(normalized);
        return repo.save(user);
    }
}

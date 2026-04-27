package com.axel.pennywise.domain.idempotency;

import com.axel.pennywise.util.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repo;

    public record PriorResponse(int statusCode, String body) {}

    @Transactional
    public Optional<PriorResponse> tryGetPrior(UUID userId, String key, String requestBodyJson) {
        log.debug("Checking idempotency: userId={}, key={}", userId, key);

        String requestHash = Hashing.sha256(requestBodyJson);
        IdempotencyKeyId id = new IdempotencyKeyId(userId, key);

        return repo.findById(id).map(row -> {
            // If request_hash differs, you typically return 409. We'll enforce later.
            if (!row.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict: userId={}, key={}, prior_hash does not match current request", userId, key);
                return new PriorResponse(409, "{\"error\":{\"code\":\"IDEMPOTENCY_CONFLICT\",\"message\":\"Idempotency-Key reused with different request\"}}");
            }
            int code = row.getResponseCode() == null ? 409 : row.getResponseCode();
            log.info("Idempotency match: userId={}, key={}, returning prior response with status={}", userId, key, code);
            return new PriorResponse(code, row.getResponseBody());
        });
    }

    @Transactional
    public void storeResponse(UUID userId, String key, String requestBodyJson, int statusCode, String responseBody) {
        log.debug("Storing idempotency response: userId={}, key={}, statusCode={}", userId, key, statusCode);

        String requestHash = Hashing.sha256(requestBodyJson);
        IdempotencyKeyEntity row = new IdempotencyKeyEntity();
        row.setId(new IdempotencyKeyId(userId, key));
        row.setRequestHash(requestHash);
        row.setResponseCode(statusCode);
        row.setResponseBody(responseBody);
        repo.save(row);

        log.debug("Idempotency response stored: userId={}, key={}", userId, key);
    }
}

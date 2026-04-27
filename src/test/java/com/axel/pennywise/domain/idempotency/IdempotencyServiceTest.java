package com.axel.pennywise.domain.idempotency;

import com.axel.pennywise.util.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private IdempotencyKeyRepository repo;
    @InjectMocks private IdempotencyService service;

    private UUID userId;
    private String key;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        key = "idem-key-1";
    }

    @Test
    void tryGetPrior_returnsEmpty_whenNoRowExists() {
        when(repo.findById(any(IdempotencyKeyId.class))).thenReturn(Optional.empty());

        Optional<IdempotencyService.PriorResponse> resp =
                service.tryGetPrior(userId, key, "{\"a\":1}");

        assertTrue(resp.isEmpty());

        // Verify the ID used has the correct parts (without relying on equals/hashCode)
        ArgumentCaptor<IdempotencyKeyId> idCaptor = ArgumentCaptor.forClass(IdempotencyKeyId.class);
        verify(repo).findById(idCaptor.capture());
        assertIdParts(idCaptor.getValue(), userId, key);

        verifyNoMoreInteractions(repo);
    }

    @Test
    void tryGetPrior_returnsConflict_whenHashDiffers() {
        String requestBodyNow = "{\"a\":1}";

        IdempotencyKeyEntity row = new IdempotencyKeyEntity();
        row.setId(new IdempotencyKeyId(userId, key));
        row.setRequestHash("DIFFERENT_HASH");
        row.setResponseCode(201);
        row.setResponseBody("{\"ok\":true}");

        when(repo.findById(any(IdempotencyKeyId.class))).thenReturn(Optional.of(row));

        Optional<IdempotencyService.PriorResponse> resp =
                service.tryGetPrior(userId, key, requestBodyNow);

        assertTrue(resp.isPresent());
        assertEquals(409, resp.get().statusCode());
        assertTrue(resp.get().body().contains("IDEMPOTENCY_KEY_REUSED"));

        ArgumentCaptor<IdempotencyKeyId> idCaptor = ArgumentCaptor.forClass(IdempotencyKeyId.class);
        verify(repo).findById(idCaptor.capture());
        assertIdParts(idCaptor.getValue(), userId, key);

        verifyNoMoreInteractions(repo);
    }

    @Test
    void tryGetPrior_returnsPriorResponse_whenHashMatches_withStoredCode() {
        String requestBody = "{\"a\":1}";
        String hash = Hashing.sha256(requestBody);

        IdempotencyKeyEntity row = new IdempotencyKeyEntity();
        row.setId(new IdempotencyKeyId(userId, key));
        row.setRequestHash(hash);
        row.setResponseCode(201);
        row.setResponseBody("{\"id\":\"x\"}");

        when(repo.findById(any(IdempotencyKeyId.class))).thenReturn(Optional.of(row));

        Optional<IdempotencyService.PriorResponse> resp =
                service.tryGetPrior(userId, key, requestBody);

        assertTrue(resp.isPresent());
        assertEquals(201, resp.get().statusCode());
        assertEquals("{\"id\":\"x\"}", resp.get().body());

        ArgumentCaptor<IdempotencyKeyId> idCaptor = ArgumentCaptor.forClass(IdempotencyKeyId.class);
        verify(repo).findById(idCaptor.capture());
        assertIdParts(idCaptor.getValue(), userId, key);

        verifyNoMoreInteractions(repo);
    }

    @Test
    void tryGetPrior_defaultsTo409_whenHashMatches_butResponseCodeNull() {
        String requestBody = "{\"a\":1}";
        String hash = Hashing.sha256(requestBody);

        IdempotencyKeyEntity row = new IdempotencyKeyEntity();
        row.setId(new IdempotencyKeyId(userId, key));
        row.setRequestHash(hash);
        row.setResponseCode(null);
        row.setResponseBody("{\"prior\":true}");

        when(repo.findById(any(IdempotencyKeyId.class))).thenReturn(Optional.of(row));

        Optional<IdempotencyService.PriorResponse> resp =
                service.tryGetPrior(userId, key, requestBody);

        assertTrue(resp.isPresent());
        assertEquals(409, resp.get().statusCode());
        assertEquals("{\"prior\":true}", resp.get().body());

        ArgumentCaptor<IdempotencyKeyId> idCaptor = ArgumentCaptor.forClass(IdempotencyKeyId.class);
        verify(repo).findById(idCaptor.capture());
        assertIdParts(idCaptor.getValue(), userId, key);

        verifyNoMoreInteractions(repo);
    }

    @Test
    void storeResponse_savesRowWithComputedHashAndBody() {
        String requestBody = "{\"a\":1}";
        String responseBody = "{\"created\":true}";

        service.storeResponse(userId, key, requestBody, 201, responseBody);

        ArgumentCaptor<IdempotencyKeyEntity> captor = ArgumentCaptor.forClass(IdempotencyKeyEntity.class);
        verify(repo).save(captor.capture());
        verifyNoMoreInteractions(repo);

        IdempotencyKeyEntity saved = captor.getValue();
        assertNotNull(saved);

        // Don’t assert equals on IdempotencyKeyId; assert the parts instead
        assertIdParts(saved.getId(), userId, key);

        assertEquals(Hashing.sha256(requestBody), saved.getRequestHash());
        assertEquals(201, saved.getResponseCode());
        assertEquals(responseBody, saved.getResponseBody());
    }

    /**
     * Asserts IdempotencyKeyId contains the expected (userId, key) without relying on equals/hashCode.
     * Tries common getter names first; falls back to field reflection.
     */
    private static void assertIdParts(IdempotencyKeyId id, UUID expectedUserId, String expectedKey) {
        assertNotNull(id);

        UUID actualUserId = (UUID) readPropertyOrField(id, UUID.class,
                "getUserId", "userId", "userId", "uid", "getUid");
        String actualKey = (String) readPropertyOrField(id, String.class,
                "getKey", "key", "keyValue", "idempotencyKey", "getIdempotencyKey");

        assertEquals(expectedUserId, actualUserId, "userId mismatch in IdempotencyKeyId");
        assertEquals(expectedKey, actualKey, "key mismatch in IdempotencyKeyId");
    }

    private static Object readPropertyOrField(Object target, Class<?> type, String... candidates) {
        // Try methods first
        for (String name : candidates) {
            try {
                Method m = target.getClass().getMethod(name);
                Object v = m.invoke(target);
                if (v != null && type.isInstance(v)) return v;
            } catch (Exception ignored) {
                // Ignore and try next
            }
        }
        // Try fields
        for (String name : candidates) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v != null && type.isInstance(v)) return v;
            } catch (Exception ignored) {
                // Ignore and try next
            }
        }
        throw new AssertionError("Unable to read " + type.getSimpleName() + " from " + target.getClass().getSimpleName());
    }
}

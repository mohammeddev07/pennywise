package com.axel.pennywise.domain.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axel.pennywise.domain.transaction.query.AbstractPostgresIT;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class UserEmailUniquenessIT extends AbstractPostgresIT {

  @Autowired UserRepository users;

  private UserEntity user(String subjectPrefix, String email) {
    UserEntity u = new UserEntity();
    u.setAuthSubject(subjectPrefix + UUID.randomUUID());
    u.setEmail(email);
    return u;
  }

  @Test
  void emailLookupIsCaseInsensitive() {
    String email = UUID.randomUUID() + "@example.com";
    users.saveAndFlush(user("external:", email.toUpperCase()));

    assertTrue(users.existsActiveByEmail(email));
    assertFalse(users.existsActiveByEmail("nobody-" + email));
  }

  @Test
  void databaseRejectsASecondActiveAccountForTheSameEmailIgnoringCase() {
    String email = UUID.randomUUID() + "@example.com";
    users.saveAndFlush(user("local:", email));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> users.saveAndFlush(user("google:", email.toUpperCase())));
  }

  @Test
  void deletedAccountsDoNotBlockTheEmail() {
    String email = UUID.randomUUID() + "@example.com";
    UserEntity gone = user("local:", email);
    gone.setDeletedAt(OffsetDateTime.now());
    users.saveAndFlush(gone);

    users.saveAndFlush(user("google:", email));
  }
}

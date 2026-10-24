package com.axel.pennywise.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class UserServiceDisplayNameTest {

  private final UserRepository repo = mock(UserRepository.class);
  private final UserService service = new UserService(repo);

  @Test
  void trimsAndClearsBlankNames() {
    when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    UserEntity user = new UserEntity();

    assertThat(service.updateDisplayName(user, "  Sam ").getDisplayName()).isEqualTo("Sam");
    assertThat(service.updateDisplayName(user, "   ").getDisplayName()).isNull();
  }
}

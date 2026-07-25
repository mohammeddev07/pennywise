package com.axel.pennywise.domain.user;

import com.axel.pennywise.domain.common.AuditedEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String authSubject;

  @Column private String email;

  @Column private String passwordHash;

  @Column(length = 3)
  private String defaultCurrencyCode;
}

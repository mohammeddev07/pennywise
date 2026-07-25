package com.axel.pennywise.domain.book;

import com.axel.pennywise.domain.common.AuditedEntity;
import com.axel.pennywise.domain.user.UserEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
public class BookEntity extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id", nullable = false)
  private UserEntity owner;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 3)
  private String currencyCode;

  @Column(nullable = false)
  private String timezone;

  @Column(nullable = false)
  private long openingBalanceMinor;
}

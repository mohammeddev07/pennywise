package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.common.AuditedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class TransactionEntity extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id", nullable = false)
  private BookEntity book;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionType type;

  @Column(nullable = false)
  private long amountMinor;

  @Column(length = 120)
  private String title;

  @Column(nullable = false)
  private LocalDate occurredOn;

  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime occurredAt;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private CategoryEntity category;

  @Enumerated(EnumType.STRING)
  @Column
  private PaymentMethod paymentMethod;

  @Column private String note;
}

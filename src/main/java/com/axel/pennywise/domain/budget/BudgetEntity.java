package com.axel.pennywise.domain.budget;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
public class BudgetEntity extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id", nullable = false)
  private BookEntity book;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private CategoryEntity category;

  @Column(nullable = false)
  private LocalDate monthStart;

  @Column(nullable = false)
  private long amountMinor;
}

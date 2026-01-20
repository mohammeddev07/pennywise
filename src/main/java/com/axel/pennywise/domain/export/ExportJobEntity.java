package com.axel.pennywise.domain.export;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.common.AuditedEntity;
import com.axel.pennywise.domain.user.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ExportJobEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private UserEntity requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportStatus status = ExportStatus.PENDING;

    @Column
    private String fileName;

    @Column
    private String storageKey;

    @Column
    private String errorMessage;
}

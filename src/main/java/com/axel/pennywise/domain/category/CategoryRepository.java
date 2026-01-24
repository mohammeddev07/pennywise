package com.axel.pennywise.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
    List<CategoryEntity> findAllByBook_IdAndDeletedAtIsNull(UUID bookId);
    Optional<CategoryEntity> findByIdAndBook_IdAndDeletedAtIsNull(UUID id, UUID bookId);

    boolean existsByBook_IdAndTypeAndNameIgnoreCaseAndDeletedAtIsNull(UUID bookId, CategoryType type, String name);
    boolean existsByBook_Id(UUID bookId);

}

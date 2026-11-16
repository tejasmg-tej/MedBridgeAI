package com.medbridge.repository;

import com.medbridge.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    long countByCompletedTrue();
}

package com.medbridge.service;

import com.medbridge.dto.ChecklistItemDto;
import com.medbridge.dto.ChecklistUpdateResponse;
import com.medbridge.entity.ChecklistItem;
import com.medbridge.entity.Report;
import com.medbridge.exception.ResourceNotFoundException;
import com.medbridge.repository.ChecklistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ChecklistService {

    private final ChecklistItemRepository repository;
    private final ReportMapper mapper;

    public ChecklistService(ChecklistItemRepository repository, ReportMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ChecklistUpdateResponse setCompleted(Long itemId, boolean completed) {
        ChecklistItem item = repository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item " + itemId + " was not found."));
        item.setCompleted(completed);
        item.setCompletedAt(completed ? LocalDateTime.now() : null);
        repository.saveAndFlush(item);

        Report report = item.getReport();
        int total = report.getChecklistItems().size();
        int done = (int) report.getChecklistItems().stream().filter(ChecklistItem::isCompleted).count();
        ChecklistItemDto dto = mapper.toDto(item);
        return new ChecklistUpdateResponse(dto, report.getId(), total, done, ReportMapper.percent(done, total));
    }
}

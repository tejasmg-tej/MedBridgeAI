package com.medbridge.controller;

import com.medbridge.dto.ChecklistToggleRequest;
import com.medbridge.dto.ChecklistUpdateResponse;
import com.medbridge.service.ChecklistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checklist")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @PatchMapping("/{itemId}")
    public ChecklistUpdateResponse update(@PathVariable Long itemId, @Valid @RequestBody ChecklistToggleRequest request) {
        return checklistService.setCompleted(itemId, request.completed());
    }
}

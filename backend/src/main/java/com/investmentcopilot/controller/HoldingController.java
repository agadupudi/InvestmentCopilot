package com.investmentcopilot.controller;

import com.investmentcopilot.dto.HoldingCreateDto;
import com.investmentcopilot.dto.HoldingReadDto;
import com.investmentcopilot.dto.HoldingUpdateDto;
import com.investmentcopilot.dto.HoldingWithQuoteDto;
import com.investmentcopilot.service.HoldingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService service;

    @GetMapping
    public List<HoldingWithQuoteDto> list() {
        return service.listWithQuotes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingReadDto create(@RequestBody @Valid HoldingCreateDto dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<HoldingReadDto> update(
            @PathVariable Integer id,
            @RequestBody @Valid HoldingUpdateDto dto
    ) {
        return service.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}

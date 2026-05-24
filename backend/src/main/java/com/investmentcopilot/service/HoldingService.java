package com.investmentcopilot.service;

import com.investmentcopilot.dto.HoldingCreateDto;
import com.investmentcopilot.dto.HoldingReadDto;
import com.investmentcopilot.dto.HoldingUpdateDto;
import com.investmentcopilot.dto.HoldingWithQuoteDto;
import com.investmentcopilot.model.Holding;
import com.investmentcopilot.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class HoldingService {

    private final HoldingRepository repo;
    private final QuoteService quoteService;

    @Transactional(readOnly = true)
    public List<HoldingWithQuoteDto> listWithQuotes() {
        List<Holding> holdings = repo.findAllByOrderBySymbolAsc();
        if (holdings.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> prices = quoteService.getPrices(
                holdings.stream().map(Holding::getSymbol).toList()
        );
        return holdings.stream().map(h -> enrich(h, prices.get(h.getSymbol()))).toList();
    }

    public HoldingReadDto create(HoldingCreateDto dto) {
        Holding h = new Holding();
        h.setSymbol(dto.symbol().toUpperCase().strip());
        h.setQuantity(dto.quantity());
        h.setCostBasis(dto.costBasis());
        h.setNotes(dto.notes());
        return HoldingReadDto.from(repo.save(h));
    }

    public Optional<HoldingReadDto> update(Integer id, HoldingUpdateDto dto) {
        return repo.findById(id).map(h -> {
            if (dto.quantity() != null) h.setQuantity(dto.quantity());
            if (dto.costBasis() != null) h.setCostBasis(dto.costBasis());
            if (dto.notes() != null) h.setNotes(dto.notes());
            return HoldingReadDto.from(repo.save(h));
        });
    }

    public boolean delete(Integer id) {
        if (!repo.existsById(id)) {
            return false;
        }
        repo.deleteById(id);
        return true;
    }

    private HoldingWithQuoteDto enrich(Holding h, BigDecimal price) {
        BigDecimal marketValue = null;
        BigDecimal pnl = null;
        Double pnlPct = null;
        if (price != null) {
            marketValue = price.multiply(h.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costTotal = h.getCostBasis().multiply(h.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            pnl = marketValue.subtract(costTotal).setScale(2, RoundingMode.HALF_UP);
            if (costTotal.compareTo(BigDecimal.ZERO) > 0) {
                pnlPct = pnl.divide(costTotal, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).doubleValue();
            }
        }
        return new HoldingWithQuoteDto(
                h.getId(), h.getSymbol(), h.getQuantity(), h.getCostBasis(),
                h.getNotes(), h.getCreatedAt(), h.getUpdatedAt(),
                price, marketValue, pnl, pnlPct
        );
    }
}

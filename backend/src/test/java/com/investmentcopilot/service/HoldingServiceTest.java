package com.investmentcopilot.service;

import com.investmentcopilot.dto.HoldingCreateDto;
import com.investmentcopilot.dto.HoldingReadDto;
import com.investmentcopilot.dto.HoldingUpdateDto;
import com.investmentcopilot.dto.HoldingWithQuoteDto;
import com.investmentcopilot.model.Holding;
import com.investmentcopilot.repository.HoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock HoldingRepository repo;
    @Mock QuoteService quoteService;
    @InjectMocks HoldingService service;

    private static Holding holding(Integer id, String symbol, String qty, String cost) {
        Holding h = new Holding();
        h.setId(id);
        h.setSymbol(symbol);
        h.setQuantity(new BigDecimal(qty));
        h.setCostBasis(new BigDecimal(cost));
        h.setCreatedAt(OffsetDateTime.now());
        h.setUpdatedAt(OffsetDateTime.now());
        return h;
    }

    @Test
    void create_uppercases_and_trims_symbol() {
        when(repo.save(any(Holding.class))).thenAnswer(inv -> {
            Holding h = inv.getArgument(0);
            h.setId(1);
            h.setCreatedAt(OffsetDateTime.now());
            h.setUpdatedAt(OffsetDateTime.now());
            return h;
        });

        HoldingReadDto result = service.create(new HoldingCreateDto(
                "  aapl  ", new BigDecimal("10"), new BigDecimal("150"), "notes"
        ));

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.notes()).isEqualTo("notes");
    }

    @Test
    void update_writes_only_non_null_fields() {
        Holding existing = holding(1, "AAPL", "10", "150");
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<HoldingReadDto> result = service.update(1,
                new HoldingUpdateDto(new BigDecimal("20"), null, null));

        assertThat(result).isPresent();
        assertThat(result.get().quantity()).isEqualByComparingTo("20");
        assertThat(result.get().costBasis()).isEqualByComparingTo("150");
        assertThat(existing.getCostBasis()).isEqualByComparingTo("150");
    }

    @Test
    void update_updates_all_provided_fields() {
        Holding existing = holding(1, "AAPL", "10", "150");
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<HoldingReadDto> result = service.update(1,
                new HoldingUpdateDto(new BigDecimal("5"), new BigDecimal("200"), "updated"));

        assertThat(result).isPresent();
        assertThat(result.get().quantity()).isEqualByComparingTo("5");
        assertThat(result.get().costBasis()).isEqualByComparingTo("200");
        assertThat(result.get().notes()).isEqualTo("updated");
    }

    @Test
    void update_returns_empty_when_holding_missing() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        Optional<HoldingReadDto> result = service.update(999,
                new HoldingUpdateDto(new BigDecimal("1"), null, null));

        assertThat(result).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    void delete_returns_false_when_missing() {
        when(repo.existsById(999)).thenReturn(false);

        assertThat(service.delete(999)).isFalse();
        verify(repo, never()).deleteById(any());
    }

    @Test
    void delete_returns_true_when_present() {
        when(repo.existsById(1)).thenReturn(true);

        assertThat(service.delete(1)).isTrue();
        verify(repo).deleteById(1);
    }

    @Test
    void listWithQuotes_returns_empty_when_no_holdings() {
        when(repo.findAllByOrderBySymbolAsc()).thenReturn(List.of());

        assertThat(service.listWithQuotes()).isEmpty();
        verifyNoInteractions(quoteService);
    }

    @Test
    void listWithQuotes_enriches_with_prices_and_computes_pnl() {
        Holding h = holding(1, "AAPL", "10", "100");
        when(repo.findAllByOrderBySymbolAsc()).thenReturn(List.of(h));
        when(quoteService.getPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", new BigDecimal("150")));

        List<HoldingWithQuoteDto> result = service.listWithQuotes();

        assertThat(result).hasSize(1);
        HoldingWithQuoteDto dto = result.get(0);
        assertThat(dto.symbol()).isEqualTo("AAPL");
        assertThat(dto.lastPrice()).isEqualByComparingTo("150");
        assertThat(dto.marketValue()).isEqualByComparingTo("1500.00");
        assertThat(dto.unrealizedPnl()).isEqualByComparingTo("500.00");
        assertThat(dto.unrealizedPnlPct()).isEqualTo(50.0);
    }

    @Test
    void listWithQuotes_handles_null_price() {
        Holding h = holding(1, "XYZ", "10", "100");
        when(repo.findAllByOrderBySymbolAsc()).thenReturn(List.of(h));
        Map<String, BigDecimal> nullPrice = new HashMap<>();
        nullPrice.put("XYZ", null);
        when(quoteService.getPrices(any())).thenReturn(nullPrice);

        List<HoldingWithQuoteDto> result = service.listWithQuotes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lastPrice()).isNull();
        assertThat(result.get(0).marketValue()).isNull();
        assertThat(result.get(0).unrealizedPnl()).isNull();
        assertThat(result.get(0).unrealizedPnlPct()).isNull();
    }
}

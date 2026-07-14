package com.portfolio.financas.summary;

import com.portfolio.financas.summary.dto.MonthlyTotalResponse;
import com.portfolio.financas.transaction.MonthlyTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryHistoryServiceTest {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Mock
    private TransactionRepository transactionRepository;

    private static MonthlyTotalProjection projection(String yearMonth, BigDecimal total) {
        return new MonthlyTotalProjection() {
            @Override
            public String getYearMonth() {
                return yearMonth;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }

    @Test
    void mesesSemTransacoesSaoPreenchidosComZero() {
        when(transactionRepository.sumByMonthBetween(anyString(), anyString())).thenReturn(List.of());
        SummaryHistoryService service = new SummaryHistoryService(transactionRepository);

        List<MonthlyTotalResponse> result = service.history(3);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(item -> item.total().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void resultadoTemOMesMaisRecentePrimeiro() {
        when(transactionRepository.sumByMonthBetween(anyString(), anyString())).thenReturn(List.of());
        SummaryHistoryService service = new SummaryHistoryService(transactionRepository);

        List<MonthlyTotalResponse> result = service.history(3);

        YearMonth current = YearMonth.now();
        assertThat(result.get(0).yearMonth()).isEqualTo(current.format(YEAR_MONTH_FORMAT));
        assertThat(result.get(1).yearMonth()).isEqualTo(current.minusMonths(1).format(YEAR_MONTH_FORMAT));
        assertThat(result.get(2).yearMonth()).isEqualTo(current.minusMonths(2).format(YEAR_MONTH_FORMAT));
    }

    @Test
    void mesesComTransacoesUsamOTotalRetornadoPelaProjecao() {
        String currentKey = YearMonth.now().format(YEAR_MONTH_FORMAT);
        when(transactionRepository.sumByMonthBetween(anyString(), anyString()))
                .thenReturn(List.of(projection(currentKey, new BigDecimal("1234.56"))));
        SummaryHistoryService service = new SummaryHistoryService(transactionRepository);

        List<MonthlyTotalResponse> result = service.history(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).total()).isEqualByComparingTo("1234.56");
    }

    @Test
    void janelaDeUmMesRetornaApenasOMesAtual() {
        when(transactionRepository.sumByMonthBetween(anyString(), anyString())).thenReturn(List.of());
        SummaryHistoryService service = new SummaryHistoryService(transactionRepository);

        List<MonthlyTotalResponse> result = service.history(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).yearMonth()).isEqualTo(YearMonth.now().format(YEAR_MONTH_FORMAT));
    }
}

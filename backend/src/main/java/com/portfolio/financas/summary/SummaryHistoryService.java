package com.portfolio.financas.summary;

import com.portfolio.financas.summary.dto.MonthlyTotalResponse;
import com.portfolio.financas.transaction.MonthlyTotalProjection;
import com.portfolio.financas.transaction.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serie historica de gasto total por mes (T-2.3.2). A agregacao em si
 * (SUM + GROUP BY) acontece no banco via
 * TransactionRepository#sumByMonthBetween, que so retorna linhas para
 * meses com transacoes; este servico apenas preenche com zero os meses
 * da janela pedida que nao vieram no resultado -- preenchimento de
 * lacunas de calendario, nao agregacao de dados de transacao em memoria.
 */
@Service
public class SummaryHistoryService {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;

    public SummaryHistoryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * @param months tamanho da janela, mes atual incluso (ex: months=6
     *               retorna o mes atual e os 5 anteriores)
     * @return lista com `months` entradas, mes mais recente primeiro
     */
    public List<MonthlyTotalResponse> history(int months) {
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(months - 1L);

        Map<String, BigDecimal> totalsByMonth = new HashMap<>();
        for (MonthlyTotalProjection projection : transactionRepository.sumByMonthBetween(
                start.format(YEAR_MONTH_FORMAT), current.format(YEAR_MONTH_FORMAT))) {
            totalsByMonth.put(projection.getYearMonth(), projection.getTotal());
        }

        List<MonthlyTotalResponse> result = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            String key = current.minusMonths(i).format(YEAR_MONTH_FORMAT);
            BigDecimal total = totalsByMonth.getOrDefault(key, BigDecimal.ZERO);
            result.add(new MonthlyTotalResponse(key, total));
        }
        return result;
    }
}

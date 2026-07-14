package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.CsvTransactionParser.ParseOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTransactionParserTest {

    @Test
    void conteudoNuloOuEmBrancoRetornaListasVazias() {
        assertThat(CsvTransactionParser.parse(null).validRows()).isEmpty();
        assertThat(CsvTransactionParser.parse(null).invalidRows()).isEmpty();
        assertThat(CsvTransactionParser.parse("   \n  ").validRows()).isEmpty();
    }

    @Test
    void aceitaDataIsoOuBrEVirgulaComoSeparador() {
        String csv = """
                data,descricao,valor
                2026-07-01,Supermercado,-150.00
                02/07/2026,Salario,3000.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(2);
        assertThat(outcome.validRows().get(0).data()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(outcome.validRows().get(1).data()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void detectaPontoEVirgulaComoSeparadorETrataVirgulaNoValorComoDecimalPtBr() {
        String csv = """
                data;descricao;valor
                01/07/2026;Supermercado;-150,50
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.validRows().get(0).valor()).isEqualByComparingTo("-150.50");
    }

    @Test
    void linhaSemHeaderEParseadaNormalmenteComoDado() {
        String csv = "01/07/2026,Supermercado,-150.00";

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.validRows().get(0).descricao()).isEqualTo("Supermercado");
    }

    @Test
    void numeroDeColunasDiferenteDeTresEInvalido() {
        String csv = """
                data,descricao,valor
                01/07/2026,Supermercado
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).line()).isEqualTo(2);
        assertThat(outcome.invalidRows().get(0).reason()).contains("3 colunas");
    }

    @Test
    void descricaoVaziaEInvalida() {
        String csv = """
                data,descricao,valor
                01/07/2026,,-150.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).reason()).contains("Descricao vazia");
    }

    @Test
    void dataEmFormatoInvalidoEInvalida() {
        String csv = """
                data,descricao,valor
                31-13-2026,Supermercado,-150.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).reason()).contains("Data em formato invalido");
    }

    @Test
    void dataComMesForaDoIntervaloValidoEInvalida() {
        String csv = """
                data,descricao,valor
                01/13/2026,Supermercado,-150.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
    }

    @Test
    void diaForaDoIntervaloDoMesEAjustadoParaOUltimoDiaValido() {
        // dd/MM/yyyy usa resolucao SMART do java.time: dia 31 em fevereiro
        // e ajustado para o ultimo dia do mes em vez de lancar erro.
        String csv = """
                data,descricao,valor
                31/02/2026,Supermercado,-150.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.validRows().get(0).data()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void valorNaoNumericoEInvalido() {
        String csv = """
                data,descricao,valor
                01/07/2026,Supermercado,abc
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).reason()).contains("Valor invalido");
    }

    @Test
    void linhasEmBrancoSaoIgnoradasSemGerarErro() {
        String csv = """
                data,descricao,valor

                01/07/2026,Supermercado,-150.00

                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.invalidRows()).isEmpty();
    }

    @Test
    void headerComColunasReordenadasEExtrasEMapeadoPorNome() {
        // Formato real de export do Nubank: 4 colunas, Valor antes de
        // Descricao, mais uma coluna Identificador que nao existe no
        // layout legado -- deve ser mapeado por nome e a coluna extra
        // ignorada, em vez de cair no erro posicional de "3 colunas".
        String csv = """
                Data,Valor,Identificador,Descrição
                02/06/2026,-74.79,6a1f6919-eb27-478c-bd3e-205202cc7add,Transferência enviada pelo Pix
                04/06/2026,-140.00,6a21a529-8baf-4ea0-93ba-9b2484ee5356,Pagamento de fatura
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(2);
        assertThat(outcome.validRows().get(0).data()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(outcome.validRows().get(0).descricao()).isEqualTo("Transferência enviada pelo Pix");
        assertThat(outcome.validRows().get(0).valor()).isEqualByComparingTo("-74.79");
    }

    @Test
    void headerComColunaValorAusenteCaiNoLayoutLegadoPosicional() {
        String csv = """
                data,descricao,montante
                01/07/2026,Supermercado,-150.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.validRows().get(0).valor()).isEqualByComparingTo("-150.00");
    }

    @Test
    void linhasValidasEInvalidasMisturadasPreservamOsNumerosDeLinhaOriginais() {
        String csv = """
                data,descricao,valor
                01/07/2026,Supermercado,-150.00
                data-invalida,Aluguel,-1000.00
                02/07/2026,Salario,3000.00
                """;

        ParseOutcome outcome = CsvTransactionParser.parse(csv);

        assertThat(outcome.validRows()).hasSize(2);
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).line()).isEqualTo(3);
    }
}

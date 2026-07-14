package com.portfolio.financas.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OfxTransactionParserTest {

    private static final String OFX_HEADER = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            SECURITY:NONE
            ENCODING:USASCII
            CHARSET:1252
            COMPRESSION:NONE
            OLDFILEUID:NONE
            NEWFILEUID:NONE

            """;

    @Test
    void conteudoNuloOuEmBrancoRetornaListasVazias() {
        assertThat(OfxTransactionParser.parse(null).validRows()).isEmpty();
        assertThat(OfxTransactionParser.parse("   \n  ").validRows()).isEmpty();
    }

    @Test
    void parseiaTransacoesValidasComNameComoDescricao() {
        String ofx = OFX_HEADER + """
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <BANKTRANLIST>
                <STMTTRN>
                <TRNTYPE>DEBIT
                <DTPOSTED>20260701
                <TRNAMT>-150.00
                <FITID>1000000001
                <NAME>Supermercado
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20260702120000[-3:GMT]
                <TRNAMT>3000.00
                <FITID>1000000002
                <NAME>Salario
                </STMTTRN>
                </BANKTRANLIST>
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;

        ParseOutcome outcome = OfxTransactionParser.parse(ofx);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(2);
        assertThat(outcome.validRows().get(0).data()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(outcome.validRows().get(0).descricao()).isEqualTo("Supermercado");
        assertThat(outcome.validRows().get(0).valor()).isEqualByComparingTo("-150.00");
        // DTPOSTED com hora+timezone: so a parte de data (yyyyMMdd) e usada.
        assertThat(outcome.validRows().get(1).data()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(outcome.validRows().get(1).valor()).isEqualByComparingTo("3000.00");
    }

    @Test
    void usaMemoComoFallbackQuandoNameAusente() {
        String ofx = """
                <OFX>
                <STMTTRN>
                <DTPOSTED>20260701
                <TRNAMT>-42.50
                <MEMO>Pagamento de boleto
                </STMTTRN>
                </OFX>
                """;

        ParseOutcome outcome = OfxTransactionParser.parse(ofx);

        assertThat(outcome.invalidRows()).isEmpty();
        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.validRows().get(0).descricao()).isEqualTo("Pagamento de boleto");
    }

    @Test
    void transacaoSemDtpostedEInvalidaMasNaoAbortaAsDemais() {
        String ofx = """
                <OFX>
                <STMTTRN>
                <TRNAMT>-10.00
                <NAME>Sem data
                </STMTTRN>
                <STMTTRN>
                <DTPOSTED>20260701
                <TRNAMT>-20.00
                <NAME>Com data
                </STMTTRN>
                </OFX>
                """;

        ParseOutcome outcome = OfxTransactionParser.parse(ofx);

        assertThat(outcome.validRows()).hasSize(1);
        assertThat(outcome.validRows().get(0).descricao()).isEqualTo("Com data");
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).reason()).contains("DTPOSTED ausente");
    }

    @Test
    void transacaoSemNameNemMemoEInvalida() {
        String ofx = """
                <OFX>
                <STMTTRN>
                <DTPOSTED>20260701
                <TRNAMT>-10.00
                </STMTTRN>
                </OFX>
                """;

        ParseOutcome outcome = OfxTransactionParser.parse(ofx);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).reason()).contains("Descricao vazia");
    }

    @Test
    void trnamtInvalidoEInvalido() {
        String ofx = """
                <OFX>
                <STMTTRN>
                <DTPOSTED>20260701
                <TRNAMT>abc
                <NAME>Teste
                </STMTTRN>
                </OFX>
                """;

        ParseOutcome outcome = OfxTransactionParser.parse(ofx);

        assertThat(outcome.validRows()).isEmpty();
        assertThat(outcome.invalidRows()).hasSize(1);
        assertThat(outcome.invalidRows().get(0).reason()).contains("TRNAMT invalido");
    }
}

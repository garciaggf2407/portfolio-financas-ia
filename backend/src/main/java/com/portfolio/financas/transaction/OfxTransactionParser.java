package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportRowError;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser tolerante de extrato bancario em OFX 1.x/SGML -- o formato usado
 * pela maioria dos bancos brasileiros (nao o OFX 2.x/XML bem-formado).
 * Diferenca chave do XML: tags de valor (folha) nao tem fechamento, ex.
 * `&lt;TRNAMT&gt;-100.00` sem `&lt;/TRNAMT&gt;`, enquanto tags de container como
 * `&lt;STMTTRN&gt;...&lt;/STMTTRN&gt;` fecham normalmente -- por isso um parser XML
 * padrao rejeita o arquivo. Em vez de trazer uma lib de SGML/OFX, extrai
 * cada bloco STMTTRN e le os campos por regex, no mesmo espirito tolerante
 * do CsvTransactionParser.
 *
 * Campos lidos: DTPOSTED (data, aceita yyyyMMdd com sufixo opcional de
 * hora/timezone, ex. yyyyMMddHHmmss[-3:GMT]), TRNAMT (valor, sinal ja
 * embutido no proprio OFX -- diferente do CSV, nao ha "coluna de tipo"
 * separada), NAME (descricao, com fallback para MEMO quando NAME ausente).
 *
 * Validado contra fixture de formato real de banco brasileiro (BANKID BR,
 * BRL, mistura de tags SGML sem fechamento e blocos com fechamento XML
 * completo, datas com sufixo de timezone) -- ver
 * OfxTransactionParserTest#parseiaFixtureRealDeBancoBrasileiro. Nenhum bug
 * exposto, diferente do CsvTransactionParser (que expos 1 bug real contra
 * extrato do Nubank).
 */
final class OfxTransactionParser {

    private static final Pattern STMTTRN_BLOCK =
            Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OfxTransactionParser() {
    }

    static ParseOutcome parse(String content) {
        List<ParsedTransactionRow> validRows = new ArrayList<>();
        List<ImportRowError> invalidRows = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return new ParseOutcome(validRows, invalidRows);
        }

        Matcher matcher = STMTTRN_BLOCK.matcher(content);
        int transactionNumber = 0;
        while (matcher.find()) {
            transactionNumber++;
            int lineNumber = countLinesBefore(content, matcher.start()) + 1;
            String block = matcher.group(1);
            try {
                validRows.add(parseBlock(block, lineNumber));
            } catch (RowParseException e) {
                invalidRows.add(new ImportRowError(lineNumber, "STMTTRN #" + transactionNumber, e.getMessage()));
            }
        }
        return new ParseOutcome(validRows, invalidRows);
    }

    private static ParsedTransactionRow parseBlock(String block, int lineNumber) {
        String dtPosted = extractTag(block, "DTPOSTED")
                .orElseThrow(() -> new RowParseException("Tag DTPOSTED ausente"));
        String trnAmt = extractTag(block, "TRNAMT")
                .orElseThrow(() -> new RowParseException("Tag TRNAMT ausente"));
        String descricao = extractTag(block, "NAME")
                .or(() -> extractTag(block, "MEMO"))
                .map(String::strip)
                .orElse("");
        if (descricao.isEmpty()) {
            throw new RowParseException("Descricao vazia (NAME/MEMO ausentes)");
        }

        return new ParsedTransactionRow(parseDate(dtPosted), descricao, parseValor(trnAmt), lineNumber);
    }

    private static LocalDate parseDate(String rawDate) {
        String digitsOnly = rawDate.strip();
        int cut = 0;
        while (cut < digitsOnly.length() && Character.isDigit(digitsOnly.charAt(cut))) {
            cut++;
        }
        if (cut < 8) {
            throw new RowParseException("DTPOSTED em formato invalido: '" + rawDate + "'");
        }
        String yyyyMMdd = digitsOnly.substring(0, 8);
        try {
            return LocalDate.parse(yyyyMMdd, OFX_DATE);
        } catch (DateTimeParseException e) {
            throw new RowParseException("DTPOSTED em formato invalido: '" + rawDate + "'");
        }
    }

    private static BigDecimal parseValor(String rawValor) {
        try {
            return new BigDecimal(rawValor.strip());
        } catch (NumberFormatException e) {
            throw new RowParseException("TRNAMT invalido: '" + rawValor + "'");
        }
    }

    private static java.util.Optional<String> extractTag(String block, String tagName) {
        Pattern tagPattern = Pattern.compile("<" + tagName + ">([^<\r\n]*)", Pattern.CASE_INSENSITIVE);
        Matcher m = tagPattern.matcher(block);
        return m.find() ? java.util.Optional.of(m.group(1)) : java.util.Optional.empty();
    }

    private static int countLinesBefore(String content, int index) {
        int count = 0;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static final class RowParseException extends RuntimeException {
        RowParseException(String message) {
            super(message);
        }
    }
}

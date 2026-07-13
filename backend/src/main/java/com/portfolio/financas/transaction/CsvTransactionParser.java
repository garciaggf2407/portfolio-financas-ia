package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportRowError;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parser tolerante de extrato bancario em CSV (colunas: data, descricao,
 * valor). Aceita separador ',' ou ';' (detectado a partir da primeira
 * linha do arquivo) e datas em dd/MM/yyyy ou yyyy-MM-dd. Quando o
 * separador e ';', a virgula em `valor` e tratada como separador decimal
 * (convencao pt-BR comum em extratos bancarios).
 *
 * Limitacao conhecida: nao suporta campos entre aspas contendo o proprio
 * separador (CSV RFC 4180 completo) nem separador de milhar em `valor`.
 * Extratos bancarios simples (o caso de uso deste projeto) tipicamente
 * nao usam nenhum dos dois.
 */
final class CsvTransactionParser {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CsvTransactionParser() {
    }

    record ParseOutcome(List<ParsedTransactionRow> validRows, List<ImportRowError> invalidRows) {
    }

    static ParseOutcome parse(String content) {
        List<ParsedTransactionRow> validRows = new ArrayList<>();
        List<ImportRowError> invalidRows = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return new ParseOutcome(validRows, invalidRows);
        }

        char separator = detectSeparator(content);
        String[] rawLines = content.split("\r\n|\r|\n", -1);

        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (lineNumber == 1 && isHeader(line, separator)) {
                continue;
            }
            try {
                validRows.add(parseLine(line, separator, lineNumber));
            } catch (RowParseException e) {
                invalidRows.add(new ImportRowError(lineNumber, rawLine, e.getMessage()));
            }
        }
        return new ParseOutcome(validRows, invalidRows);
    }

    private static char detectSeparator(String content) {
        String firstLine = content.lines().findFirst().orElse("");
        return firstLine.contains(";") ? ';' : ',';
    }

    private static boolean isHeader(String line, char separator) {
        String[] fields = splitFields(line, separator);
        return fields.length > 0 && fields[0].strip().equalsIgnoreCase("data");
    }

    private static ParsedTransactionRow parseLine(String line, char separator, int lineNumber) {
        String[] fields = splitFields(line, separator);
        if (fields.length != 3) {
            throw new RowParseException(
                    "Esperado 3 colunas (data, descricao, valor), encontrado " + fields.length);
        }

        LocalDate data = parseDate(fields[0].strip());
        String descricao = fields[1].strip();
        if (descricao.isEmpty()) {
            throw new RowParseException("Descricao vazia");
        }
        BigDecimal valor = parseValor(fields[2].strip(), separator);

        return new ParsedTransactionRow(data, descricao, valor, lineNumber);
    }

    private static LocalDate parseDate(String rawDate) {
        if (rawDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                return LocalDate.parse(rawDate, ISO_DATE);
            } catch (DateTimeParseException e) {
                throw new RowParseException("Data em formato invalido: '" + rawDate + "'");
            }
        }
        if (rawDate.matches("\\d{2}/\\d{2}/\\d{4}")) {
            try {
                return LocalDate.parse(rawDate, BR_DATE);
            } catch (DateTimeParseException e) {
                throw new RowParseException("Data em formato invalido: '" + rawDate + "'");
            }
        }
        throw new RowParseException(
                "Data em formato invalido (esperado dd/MM/yyyy ou yyyy-MM-dd): '" + rawDate + "'");
    }

    private static BigDecimal parseValor(String rawValor, char separator) {
        String normalized = separator == ';' ? rawValor.replace(',', '.') : rawValor;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new RowParseException("Valor invalido: '" + rawValor + "'");
        }
    }

    private static String[] splitFields(String line, char separator) {
        return line.split(Pattern.quote(String.valueOf(separator)), -1);
    }

    private static final class RowParseException extends RuntimeException {
        RowParseException(String message) {
            super(message);
        }
    }
}

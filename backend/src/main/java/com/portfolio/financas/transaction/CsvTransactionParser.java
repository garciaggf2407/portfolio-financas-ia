package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportRowError;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parser tolerante de extrato bancario em CSV. Aceita separador ',' ou ';'
 * (detectado a partir da primeira linha do arquivo) e datas em dd/MM/yyyy
 * ou yyyy-MM-dd. Quando o separador e ';', a virgula em `valor` e tratada
 * como separador decimal (convencao pt-BR comum em extratos bancarios).
 *
 * Colunas: quando a primeira linha e um header reconhecivel (contem
 * "data", "valor" e "descricao"/"descrição" por nome, em qualquer ordem
 * e com colunas extras permitidas -- ex.: exports reais como o do Nubank
 * trazem "Data,Valor,Identificador,Descrição"), o mapeamento e por nome e
 * colunas desconhecidas sao ignoradas. Sem header ou com header nao
 * reconhecido, cai no layout legado posicional (data, descricao, valor,
 * exatamente 3 colunas) por compatibilidade retroativa.
 *
 * Limitacao conhecida: nao suporta campos entre aspas contendo o proprio
 * separador (CSV RFC 4180 completo) nem separador de milhar em `valor`.
 * Extratos bancarios simples (o caso de uso deste projeto) tipicamente
 * nao usam nenhum dos dois.
 */
final class CsvTransactionParser {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final ColumnLayout LEGACY_LAYOUT = new ColumnLayout(0, 1, 2, 3, true);

    private CsvTransactionParser() {
    }

    record ParseOutcome(List<ParsedTransactionRow> validRows, List<ImportRowError> invalidRows) {
    }

    private record ColumnLayout(int dataIdx, int descricaoIdx, int valorIdx, int minColumns, boolean exactMatch) {
    }

    static ParseOutcome parse(String content) {
        List<ParsedTransactionRow> validRows = new ArrayList<>();
        List<ImportRowError> invalidRows = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return new ParseOutcome(validRows, invalidRows);
        }

        char separator = detectSeparator(content);
        String[] rawLines = content.split("\r\n|\r|\n", -1);

        ColumnLayout layout = LEGACY_LAYOUT;
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (lineNumber == 1 && isHeader(line, separator)) {
                layout = mapHeader(splitFields(line, separator)).orElse(LEGACY_LAYOUT);
                continue;
            }
            try {
                validRows.add(parseLine(line, separator, lineNumber, layout));
            } catch (RowParseException e) {
                invalidRows.add(new ImportRowError(lineNumber, rawLine, e.getMessage()));
            }
        }
        return new ParseOutcome(validRows, invalidRows);
    }

    private static Optional<ColumnLayout> mapHeader(String[] headerFields) {
        int dataIdx = -1;
        int descricaoIdx = -1;
        int valorIdx = -1;
        for (int i = 0; i < headerFields.length; i++) {
            String name = normalizeHeaderName(headerFields[i]);
            if (name.equals("data")) {
                dataIdx = i;
            } else if (name.startsWith("descri")) {
                descricaoIdx = i;
            } else if (name.equals("valor")) {
                valorIdx = i;
            }
        }
        if (dataIdx < 0 || descricaoIdx < 0 || valorIdx < 0) {
            return Optional.empty();
        }
        int minColumns = Math.max(dataIdx, Math.max(descricaoIdx, valorIdx)) + 1;
        return Optional.of(new ColumnLayout(dataIdx, descricaoIdx, valorIdx, minColumns, false));
    }

    private static String normalizeHeaderName(String rawName) {
        String withoutAccents = Normalizer.normalize(rawName.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }

    private static char detectSeparator(String content) {
        String firstLine = content.lines().findFirst().orElse("");
        return firstLine.contains(";") ? ';' : ',';
    }

    private static boolean isHeader(String line, char separator) {
        String[] fields = splitFields(line, separator);
        return fields.length > 0 && fields[0].strip().equalsIgnoreCase("data");
    }

    private static ParsedTransactionRow parseLine(String line, char separator, int lineNumber, ColumnLayout layout) {
        String[] fields = splitFields(line, separator);
        boolean invalidCount = layout.exactMatch()
                ? fields.length != layout.minColumns()
                : fields.length < layout.minColumns();
        if (invalidCount) {
            String prefix = layout.exactMatch() ? "Esperado " : "Esperado ao menos ";
            throw new RowParseException(
                    prefix + layout.minColumns() + " colunas (data, descricao, valor), encontrado " + fields.length);
        }

        LocalDate data = parseDate(fields[layout.dataIdx()].strip());
        String descricao = fields[layout.descricaoIdx()].strip();
        if (descricao.isEmpty()) {
            throw new RowParseException("Descricao vazia");
        }
        BigDecimal valor = parseValor(fields[layout.valorIdx()].strip(), separator);

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

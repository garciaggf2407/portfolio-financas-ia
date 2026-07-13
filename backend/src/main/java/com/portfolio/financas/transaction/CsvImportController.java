package com.portfolio.financas.transaction;

import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.transaction.dto.ImportResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Upload de extrato bancario em CSV (colunas: data, descricao, valor).
 * O parsing tolerante a formato (separador ',' ou ';', datas dd/MM/yyyy
 * ou yyyy-MM-dd) e feito por CsvTransactionParser; linhas invalidas sao
 * reportadas individualmente e nao abortam o import inteiro.
 *
 * NOTA para revisao: esta versao persiste todas as linhas validas sem
 * verificar duplicatas -- reimportar o mesmo arquivo falharia com uma
 * violacao da constraint UNIQUE em hash_deduplicacao. A deduplicacao
 * (mesma data+descricao+valor ja existente e ignorada e contada
 * separadamente) e responsabilidade de TransactionImportService, que
 * assume a persistencia por completo em T-2.1.2.
 */
@RestController
public class CsvImportController {

    private final TransactionRepository transactionRepository;

    public CsvImportController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @PostMapping(value = "/transactions/import", consumes = "multipart/form-data")
    public ResponseEntity<ImportResult> importTransactions(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Arquivo CSV ausente ou vazio.");
        }

        String content = readAsUtf8(file);
        CsvTransactionParser.ParseOutcome outcome = CsvTransactionParser.parse(content);

        List<Transaction> toPersist = new ArrayList<>();
        for (ParsedTransactionRow row : outcome.validRows()) {
            String hash = DeduplicationHash.compute(row.data(), row.descricao(), row.valor());
            toPersist.add(new Transaction(
                    row.data(), row.descricao(), row.valor(), file.getOriginalFilename(), hash));
        }
        transactionRepository.saveAll(toPersist);

        ImportResult result = new ImportResult(toPersist.size(), 0, outcome.invalidRows());
        return ResponseEntity.ok(result);
    }

    private String readAsUtf8(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }
}

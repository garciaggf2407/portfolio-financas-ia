package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persiste o resultado do parsing de CSV (CsvTransactionParser) com
 * deduplicacao: uma linha valida cuja combinacao data+descricao+valor ja
 * existe -- no banco ou em outra linha do mesmo arquivo -- e ignorada e
 * contada separadamente, em vez de gerar uma nova transacao ou violar a
 * constraint UNIQUE de hash_deduplicacao (V1__init.sql).
 */
@Service
public class TransactionImportService {

    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionImportService(TransactionRepository transactionRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
    }

    public ImportResult importCsv(String csvContent, String originFilename) {
        ParseOutcome outcome = CsvTransactionParser.parse(csvContent);
        return persist(outcome, originFilename);
    }

    public ImportResult importOfx(String ofxContent, String originFilename) {
        ParseOutcome outcome = OfxTransactionParser.parse(ofxContent);
        return persist(outcome, originFilename);
    }

    /**
     * Ponto de entrada usado pelo controller: detecta o formato do extrato
     * (CSV ou OFX) a partir do nome do arquivo e, como fallback, do
     * conteudo (extratos OFX comecam com "OFXHEADER:" ou contem a tag
     * &lt;OFX&gt;), e despacha para o parser correspondente.
     */
    public ImportResult importStatement(String content, String originFilename) {
        return isOfx(content, originFilename) ? importOfx(content, originFilename) : importCsv(content, originFilename);
    }

    private boolean isOfx(String content, String originFilename) {
        if (originFilename != null && originFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".ofx")) {
            return true;
        }
        String head = content == null ? "" : content.stripLeading();
        return head.regionMatches(true, 0, "OFXHEADER:", 0, "OFXHEADER:".length()) || head.contains("<OFX>");
    }

    private ImportResult persist(ParseOutcome outcome, String originFilename) {
        List<ParsedTransactionRow> rows = outcome.validRows();

        Set<String> hashesInBatch = new HashSet<>();
        for (ParsedTransactionRow row : rows) {
            hashesInBatch.add(DeduplicationHash.compute(row.data(), row.descricao(), row.valor()));
        }
        Set<String> existingHashes = transactionRepository.findExistingHashes(hashesInBatch);

        Set<String> seenInThisImport = new HashSet<>();
        List<Transaction> toPersist = new ArrayList<>();
        int ignoradasDuplicadas = 0;

        for (ParsedTransactionRow row : rows) {
            String hash = DeduplicationHash.compute(row.data(), row.descricao(), row.valor());
            // Duplicada se ja existe no banco OU se ja apareceu antes neste
            // mesmo arquivo (ex: extrato com a mesma linha repetida).
            boolean isDuplicate = existingHashes.contains(hash) || !seenInThisImport.add(hash);
            if (isDuplicate) {
                ignoradasDuplicadas++;
                continue;
            }
            toPersist.add(new Transaction(row.data(), row.descricao(), row.valor(), originFilename, hash));
        }

        transactionRepository.saveAll(toPersist);

        if (!toPersist.isEmpty()) {
            List<UUID> transactionIds = toPersist.stream().map(Transaction::getId).toList();
            eventPublisher.publishEvent(new TransactionsImportedEvent(transactionIds));
        }

        return new ImportResult(toPersist.size(), ignoradasDuplicadas, outcome.invalidRows());
    }
}

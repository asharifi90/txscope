package com.txscope.demo.scenario;

import com.txscope.demo.domain.Item;
import com.txscope.demo.domain.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Scenario S1: External HTTP call inside a @Transactional method.
 * <p>
 * The DB connection is borrowed for the whole transaction duration,
 * including the time spent waiting on the external HTTP call.
 * Under concurrent load this saturates the pool while PostgreSQL itself
 * is mostly idle.
 */
@Service
public class S1ExternalCallInTransactionService {

    private static final Logger log = LoggerFactory.getLogger(S1ExternalCallInTransactionService.class);

    private final ItemRepository itemRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public S1ExternalCallInTransactionService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Opens a transaction, does a tiny DB write, then blocks on an external HTTP call
     * (or a simulated sleep) while still holding the connection.
     *
     * @param delayMs how long the "external" call should take
     */
    @Transactional
    public Item execute(long delayMs) {
        log.info("S1: starting transactional method, delay={}ms", delayMs);

        // Real (but tiny) DB work so a connection is actually borrowed
        Item item = itemRepository.save(new Item("s1-" + System.currentTimeMillis()));

        // Simulate external HTTP / RPC that takes time
        // In a real incident this would be a RestClient/WebClient call to another service.
        simulateExternalCall(delayMs);

        log.info("S1: transactional method finished for item id={}", item.getId());
        return item;
    }

    private void simulateExternalCall(long delayMs) {
        try {
            // Prefer a real HTTP call if a slow endpoint is available;
            // otherwise fall back to Thread.sleep so the demo works offline.
            // For now we keep it simple and reliable with sleep.
            log.debug("S1: simulating external call for {} ms (holding DB connection)", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating external call", e);
        }
    }
}

package com.txscope.demo.scenario;

import com.txscope.demo.domain.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intentional failure endpoints for TxScope demos.
 * <p>
 * S1 is implemented first. S2–S4 will be added later.
 */
@RestController
@RequestMapping("/scenarios")
public class ScenarioController {

    private final S1ExternalCallInTransactionService s1Service;

    public ScenarioController(S1ExternalCallInTransactionService s1Service) {
        this.s1Service = s1Service;
    }

    /**
     * S1 – External work inside a transaction.
     * <p>
     * Call this endpoint concurrently (e.g. with the load script) while the pool is small.
     * Each request holds a Hikari connection for the whole duration of the simulated external call.
     *
     * @param delayMs duration of the simulated external call (default 3000 ms)
     */
    @GetMapping("/s1")
    public ResponseEntity<Item> s1(@RequestParam(defaultValue = "3000") long delayMs) {
        Item item = s1Service.execute(delayMs);
        return ResponseEntity.ok(item);
    }

    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("demo-app is up");
    }
}

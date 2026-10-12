package org.example.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints to start an event backtest and to follow it until its result is ready.
 *
 * <p>A backtest takes from seconds to minutes, so {@code POST} only starts it and answers at once
 * with a job id. The user interface then asks {@code GET /api/backtests/{id}} every few seconds.
 */
@RestController
public class BacktestJobController {

    private final BacktestJobService service;

    /**
     * Creates the controller. Spring calls this and passes in the service (dependency injection).
     *
     * @param service starts and remembers the backtests
     */
    public BacktestJobController(BacktestJobService service) {
        this.service = service;
    }

    /**
     * Starts a backtest in the background.
     *
     * @param request the settings of the backtest
     * @return 202 Accepted with the new job; its result comes later
     */
    @PostMapping("/api/backtests")
    public ResponseEntity<BacktestJob> start(@RequestBody BacktestRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submit(request));
    }

    /**
     * Tells how a backtest is doing, with its result once it is done.
     *
     * @param id the job id returned when the backtest was started
     * @return the job, or 404 Not Found if there is no such job
     */
    @GetMapping("/api/backtests/{id}")
    public ResponseEntity<BacktestJob> job(@PathVariable String id) {
        return ResponseEntity.of(service.find(id));
    }

    /**
     * Turns a wrong setting into 400 Bad Request with a readable message.
     *
     * @param e the error raised while checking the request
     * @return the message as JSON
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

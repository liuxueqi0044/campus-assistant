package cn.iocoder.yudao.module.campus.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A deterministic test double for a gateway. Outcomes are consumed in FIFO order and every invocation is recorded.
 * It deliberately has no network implementation and belongs only on test classpaths.
 */
public final class ControlledGateway<Request, Response> {

    private final Clock clock;
    private final ConcurrentLinkedQueue<Outcome<Response>> outcomes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<GatewayCall<Request>> calls = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Outcome<Response>> fallback = new AtomicReference<>();

    public ControlledGateway(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void succeedNext(Response response) {
        outcomes.add(Outcome.success(response));
    }

    public void failNext(RuntimeException failure) {
        outcomes.add(Outcome.failure(failure));
    }

    public void alwaysSucceed(Response response) {
        fallback.set(Outcome.success(response));
    }

    public void alwaysFail(RuntimeException failure) {
        fallback.set(Outcome.failure(failure));
    }

    public Response invoke(Request request) {
        calls.add(new GatewayCall<>(request, clock.instant()));
        Outcome<Response> outcome = outcomes.poll();
        if (outcome == null) {
            outcome = fallback.get();
        }
        if (outcome == null) {
            throw new IllegalStateException("No controlled gateway outcome configured");
        }
        return outcome.resolve();
    }

    public int callCount() {
        return calls.size();
    }

    public List<GatewayCall<Request>> calls() {
        return List.copyOf(new ArrayList<>(calls));
    }

    public record GatewayCall<Request>(Request request, Instant invokedAt) {
    }

    private record Outcome<Response>(Response response, RuntimeException failure) {
        private static <Response> Outcome<Response> success(Response response) {
            return new Outcome<>(response, null);
        }

        private static <Response> Outcome<Response> failure(RuntimeException failure) {
            return new Outcome<>(null, Objects.requireNonNull(failure, "failure"));
        }

        private Response resolve() {
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}

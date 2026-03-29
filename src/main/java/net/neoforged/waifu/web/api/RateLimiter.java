package net.neoforged.waifu.web.api;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final Map<String, RateLimitRemainder> limits = new ConcurrentHashMap<>();

    public boolean rateLimitExceeded(Context context, String identification, TokenManager.RateLimit rateLimit) {
        long now = System.currentTimeMillis() / 1000;
        var limit = this.limits.get(identification);
        if (limit == null || limit.resetsAtSeconds <= now) {
            limit = new RateLimitRemainder(now + rateLimit.per().toSeconds(), new AtomicInteger(rateLimit.requests()));
            limits.put(identification, limit);
        }

        context.header("x-ratelimit-reset", String.valueOf(limit.resetsAtSeconds() - now));


        var current = limit.remaining().get();
        if (current <= 0) {
            context.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("x-ratelimit-remaining", "0")
                    .json(Map.of("error", "Rate limit (" + rateLimit.requests() + ") exceeded, try again in " + (limit.resetsAtSeconds() - now) + " seconds"));
            return true;
        }

        var remaining = limit.remaining().decrementAndGet();
        context.header("x-ratelimit-remaining", String.valueOf(remaining));
        return false;
    }

    private record RateLimitRemainder(long resetsAtSeconds, AtomicInteger remaining) {
    }
}

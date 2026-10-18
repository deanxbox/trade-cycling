package de.maxhenkel.tradecycling;

import de.maxhenkel.tradecycling.compatibility.VisibleTraders;
import de.maxhenkel.tradecycling.mixin.AbstractVillagerAccessor;
import de.maxhenkel.tradecycling.mixin.VillagerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public class ParallelTradeSimulator {

    private static final int WORKER_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    // shortcut: unknown price floors (items) stop after this many extra tries past the first match; raise if users want deeper searches
    private static final int CHEAPEST_EXTRA_ATTEMPTS = 20_000;
    private static final long PROGRESS_INTERVAL_NANOS = 250_000_000L;
    private static final int COOPERATIVE_PAUSE_INTERVAL = 256;
    private static final long COOPERATIVE_PAUSE_NANOS = 100_000L;
    private static final ForkJoinPool WORKER_POOL = new ForkJoinPool(WORKER_COUNT, pool -> {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("Trade Cycling Simulator " + thread.getPoolIndex());
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
        return thread;
    }, null, true);

    public static boolean isUseful() {
        return WORKER_COUNT >= 1;
    }

    public static int workerCount() {
        return WORKER_COUNT;
    }

    public static Result run(ServerLevel level, Villager sourceVillager, ServerPlayer player, GossipContainer gossips, AutoTradeHandler.MatchPlan matchPlan, int maxCycles, boolean cooperativePause, BooleanSupplier cancelled, IntConsumer progressConsumer) {
        AtomicInteger attempts = new AtomicInteger();
        AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());
        AtomicReference<Winner> winner = new AtomicReference<>();
        Search search = new Search(winner);

        List<CompletableFuture<Void>> workers = new ArrayList<>(WORKER_COUNT);
        for (int i = 0; i < WORKER_COUNT; i++) {
            workers.add(CompletableFuture.runAsync(() -> runWorker(level, sourceVillager, player, gossips, matchPlan.copy(), maxCycles, cooperativePause, attempts, lastProgressNanos, search, cancelled, progressConsumer), WORKER_POOL));
        }
        for (CompletableFuture<Void> worker : workers) {
            worker.join();
        }

        Winner matched = winner.get();
        if (matched != null) {
            return Result.matched(Math.min(attempts.get(), maxCycles), WORKER_COUNT, matched.offers, matched.futureOfferSets, matched.matchSummary);
        }
        return Result.noMatch(Math.min(attempts.get(), maxCycles), WORKER_COUNT);
    }

    private static void runWorker(ServerLevel level, Villager sourceVillager, ServerPlayer player, GossipContainer gossips, AutoTradeHandler.MatchPlan matchPlan, int maxCycles, boolean cooperativePause, AtomicInteger attempts, AtomicLong lastProgressNanos, Search search, BooleanSupplier cancelled, IntConsumer progressConsumer) {
        Villager clone = createClone(level, sourceVillager, gossips);
        boolean needsSpecialPrices = matchPlan.hasPriceConstraints();

        while (!cancelled.getAsBoolean() && !search.done) {
            int attempt = attempts.incrementAndGet();
            if (attempt > maxCycles || search.exhausted(attempt)) {
                return;
            }
            maybeReportProgress(attempt, lastProgressNanos, progressConsumer);
            maybeCooperativePause(attempt, cooperativePause);

            clone.setOffers(null);
            MerchantOffers activeOffers = clone.getOffers();
            if (needsSpecialPrices && clone instanceof VillagerAccessor villagerAccessor) {
                villagerAccessor.invokeUpdateSpecialPrices(player);
                activeOffers = clone.getOffers();
            }

            LookupOffers lookupOffers = createLookupOffers(level, clone, activeOffers);
            AutoTradeHandler.MatchSummary matchSummary = matchPlan.findVillagerMatch(lookupOffers.activeAndFutureOffers);
            if (matchSummary.matched) {
                search.offer(attempt, new Winner(activeOffers.copy(), copyOfferSets(lookupOffers.futureOffers), matchSummary));
                if (search.done) {
                    return;
                }
            }
        }
    }

    private static LookupOffers createLookupOffers(ServerLevel level, Villager villager, MerchantOffers activeOffers) {
        if (!VisibleTraders.isCompatibilityActive()) {
            return new LookupOffers(activeOffers, Collections.emptyList());
        }

        MerchantOffers lookupOffers = new MerchantOffers();
        lookupOffers.addAll(activeOffers);
        List<MerchantOffers> futureOfferSets = new ArrayList<>();
        int currentLevel = villager.getVillagerData().level();
        for (int levelIndex = currentLevel + 1; levelIndex <= 5; levelIndex++) {
            VillagerData data = villager.getVillagerData().withLevel(levelIndex);
            VillagerProfession profession = data.profession().value();
            ResourceKey<TradeSet> trades = profession.getTrades(data.level());
            if (trades == null) {
                continue;
            }

            MerchantOffers futureOffers = new MerchantOffers();
            if (villager instanceof AbstractVillagerAccessor abstractVillagerAccessor) {
                abstractVillagerAccessor.invokeAddOffersFromTradeSet(level, futureOffers, trades);
                futureOfferSets.add(futureOffers);
                lookupOffers.addAll(futureOffers);
            }
        }
        return new LookupOffers(lookupOffers, futureOfferSets);
    }

    private static List<MerchantOffers> copyOfferSets(List<MerchantOffers> offerSets) {
        if (offerSets.isEmpty()) {
            return Collections.emptyList();
        }
        List<MerchantOffers> copies = new ArrayList<>(offerSets.size());
        for (MerchantOffers offerSet : offerSets) {
            copies.add(offerSet.copy());
        }
        return List.copyOf(copies);
    }

    private static void maybeCooperativePause(int attempts, boolean cooperativePause) {
        if (!cooperativePause || (attempts % COOPERATIVE_PAUSE_INTERVAL) != 0) {
            return;
        }
        LockSupport.parkNanos(COOPERATIVE_PAUSE_NANOS);
    }

    private static void maybeReportProgress(int attempts, AtomicLong lastProgressNanos, IntConsumer progressConsumer) {
        if ((attempts & 1023) != 0) {
            return;
        }
        long now = System.nanoTime();
        long last = lastProgressNanos.get();
        if (now - last >= PROGRESS_INTERVAL_NANOS && lastProgressNanos.compareAndSet(last, now)) {
            progressConsumer.accept(attempts);
        }
    }

    private static Villager createClone(ServerLevel level, Villager sourceVillager, GossipContainer gossips) {
        Villager clone = new Villager(EntityTypes.VILLAGER, level);
        if (gossips != null) {
            clone.setGossips(gossips.copy()); // discounts depend on the player's reputation with the real villager
        }
        clone.setVillagerData(sourceVillager.getVillagerData());
        clone.setVillagerXp(sourceVillager.getVillagerXp());
        clone.setPos(sourceVillager.getX(), sourceVillager.getY(), sourceVillager.getZ());
        sourceVillager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(jobSite ->
                clone.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSite));
        return clone;
    }

    /** Tracks the best match; "cheapest" targets keep searching (bounded) until their price floor is reached. */
    private static final class Search {
        private final AtomicReference<Winner> winner;
        private volatile boolean done;
        private volatile int firstMatchAttempt = -1;

        private Search(AtomicReference<Winner> winner) {
            this.winner = winner;
        }

        private boolean exhausted(int attempt) {
            return firstMatchAttempt >= 0 && attempt - firstMatchAttempt > CHEAPEST_EXTRA_ATTEMPTS;
        }

        private synchronized void offer(int attempt, Winner candidate) {
            Winner best = winner.get();
            if (best == null || candidate.matchSummary.totalEmeralds() < best.matchSummary.totalEmeralds()) {
                winner.set(candidate);
            }
            if (firstMatchAttempt < 0) {
                firstMatchAttempt = attempt;
            }
            if (candidate.matchSummary.cheapestSatisfied()) {
                done = true;
            }
        }
    }

    private record LookupOffers(MerchantOffers activeAndFutureOffers, List<MerchantOffers> futureOffers) {
    }

    private record Winner(MerchantOffers offers, List<MerchantOffers> futureOfferSets, AutoTradeHandler.MatchSummary matchSummary) {
    }

    public record Result(boolean matched, int attempts, int workers, @Nullable MerchantOffers offers, List<MerchantOffers> futureOfferSets, @Nullable AutoTradeHandler.MatchSummary matchSummary) {

        public static Result matched(int attempts, int workers, MerchantOffers offers, AutoTradeHandler.MatchSummary matchSummary) {
            return new Result(true, attempts, workers, offers, Collections.emptyList(), matchSummary);
        }

        public static Result matched(int attempts, int workers, MerchantOffers offers, List<MerchantOffers> futureOfferSets, AutoTradeHandler.MatchSummary matchSummary) {
            return new Result(true, attempts, workers, offers, List.copyOf(futureOfferSets), matchSummary);
        }

        public static Result noMatch(int attempts, int workers) {
            return new Result(false, attempts, workers, null, Collections.emptyList(), null);
        }
    }
}

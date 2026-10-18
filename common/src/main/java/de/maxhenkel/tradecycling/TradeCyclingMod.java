package de.maxhenkel.tradecycling;

import de.maxhenkel.tradecycling.compatibility.VisibleTraders;
import de.maxhenkel.tradecycling.mixin.VillagerAccessor;
import de.maxhenkel.tradecycling.mixin.MerchantMenuAccessor;
import de.maxhenkel.tradecycling.net.ServerAutoCycleProgressPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleRequestPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleResultPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class TradeCyclingMod {

    public static final String MODID = "trade_cycling";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    private static final int SERVER_AUTOCYCLE_INITIAL_BATCH_SIZE = 4096;
    private static final int SERVER_AUTOCYCLE_MAX_BATCH_SIZE = 65_536;
    private static final long SERVER_AUTOCYCLE_PROGRESS_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final ExecutorService SERVER_AUTOCYCLE_EXECUTOR = Executors.newCachedThreadPool(new AutoCycleThreadFactory());
    private static final ConcurrentMap<UUID, ServerAutoCycleJob> SERVER_AUTOCYCLE_JOBS = new ConcurrentHashMap<>();

    public void onServerAutoCycleCancel(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ServerAutoCycleJob job = SERVER_AUTOCYCLE_JOBS.get(playerId);
        if (job != null) {
            job.cancel();
        }
    }


    public void init() {
        VisibleTraders.init(this);
    }

    public abstract boolean isModLoaded(String modId);

    public abstract void sendServerAutoCycleResult(ServerPlayer player, ServerAutoCycleResultPacket packet);

    public void sendServerAutoCycleProgress(ServerPlayer player, ServerAutoCycleProgressPacket packet) {
    }

    public int getServerAutoCycleMaxAttempts() {
        return Integer.MAX_VALUE;
    }

    public int getServerAutoCycleBatchTimeMillis() {
        return 10;
    }

    public int getServerAutoCycleBatchDelayMillis() {
        return 0;
    }

    public static void onCycleTrades(@Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!(player.containerMenu instanceof MerchantMenuAccessor merchantAccessor)) {
            return;
        }
        if (!(player.containerMenu instanceof MerchantMenu container)) {
            return;
        }
        Merchant merchant = merchantAccessor.getTrader();

        if (container.getTraderXp() > 0) {
            return;
        }

        if (!(merchant instanceof Villager villager)) {
            return;
        }
        if (!(merchant instanceof VillagerAccessor villagerAccessor)) {
            return;
        }

        if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
            return;
        }

        villager.setOffers(null);
        villager.getOffers();
        villagerAccessor.invokeUpdateSpecialPrices(player);
        villager.setTradingPlayer(player);
        VisibleTraders.regenerateTrades(villager);
        VisibleTraders.requestOffers(villager, player);
        sendOffers(player, container.containerId, villager);
        container.slotsChanged(merchantAccessor.getTradeContainer());
    }

    public void onServerAutoCycle(ServerPlayer player, ServerAutoCycleRequestPacket packet) {
        if (player == null) {
            return;
        }
        if (!(player.containerMenu instanceof MerchantMenuAccessor merchantAccessor)) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: open a villager first"));
            return;
        }
        if (!(player.containerMenu instanceof MerchantMenu container)) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: open a villager first"));
            return;
        }
        Merchant merchant = merchantAccessor.getTrader();
        if (!(merchant instanceof Villager villager)) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: trader is not a villager"));
            return;
        }
        if (!(merchant instanceof VillagerAccessor villagerAccessor)) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: villager access failed"));
            return;
        }
        if (container.getTraderXp() > 0) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: villager already has XP"));
            return;
        }
        if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: villager has no job site"));
            return;
        }

        List<AutoTradeHandler.ItemTarget> itemTargets = resolveItemTargets(packet);
        List<AutoTradeHandler.EnchantTarget> enchantTargets = resolveEnchantTargets(player, villager, packet);
        if (itemTargets.isEmpty() && enchantTargets.isEmpty()) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: no valid targets"));
            return;
        }
        AutoTradeHandler.MatchPlan matchPlan = AutoTradeHandler.createMatchPlan(itemTargets, enchantTargets, packet.matchAny());

        long startedNanos = System.nanoTime();
        MerchantOffers currentOffers = VisibleTraders.getOffers(villager);
        AutoTradeHandler.MatchSummary currentMatch = matchPlan.findVillagerMatch(currentOffers);
        if (currentMatch.matched && currentMatch.cheapestSatisfied()) { // cheapest targets keep searching for a lower price
            sendOffers(player, container.containerId, villager);
            sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(true, 0, currentMatch.completionMessage() + " after 0 tries" + formatStats(0, startedNanos)));
            return;
        }

        int maxCycles = Math.min(sanitizeCycleCount(getServerAutoCycleMaxAttempts()), sanitizeCycleCount(packet.maxCycles()));
        sendServerAutoCycleProgress(player, new ServerAutoCycleProgressPacket(0, maxCycles));
        startServerAutoCycleJob(player, packet, container.containerId, villager, villagerAccessor, matchPlan, maxCycles, startedNanos);
    }

    private void startServerAutoCycleJob(ServerPlayer player, ServerAutoCycleRequestPacket packet, int containerId, Villager villager, VillagerAccessor villagerAccessor, AutoTradeHandler.MatchPlan matchPlan, int maxCycles, long startedNanos) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            sendServerAutoCycleResult(player, new ServerAutoCycleResultPacket(false, 0, "Autotrade unavailable: server is not ready"));
            return;
        }

        UUID playerId = player.getUUID();
        ServerAutoCycleJob job = new ServerAutoCycleJob();
        job.gossips = villager.getGossips().copy(); // snapshot on the server thread so workers price with the real reputation
        ServerAutoCycleJob previousJob = SERVER_AUTOCYCLE_JOBS.put(playerId, job);
        if (previousJob != null) {
            previousJob.cancel();
        }

        if (canUseParallelCloneAutoCycle()) {
            SERVER_AUTOCYCLE_EXECUTOR.execute(() -> runParallelAutoCycleJob(server, player.level(), player, packet, containerId, villager, villagerAccessor, matchPlan, maxCycles, startedNanos, playerId, job));
        } else {
            SERVER_AUTOCYCLE_EXECUTOR.execute(() -> runServerAutoCycleJob(server, player, packet, containerId, villager, villagerAccessor, matchPlan, maxCycles, startedNanos, playerId, job));
        }
    }

    private static boolean canUseParallelCloneAutoCycle() {
        return ParallelTradeSimulator.isUseful();
    }

    private void runParallelAutoCycleJob(MinecraftServer server, ServerLevel level, ServerPlayer player, ServerAutoCycleRequestPacket packet, int containerId, Villager villager, VillagerAccessor villagerAccessor, AutoTradeHandler.MatchPlan matchPlan, int maxCycles, long startedNanos, UUID playerId, ServerAutoCycleJob job) {
        int attempts = 0;
        try {
            ParallelTradeSimulator.Result result = ParallelTradeSimulator.run(level, villager, player, job.gossips, matchPlan, maxCycles, !server.isDedicatedServer(), job::isCancelled, progress -> {
                if (!job.isCancelled()) {
                    server.execute(() -> {
                        if (job.isCancelled()) {
                            return;
                        }
                        Optional<String> validationError = validateServerAutoCycleState(player, containerId, villager);
                        if (validationError.isPresent()) {
                            job.cancel();
                            sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(false, Math.min(progress, maxCycles), validationError.get() + formatStats(Math.min(progress, maxCycles), startedNanos)));
                            return;
                        }
                        sendServerAutoCycleProgress(player, new ServerAutoCycleProgressPacket(Math.min(progress, maxCycles), maxCycles));
                    });
                }
            });
            attempts = result.attempts();

            if (job.isCancelled()) {
                return;
            }

            applyParallelAutoCycleResult(server, player, packet, containerId, villager, villagerAccessor, matchPlan, maxCycles, startedNanos, job, result).join();
        } catch (Exception e) {
            LOGGER.error("Parallel server autotrade failed", e);
            finishServerAutoCycleError(server, player, packet, attempts, startedNanos, "Autotrade stopped: parallel worker error", job);
        } finally {
            SERVER_AUTOCYCLE_JOBS.remove(playerId, job);
        }
    }

    private CompletableFuture<Void> applyParallelAutoCycleResult(MinecraftServer server, ServerPlayer player, ServerAutoCycleRequestPacket packet, int containerId, Villager villager, VillagerAccessor villagerAccessor, AutoTradeHandler.MatchPlan matchPlan, int maxCycles, long startedNanos, ServerAutoCycleJob job, ParallelTradeSimulator.Result result) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                if (job.isCancelled()) {
                    future.complete(null);
                    return;
                }

                Optional<String> validationError = validateServerAutoCycleState(player, containerId, villager);
                if (validationError.isPresent()) {
                    sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(false, result.attempts(), validationError.get() + formatStats(result.attempts(), startedNanos)));
                    future.complete(null);
                    return;
                }

                if (result.matched() && result.offers() != null && result.matchSummary() != null) {
                    villager.setOffers(result.offers().copy());
                    villagerAccessor.invokeUpdateSpecialPrices(player);
                    villager.setTradingPlayer(player);
                    VisibleTraders.setLockedOffers(villager, result.futureOfferSets());
                    VisibleTraders.requestOffers(villager, player);
                    sendOffers(player, containerId, villager);
                    sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(true, result.attempts(), result.matchSummary().completionMessage() + formatTryCount(result.attempts()) + formatStats(result.attempts(), startedNanos) + formatWorkerCount(result.workers())));
                } else {
                    sendServerAutoCycleProgress(player, new ServerAutoCycleProgressPacket(result.attempts(), maxCycles));
                    sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(false, result.attempts(), "Autotrade stopped after " + result.attempts() + " cycles without a match" + formatStats(result.attempts(), startedNanos) + formatWorkerCount(result.workers())));
                }
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void runServerAutoCycleJob(MinecraftServer server, ServerPlayer player, ServerAutoCycleRequestPacket packet, int containerId, Villager villager, VillagerAccessor villagerAccessor, AutoTradeHandler.MatchPlan matchPlan, int maxCycles, long startedNanos, UUID playerId, ServerAutoCycleJob job) {
        int attempts = 0;
        try {
            while (!job.isCancelled() && attempts < maxCycles) {
                int startingAttempts = attempts;
                int batchSize = Math.min(job.getBatchSize(), maxCycles - attempts);
                BatchResult batchResult = runServerAutoCycleBatch(server, player, packet, containerId, villager, villagerAccessor, matchPlan, attempts, batchSize, maxCycles, startedNanos, job).join();
                attempts = batchResult.attempts;
                job.updateBatchSize(attempts - startingAttempts, batchResult.elapsedNanos, getServerAutoCycleBatchTimeMillis());
                if (batchResult.status != BatchStatus.CONTINUE) {
                    return;
                }

                int batchDelayMillis = Math.max(0, getServerAutoCycleBatchDelayMillis());
                if (batchDelayMillis > 0) {
                    TimeUnit.MILLISECONDS.sleep(batchDelayMillis);
                }
            }

            if (!job.isCancelled()) {
                finishServerAutoCycleNoMatch(server, player, packet, containerId, villager, maxCycles, startedNanos, job);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishServerAutoCycleError(server, player, packet, attempts, startedNanos, "Autotrade stopped: worker thread interrupted", job);
        } catch (Exception e) {
            LOGGER.error("Server autotrade failed", e);
            finishServerAutoCycleError(server, player, packet, attempts, startedNanos, "Autotrade stopped: server error", job);
        } finally {
            SERVER_AUTOCYCLE_JOBS.remove(playerId, job);
        }
    }

    private CompletableFuture<BatchResult> runServerAutoCycleBatch(MinecraftServer server, ServerPlayer player, ServerAutoCycleRequestPacket packet, int containerId, Villager villager, VillagerAccessor villagerAccessor, AutoTradeHandler.MatchPlan matchPlan, int startingAttempts, int batchSize, int maxCycles, long startedNanos, ServerAutoCycleJob job) {
        CompletableFuture<BatchResult> future = new CompletableFuture<>();
        server.execute(() -> {
            int attempts = startingAttempts;
            long batchStartedNanos = System.nanoTime();
            try {
                if (job.isCancelled()) {
                    future.complete(new BatchResult(BatchStatus.STOPPED, attempts, System.nanoTime() - batchStartedNanos));
                    return;
                }

                Optional<String> validationError = validateServerAutoCycleState(player, containerId, villager);
                if (validationError.isPresent()) {
                    sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(false, attempts, validationError.get() + formatStats(attempts, startedNanos)));
                    future.complete(new BatchResult(BatchStatus.STOPPED, attempts, System.nanoTime() - batchStartedNanos));
                    return;
                }

                long batchTimeNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, getServerAutoCycleBatchTimeMillis()));
                for (int i = 0; i < batchSize; i++) {
                    if (job.isCancelled()) {
                        future.complete(new BatchResult(BatchStatus.STOPPED, attempts, System.nanoTime() - batchStartedNanos));
                        return;
                    }
                    if (i > 0 && System.nanoTime() - batchStartedNanos >= batchTimeNanos) {
                        break;
                    }

                    attempts++;
                    cycleVillager(player, villager, villagerAccessor);
                    MerchantOffers offers = VisibleTraders.getOffers(villager);
                    AutoTradeHandler.MatchSummary matchSummary = matchPlan.findVillagerMatch(offers);
                    if (matchSummary.matched) {
                        sendOffers(player, containerId, villager);
                        sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(true, attempts, matchSummary.completionMessage() + formatTryCount(attempts) + formatStats(attempts, startedNanos)));
                        future.complete(new BatchResult(BatchStatus.MATCHED, attempts, System.nanoTime() - batchStartedNanos));
                        return;
                    }
                }

                if (job.shouldSendProgress() || attempts >= maxCycles) {
                    sendServerAutoCycleProgress(player, new ServerAutoCycleProgressPacket(attempts, maxCycles));
                }
                future.complete(new BatchResult(BatchStatus.CONTINUE, attempts, System.nanoTime() - batchStartedNanos));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private Optional<String> validateServerAutoCycleState(ServerPlayer player, int containerId, Villager villager) {
        if (!(player.containerMenu instanceof MerchantMenuAccessor merchantAccessor)) {
            return Optional.of("Autotrade stopped: villager menu closed");
        }
        if (!(player.containerMenu instanceof MerchantMenu container) || container.containerId != containerId) {
            return Optional.of("Autotrade stopped: villager menu changed");
        }
        if (merchantAccessor.getTrader() != villager) {
            return Optional.of("Autotrade stopped: trader changed");
        }
        if (container.getTraderXp() > 0) {
            return Optional.of("Autotrade stopped: villager gained XP");
        }
        if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
            return Optional.of("Autotrade stopped: villager has no job site");
        }
        return Optional.empty();
    }

    private void finishServerAutoCycleNoMatch(MinecraftServer server, ServerPlayer player, ServerAutoCycleRequestPacket packet, int containerId, Villager villager, int maxCycles, long startedNanos, ServerAutoCycleJob job) {
        server.execute(() -> {
            if (job.isCancelled()) {
                return;
            }
            if (player.containerMenu instanceof MerchantMenu container && container.containerId == containerId) {
                sendOffers(player, containerId, villager);
            }
            sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(false, maxCycles, "Autotrade stopped after " + maxCycles + " cycles without a match" + formatStats(maxCycles, startedNanos)));
        });
    }

    private void finishServerAutoCycleError(MinecraftServer server, ServerPlayer player, ServerAutoCycleRequestPacket packet, int attempts, long startedNanos, String message, ServerAutoCycleJob job) {
        server.execute(() -> {
            if (job.isCancelled()) {
                return;
            }
            sendFinishedServerAutoCycleResult(player, packet, new ServerAutoCycleResultPacket(false, attempts, message + formatStats(attempts, startedNanos)));
        });
    }

    private static void cycleVillager(ServerPlayer player, Villager villager, VillagerAccessor villagerAccessor) {
        villager.setOffers(null);
        villager.getOffers();
        villagerAccessor.invokeUpdateSpecialPrices(player);
        villager.setTradingPlayer(player);
        VisibleTraders.regenerateTrades(villager);
        VisibleTraders.requestOffers(villager, player);
    }

    private static List<AutoTradeHandler.ItemTarget> resolveItemTargets(ServerAutoCycleRequestPacket packet) {
        List<AutoTradeHandler.ItemTarget> targets = new ArrayList<>();
        for (ServerAutoCycleRequestPacket.ItemTarget target : packet.itemTargets()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(target.itemId());
            item.ifPresent(value -> targets.add(new AutoTradeHandler.ItemTarget(value, sanitizeMaxEmeralds(target.maxEmeralds()), target.tradeSide(), target.cheapest())));
        }
        return targets;
    }

    private static List<AutoTradeHandler.EnchantTarget> resolveEnchantTargets(ServerPlayer player, Villager villager, ServerAutoCycleRequestPacket packet) {
        List<AutoTradeHandler.EnchantTarget> targets = new ArrayList<>();
        var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (ServerAutoCycleRequestPacket.EnchantTarget target : packet.enchantTargets()) {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, target.enchantId());
            var holder = enchantments.get(key);
            if (holder.isPresent()) {
                int minLevel = Math.max(1, target.minLevel());
                int floor = target.cheapest() ? cheapestFloor(holder.get().is(EnchantmentTags.DOUBLE_TRADE_PRICE), minLevel, villager, player) : 0;
                targets.add(new AutoTradeHandler.EnchantTarget(key, minLevel, sanitizeMaxEmeralds(target.maxEmeralds()), target.cheapest(), floor));
            }
        }
        return targets;
    }

    /**
     * Lower bound of the effective price of an enchanted book for this villager and player, used only to stop a
     * "cheapest" search early. Deliberately conservative: if the real minimum is lower than this estimate the search
     * simply keeps going until its bounded extra-attempt budget runs out.
     */
    private static int cheapestFloor(boolean doublePrice, int level, Villager villager, ServerPlayer player) {
        int base = Math.min(64, (2 + 3 * level) * (doublePrice ? 2 : 1));
        int diff = 0;
        int reputation = villager.getPlayerReputation(player);
        if (reputation > 0) {
            diff -= Mth.floor(reputation * 0.2F); // 0.2 = largest vanilla book multiplier, so the floor never sits above the real minimum
        }
        MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero != null) {
            diff -= Math.max((int) Math.floor((0.3F + 0.0625F * hero.getAmplifier()) * base), 1);
        }
        return Math.clamp(base + diff, 1, 64);
    }

    private static int sanitizeMaxEmeralds(int maxEmeralds) {
        return maxEmeralds <= 0 ? Integer.MAX_VALUE : maxEmeralds;
    }

    private static int sanitizeCycleCount(int cycles) {
        return Math.max(1, cycles);
    }

    private static String formatTryCount(int tries) {
        return " after " + tries + " " + (tries == 1 ? "try" : "tries");
    }

    private static String formatStats(int tries, long startedNanos) {
        double elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000D;
        String average = tries <= 0 ? "n/a" : String.format(Locale.ROOT, "%.3f ms/trade", elapsedMillis / tries);
        return String.format(Locale.ROOT, " | Time: %.2f ms | Avg: %s", elapsedMillis, average);
    }

    private static String formatWorkerCount(int workers) {
        return " | Workers: " + workers;
    }

    private void sendFinishedServerAutoCycleResult(ServerPlayer player, ServerAutoCycleRequestPacket request, ServerAutoCycleResultPacket result) {
        if (request.sendResultToServer()) {
            LOGGER.info("{} {}", player.getName().getString(), result.message());
        }
        sendServerAutoCycleResult(player, result);
    }

    private static void sendOffers(ServerPlayer player, int containerId, Villager villager) {
        MerchantOffers offers = VisibleTraders.getOffers(villager);
        boolean sentVisibleTradersOffers = VisibleTraders.sendTrades(player, villager, containerId, offers, VisibleTraders.getLevel(villager), villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
        if (sentVisibleTradersOffers) {
            return;
        }
        player.sendMerchantOffers(containerId, offers, VisibleTraders.getLevel(villager), villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
    }

    private enum BatchStatus {
        CONTINUE,
        MATCHED,
        STOPPED
    }

    private static class BatchResult {
        private final BatchStatus status;
        private final int attempts;
        private final long elapsedNanos;

        private BatchResult(BatchStatus status, int attempts, long elapsedNanos) {
            this.status = status;
            this.attempts = attempts;
            this.elapsedNanos = elapsedNanos;
        }
    }

    private static class ServerAutoCycleJob {
        private final AtomicBoolean cancelled;
        private net.minecraft.world.entity.ai.gossip.GossipContainer gossips;
        private long lastProgressNanos;
        private int batchSize;

        private ServerAutoCycleJob() {
            this.cancelled = new AtomicBoolean(false);
            this.lastProgressNanos = System.nanoTime();
            this.batchSize = SERVER_AUTOCYCLE_INITIAL_BATCH_SIZE;
        }

        private void cancel() {
            cancelled.set(true);
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private boolean shouldSendProgress() {
            long now = System.nanoTime();
            if (now - lastProgressNanos < SERVER_AUTOCYCLE_PROGRESS_INTERVAL_NANOS) {
                return false;
            }
            lastProgressNanos = now;
            return true;
        }

        private int getBatchSize() {
            return batchSize;
        }

        private void updateBatchSize(int completedCycles, long elapsedNanos, int targetMillis) {
            if (completedCycles <= 0 || elapsedNanos <= 0L) {
                return;
            }
            long targetNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, targetMillis));
            long estimatedCycles = (long) Math.floor((double) targetNanos * (double) completedCycles / (double) elapsedNanos);
            if (estimatedCycles <= 0L) {
                return;
            }
            int tunedSize = (int) Math.clamp(estimatedCycles, 1L, SERVER_AUTOCYCLE_MAX_BATCH_SIZE);
            batchSize = (batchSize + tunedSize) / 2;
        }
    }

    private static class AutoCycleThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Trade Cycling Server Autotrade");
            thread.setDaemon(true);
            return thread;
        }
    }

}









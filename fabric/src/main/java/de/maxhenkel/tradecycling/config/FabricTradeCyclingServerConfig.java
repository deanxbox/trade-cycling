package de.maxhenkel.tradecycling.config;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.configbuilder.entry.ConfigEntry;

public class FabricTradeCyclingServerConfig {

    public final ConfigEntry<Integer> maxServerAutoTradeCycles;
    public final ConfigEntry<Integer> serverAutoTradeBatchTimeMillis;
    public final ConfigEntry<Integer> serverAutoTradeBatchDelayMillis;

    public FabricTradeCyclingServerConfig(ConfigBuilder builder) {
        maxServerAutoTradeCycles = builder.integerEntry("server_autotrade_max_cycles", 1_000_000, 1, Integer.MAX_VALUE);
        serverAutoTradeBatchTimeMillis = builder.integerEntry("server_autotrade_batch_time_ms", 10, 1, 50);
        serverAutoTradeBatchDelayMillis = builder.integerEntry("server_autotrade_batch_delay_ms", 0, 0, 100);
    }

    public int getMaxServerAutoTradeCycles() {
        return maxServerAutoTradeCycles.get();
    }

    public void setMaxServerAutoTradeCycles(int maxCycles) {
        maxServerAutoTradeCycles.set(Math.max(1, maxCycles));
        maxServerAutoTradeCycles.save();
    }

    public int getServerAutoTradeBatchTimeMillis() {
        return serverAutoTradeBatchTimeMillis.get();
    }

    public void setServerAutoTradeBatchTimeMillis(int millis) {
        serverAutoTradeBatchTimeMillis.set(Math.clamp(millis, 1, 50));
        serverAutoTradeBatchTimeMillis.save();
    }

    public int getServerAutoTradeBatchDelayMillis() {
        return serverAutoTradeBatchDelayMillis.get();
    }

    public void setServerAutoTradeBatchDelayMillis(int millis) {
        serverAutoTradeBatchDelayMillis.set(Math.clamp(millis, 0, 100));
        serverAutoTradeBatchDelayMillis.save();
    }
}

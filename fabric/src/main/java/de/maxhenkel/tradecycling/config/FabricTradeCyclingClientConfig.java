package de.maxhenkel.tradecycling.config;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.configbuilder.entry.ConfigEntry;
public class FabricTradeCyclingClientConfig extends TradeCyclingClientConfig {

    public final ConfigEntry<CycleTradesButtonLocation> cycleTradesButtonLocation;
    public final ConfigEntry<Integer> tradeCycleDelayTicks;
    public final ConfigEntry<Boolean> autoTradeMatchAny;
    public final ConfigEntry<Integer> maxAutoTradeCycles;
    public final ConfigEntry<Boolean> announceAutoTradeResultsInChat;
    public final ConfigEntry<Boolean> sendAutoTradeResultsToServer;

    public FabricTradeCyclingClientConfig(ConfigBuilder builder) {
        cycleTradesButtonLocation = builder.enumEntry("cycle_trades_button_location", CycleTradesButtonLocation.TOP_LEFT);
        tradeCycleDelayTicks = builder.integerEntry("trade_cycle_delay_ticks", 1, 1, 100);
        autoTradeMatchAny = builder.booleanEntry("autotrade_match_any", false);
        maxAutoTradeCycles = builder.integerEntry("autotrade_max_cycles", 1000, 1, Integer.MAX_VALUE);
        announceAutoTradeResultsInChat = builder.booleanEntry("autotrade_announce_results_in_chat", true);
        sendAutoTradeResultsToServer = builder.booleanEntry("autotrade_send_results_to_server", false);
    }

    @Override
    public CycleTradesButtonLocation getCycleTradesButtonLocation() {
        return cycleTradesButtonLocation.get();
    }

    @Override
    public int getTradeCycleDelayTicks() {
        return tradeCycleDelayTicks.get();
    }

    @Override
    public boolean getAutoTradeMatchAny() {
        return autoTradeMatchAny.get();
    }

    @Override
    public int getMaxAutoTradeCycles() {
        return maxAutoTradeCycles.get();
    }

    @Override
    public boolean getAnnounceAutoTradeResultsInChat() {
        return announceAutoTradeResultsInChat.get();
    }

    @Override
    public boolean getSendAutoTradeResultsToServer() {
        return sendAutoTradeResultsToServer.get();
    }

    @Override
    public void setTradeCycleDelayTicks(int ticks) {
        tradeCycleDelayTicks.set(ticks);
        tradeCycleDelayTicks.save();
    }

    @Override
    public void setAutoTradeMatchAny(boolean matchAny) {
        autoTradeMatchAny.set(matchAny);
        autoTradeMatchAny.save();
    }

    @Override
    public void setMaxAutoTradeCycles(int maxCycles) {
        maxAutoTradeCycles.set(maxCycles);
        maxAutoTradeCycles.save();
    }

    @Override
    public void setCycleTradesButtonLocation(CycleTradesButtonLocation location) {
        cycleTradesButtonLocation.set(location);
        cycleTradesButtonLocation.save();
    }

    @Override
    public void setAnnounceAutoTradeResultsInChat(boolean announce) {
        announceAutoTradeResultsInChat.set(announce);
        announceAutoTradeResultsInChat.save();
    }

    @Override
    public void setSendAutoTradeResultsToServer(boolean sendToServer) {
        sendAutoTradeResultsToServer.set(sendToServer);
        sendAutoTradeResultsToServer.save();
    }
}

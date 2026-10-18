package de.maxhenkel.tradecycling.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ForgeTradeCyclingClientConfig extends TradeCyclingClientConfig {

    public final ForgeConfigSpec.EnumValue<CycleTradesButtonLocation> cycleTradesButtonLocation;
    public final ForgeConfigSpec.IntValue tradeCycleDelayTicks;
    public final ForgeConfigSpec.BooleanValue autoTradeMatchAny;
    public final ForgeConfigSpec.IntValue maxAutoTradeCycles;
    public final ForgeConfigSpec.BooleanValue announceAutoTradeResultsInChat;
    public final ForgeConfigSpec.BooleanValue sendAutoTradeResultsToServer;

    public ForgeTradeCyclingClientConfig(ForgeConfigSpec.Builder builder) {
        cycleTradesButtonLocation = builder
                .comment("The location of the cycle trades button")
                .defineEnum("cycle_trades_button_location", CycleTradesButtonLocation.TOP_LEFT);
        tradeCycleDelayTicks = builder
                .comment("Delay between autotrade refresh checks in ticks")
                .defineInRange("trade_cycle_delay_ticks", 3, 1, 100);
        autoTradeMatchAny = builder
                .comment("When true, autotrade stops after any target matches instead of requiring all targets")
                .define("autotrade_match_any", false);
        maxAutoTradeCycles = builder
                .comment("Maximum number of automatic trade cycles before autotrade stops")
                .defineInRange("autotrade_max_cycles", 1000, 1, Integer.MAX_VALUE);
        announceAutoTradeResultsInChat = builder
                .comment("Announce completed autotrade stats in the client chat")
                .define("autotrade_announce_results_in_chat", true);
        sendAutoTradeResultsToServer = builder
                .comment("Send completed server autotrade stats to the server log")
                .define("autotrade_send_results_to_server", false);
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
    }

    @Override
    public void setAutoTradeMatchAny(boolean matchAny) {
        autoTradeMatchAny.set(matchAny);
    }

    @Override
    public void setMaxAutoTradeCycles(int maxCycles) {
        maxAutoTradeCycles.set(maxCycles);
    }

    @Override
    public void setCycleTradesButtonLocation(CycleTradesButtonLocation location) {
        cycleTradesButtonLocation.set(location);
    }

    @Override
    public void setAnnounceAutoTradeResultsInChat(boolean announce) {
        announceAutoTradeResultsInChat.set(announce);
    }

    @Override
    public void setSendAutoTradeResultsToServer(boolean sendToServer) {
        sendAutoTradeResultsToServer.set(sendToServer);
    }
}

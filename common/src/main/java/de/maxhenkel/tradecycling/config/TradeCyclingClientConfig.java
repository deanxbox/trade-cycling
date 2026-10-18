package de.maxhenkel.tradecycling.config;

public abstract class TradeCyclingClientConfig {

    public abstract CycleTradesButtonLocation getCycleTradesButtonLocation();
    public abstract int getTradeCycleDelayTicks();
    public abstract boolean getAutoTradeMatchAny();
    public abstract int getMaxAutoTradeCycles();
    public abstract boolean getAnnounceAutoTradeResultsInChat();
    public abstract boolean getSendAutoTradeResultsToServer();

    public void setTradeCycleDelayTicks(int ticks) {
    }

    public void setAutoTradeMatchAny(boolean matchAny) {
    }

    public void setMaxAutoTradeCycles(int maxCycles) {
    }

    public void setCycleTradesButtonLocation(CycleTradesButtonLocation location) {
    }

    public void setAnnounceAutoTradeResultsInChat(boolean announce) {
    }

    public void setSendAutoTradeResultsToServer(boolean sendToServer) {
    }

    public static enum CycleTradesButtonLocation {
        TOP_LEFT, TOP_RIGHT, NONE
    }
}

package com.kuhen.cryptopro.data;

import com.kuhen.cryptopro.data.model.Candle;
import com.kuhen.cryptopro.data.model.FundingRate;
import com.kuhen.cryptopro.data.model.FeedStatus;
import com.kuhen.cryptopro.data.model.LiquidationEvent;
import com.kuhen.cryptopro.data.model.OpenInterestSnapshot;
import com.kuhen.cryptopro.data.model.OrderBookSnapshot;
import com.kuhen.cryptopro.data.model.Timeframe;

import java.util.List;

public interface MarketDataProvider {

    List<Candle> getRecentCandles(String symbol, Timeframe timeframe, int limit);

    OrderBookSnapshot getLatestOrderBook(String symbol);

    FundingRate getLatestFundingRate(String symbol);

    List<OpenInterestSnapshot> getRecentOpenInterest(String symbol, int limit);

    List<LiquidationEvent> getRecentLiquidations(String symbol, int limit);

    FeedStatus getFeedStatus(String symbol);
}



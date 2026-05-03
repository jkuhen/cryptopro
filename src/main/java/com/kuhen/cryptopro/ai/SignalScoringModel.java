package com.kuhen.cryptopro.ai;

public interface SignalScoringModel {

    SignalScoringResult score(SignalScoringFeatures features);
}



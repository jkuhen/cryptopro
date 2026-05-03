package com.kuhen.cryptopro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cryptopro.automation")
public class AutomationProperties {

    private boolean enabled = true;
    private final Backtest backtest = new Backtest();
    private final Persistence persistence = new Persistence();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Backtest getBacktest() {
        return backtest;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public static class Backtest {
        private boolean enabled = true;
        private String cron = "0 0 */6 * * *";
        private String inputDir = "data";
        private String filePattern = "Binance_*USDT_d.csv";
        private String outputDir = "ml/reports/auto_backtest";
        private int maxCandles = 0;
        private int warmupCandles = 120;
        private int atrPeriod = 14;
        private double quantity = 1.0;
        private double stopLossAtrMultiplier = 2.0;
        private double takeProfitAtrMultiplier = 3.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getInputDir() {
            return inputDir;
        }

        public void setInputDir(String inputDir) {
            this.inputDir = inputDir;
        }

        public String getFilePattern() {
            return filePattern;
        }

        public void setFilePattern(String filePattern) {
            this.filePattern = filePattern;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }

        public int getMaxCandles() {
            return maxCandles;
        }

        public void setMaxCandles(int maxCandles) {
            this.maxCandles = maxCandles;
        }

        public int getWarmupCandles() {
            return warmupCandles;
        }

        public void setWarmupCandles(int warmupCandles) {
            this.warmupCandles = warmupCandles;
        }

        public int getAtrPeriod() {
            return atrPeriod;
        }

        public void setAtrPeriod(int atrPeriod) {
            this.atrPeriod = atrPeriod;
        }

        public double getQuantity() {
            return quantity;
        }

        public void setQuantity(double quantity) {
            this.quantity = quantity;
        }

        public double getStopLossAtrMultiplier() {
            return stopLossAtrMultiplier;
        }

        public void setStopLossAtrMultiplier(double stopLossAtrMultiplier) {
            this.stopLossAtrMultiplier = stopLossAtrMultiplier;
        }

        public double getTakeProfitAtrMultiplier() {
            return takeProfitAtrMultiplier;
        }

        public void setTakeProfitAtrMultiplier(double takeProfitAtrMultiplier) {
            this.takeProfitAtrMultiplier = takeProfitAtrMultiplier;
        }
    }

    public static class Persistence {
        private boolean enabled = true;
        private String cron = "0 10 */6 * * *";
        private String inputDir = "data";
        private String filePattern = "Binance_*USDT_d.csv";
        private String outputRoot = "ml/artifacts/history";
        private String analysisSourceDir = "ml/reports/history_backtest";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getInputDir() {
            return inputDir;
        }

        public void setInputDir(String inputDir) {
            this.inputDir = inputDir;
        }

        public String getFilePattern() {
            return filePattern;
        }

        public void setFilePattern(String filePattern) {
            this.filePattern = filePattern;
        }

        public String getOutputRoot() {
            return outputRoot;
        }

        public void setOutputRoot(String outputRoot) {
            this.outputRoot = outputRoot;
        }

        public String getAnalysisSourceDir() {
            return analysisSourceDir;
        }

        public void setAnalysisSourceDir(String analysisSourceDir) {
            this.analysisSourceDir = analysisSourceDir;
        }
    }
}


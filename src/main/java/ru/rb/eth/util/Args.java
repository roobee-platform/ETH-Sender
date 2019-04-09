package ru.rb.eth.util;

import com.beust.jcommander.Parameter;

public class Args {

    @Parameter(names = { "--xlsx", "-x" }, description = "Path to .xlsx table with data", required = true)
    private String xlsxPath;

    @Parameter(names = { "--address", "-a" }, description = "Smart contract address in Ethereum network", required = true)
    private String contractAdress;

    @Parameter(names = { "--timer", "-t" }, description = "Time between saving progress (in seconds)")
    private long time = 3600;

    @Parameter(names = { "--tx-timer", "-o" }, description = "Time between sending transactions (in milliseconds)")
    private long txTime = 3000;

    @Parameter(names = { "--private", "-p" }, description = "Private key for provided account", password = true, echoInput = true)
    private String privateKey;

    @Parameter(names = { "--url", "-u" }, description = "Node URL")
    private String url;

    public String getXlsxPath() {
        return xlsxPath;
    }

    public String getContractAdress() {
        return contractAdress;
    }

    public long getTime() {
        return time;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public long getTxTime() {
        return txTime;
    }

    public String getUrl() {
        return url;
    }
}
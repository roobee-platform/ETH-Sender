package ru.rb.eth.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Util {

    public static boolean isValidAddress(String address) {
        return address.matches("0x[A-Fa-f0-9]{40}");
    }

    public static boolean isValidAmount(String amount) {
        int lastCommaIndex = amount.lastIndexOf('.');
        if(lastCommaIndex == -1) {
            return amount.matches("\\d+");
        } else {
            return amount.matches("\\d+.?\\d*") && amount.substring(lastCommaIndex).length() <= 19;
        }
    }

    public static double getEthPrice() {
        Logger log = LoggerFactory.getLogger(Util.class);
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://api.coinmarketcap.com/v1/ticker/ethereum/")
                    .build();
            Response response = client.newCall(request).execute();
            if (response.body() != null) {
                ObjectNode[] nodes = new ObjectMapper().readValue(response.body().string(), ObjectNode[].class);
                return nodes[0].get("price_usd").asDouble();
            }
        } catch (IOException e) {
            log.error("", e);
        }
        return Double.NaN;
    }
}
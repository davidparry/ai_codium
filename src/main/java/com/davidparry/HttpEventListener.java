package com.davidparry;

import okhttp3.Call;
import okhttp3.EventListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HttpEventListener extends EventListener {
    private static final Logger logger = LoggerFactory.getLogger(HttpEventListener.class);
    @Override
    public void callFailed(@NotNull Call call, @NotNull IOException ioe) {
        super.callFailed(call, ioe);
        logger.error("CallFailed {} ", call, ioe);
    }

    @Override
    public void responseFailed(@NotNull Call call, @NotNull IOException ioe) {
        super.responseFailed(call, ioe);
        logger.error("ResponseFailed {} ", call, ioe);
    }

    @Override
    public void requestFailed(@NotNull Call call, @NotNull IOException ioe) {
        super.requestFailed(call, ioe);
        logger.error("RequestFailed {} ", call, ioe);
    }
}



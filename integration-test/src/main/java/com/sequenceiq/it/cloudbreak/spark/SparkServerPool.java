package com.sequenceiq.it.cloudbreak.spark;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class SparkServerPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(SparkServerPool.class);

    private static final AtomicInteger NEXT_PORT = new AtomicInteger(9400);

    private final Map<SparkServer, Boolean> servers = new HashMap<>();

    private static File keystoreFile;

    private final String endpoint;

    private final boolean printRequestBody;

    public SparkServerPool(int initialSparkPoolSize, boolean printRequestBody, String endpoint) {
        this.printRequestBody = printRequestBody;
        this.endpoint = endpoint;
        for (int i = 0; i < initialSparkPoolSize; i++) {
            initializeSpark();
        }
    }

    static {
        LOGGER.info("Preparing SparkServer keystore file");
        try {
            InputStream sshPemInputStream = new ClassPathResource("/keystore_server").getInputStream();
            File tempKeystoreFile = File.createTempFile("/keystore_server", ".tmp");
            try (OutputStream outputStream = new FileOutputStream(tempKeystoreFile)) {
                IOUtils.copy(sshPemInputStream, outputStream);
            } catch (IOException e) {
                LOGGER.error("can't write " + "/keystore_server", e);
                throw e;
            }
            keystoreFile = tempKeystoreFile;
        } catch (IOException e) {
            throw new RuntimeException("/keystore_server" + " not found", e);
        }
    }

    public SparkServer pop() {
        synchronized (servers) {
            LOGGER.info("Spark server popped. Pool size: {}", servers.entrySet().stream().filter(Entry::getValue).count());
            if (servers.entrySet().stream().noneMatch(Entry::getValue)) {
                initializeSpark();
            }
            Optional<Entry<SparkServer, Boolean>> found = servers.entrySet().stream().filter(Entry::getValue).findFirst();
            Entry<SparkServer, Boolean> entry = found.orElseThrow();
            SparkServer sparkServer = entry.getKey();
            LOGGER.info("POP chosen one: {}", sparkServer);
            LOGGER.info("POP state: {}", sparkServer.getEndpoint());
            logServers();
            entry.setValue(Boolean.FALSE);
            long start = System.currentTimeMillis();
            sparkServer.init();
            sparkServer.awaitInitialization();
            LOGGER.info("Spark has been initalized in {}ms", System.currentTimeMillis() - start);
            return sparkServer;
        }
    }

    private void initializeSpark() {
        LOGGER.info("Spark server pool is empty - creating spark server. Pool size: {}", servers.entrySet().stream().filter(Entry::getValue).count());
        SparkServer server = new SparkServer(NEXT_PORT.incrementAndGet(), keystoreFile, endpoint, printRequestBody);
        servers.put(server, Boolean.TRUE);
    }

    private void logServers() {
        servers.forEach((key, value) -> LOGGER.debug("servers - [{}]", key + "::" + value));
    }

    public void put(SparkServer sparkServer) {
        synchronized (servers) {
            if (!servers.get(sparkServer)) {
                LOGGER.info("Spark server put back. Pool size: {}", servers.entrySet().stream().filter(Entry::getValue).count());
                LOGGER.info("PUT state: {}", sparkServer.getEndpoint());
                logServers();
                long start = System.currentTimeMillis();
                sparkServer.stop();
                sparkServer.awaitStop();
                LOGGER.info("spark server has been cleared in {}ms.", System.currentTimeMillis() - start);
                servers.put(sparkServer, Boolean.TRUE);
                servers.notify();
            }
        }
    }

    @PreDestroy
    public void autoShutdown() {
        LOGGER.info("Invoking PreDestroy for Spark Pool bean");
        servers.keySet().forEach(SparkServer::stop);
    }
}

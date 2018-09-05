package com.aaxis.zookeeper.zookeeperlock;

import lombok.Data;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author paynejia
 * This class aims at testing zookeeper distrubited lock with multiple thread to minic multiple clients
 */
@RestController
@Data
public class ZookeeperLockController {

    protected static final Logger logger = LoggerFactory.getLogger(ZookeeperLockController.class);

    @Value("${zookeeper.connection.info}")
    private String zookeeperConnectionInfo;
    /**
     * @param
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @RequestMapping(value = "/testlock")
    public String testZookeeperLock()  throws InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(5);
        //String zookeeperConnectionString = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(
                this.zookeeperConnectionInfo, retryPolicy);
        client.start();
        logger.info("==================Client starting==================");
        ExecutorService exec = Executors.newCachedThreadPool();
        List<Future<String>> future =  null;
        Set<Callable<String>> callables = new HashSet<Callable<String>>();
        for (int i = 0; i < 5; i++) {
            //future = exec.submit(new MyLock("client" + i, client, latch));
            callables.add(new Worker("client" + i, client, latch));
        }
        future = exec.invokeAll(callables);
        for (Future<String> element : future) {
            logger.info("future.get() = " + element.get());
        }
        exec.shutdown();
        latch.await();
        logger.info("==================All tasks finished==================");
        client.close();
        logger.info("==================Client closed==================");
        return "zookeeper lock";
    }

    static class Worker implements Callable {
        private String name;
        private CuratorFramework client;
        private CountDownLatch latch;

        public Worker(String name, CuratorFramework client, CountDownLatch latch) {
            this.name = name;
            this.client = client;
            this.latch = latch;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String call() {
            InterProcessMutex lock = new InterProcessMutex(client,
                    "/test_group");
            try {
                if (lock.acquire(120, TimeUnit.SECONDS)) {
                    try {
                        logger.info("----------" + this.name
                                + "Get lock----------");
                        logger.info("----------" + this.name
                                + "Processing resource----------");
                        Thread.sleep(10 * 1000);
                        logger.info("----------" + this.name
                                + "Release resource----------");
                        latch.countDown();
                    } finally {
                        lock.release();
                        logger.info("----------" + this.name
                                + "Release lock----------");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this.name + "returned!";
        }
    }
}

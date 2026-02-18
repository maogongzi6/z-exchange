package com.exchange.upstream.wallet;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Objects;

@Import(value = RedisConfig.class)
@EnableCaching
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class RedissonTest {
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    RedisTemplate<String, String> redisTemplate;
    @Autowired
    RedisTemplate<String, Object> redisTemplate2;

    @Test
    public void test() throws InterruptedException {
        RKeys keys = redissonClient.getKeys();
        Iterable<String> i = keys.getKeys();
        for (Iterator<String> iterator = i.iterator(); iterator.hasNext(); ) {
            System.out.println(iterator.next());
        }
        Thread.sleep(60_000);
    }

    @Test
    public void redisTest() {
        redisTemplate2.opsForValue().set("h2", new S("w2"));
        Object v = redisTemplate2.opsForValue().get("h2");
        System.out.println(v);

        redisTemplate.opsForValue().get("h");
    }
}

@Data
@AllArgsConstructor
class S implements Serializable {
    String s;
}
package com.cityroam;

import com.cityroam.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CityRoamApplicationTests {

    @Test
    void nextIdCombinesTimeAndDailyRedisSequenceWithoutExternalRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment(startsWith("icrorder:"))).thenReturn(1L, 2L);
        RedisWorker redisWorker = new RedisWorker(redisTemplate);

        long first = redisWorker.nextId("order");
        long second = redisWorker.nextId("order");

        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        verify(values, times(2)).increment(startsWith("icrorder:"));
    }
}

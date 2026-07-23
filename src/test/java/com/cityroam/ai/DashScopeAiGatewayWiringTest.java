package com.cityroam.ai;

import com.cityroam.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeAiGatewayWiringTest {

    @Test
    void marksTheApplicationConstructorForSpringInjection() throws NoSuchMethodException {
        Constructor<DashScopeAiGateway> constructor = DashScopeAiGateway.class.getConstructor(AiProperties.class);

        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }
}

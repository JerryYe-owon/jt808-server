package org.yzh.web.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.yzh.web.model.enums.Video360Mode;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JTPropertiesVideo360Test {

    @ParameterizedTest
    @EnumSource(Video360Mode.class)
    void resolvesDefaultChannelMap(Video360Mode mode) {
        JTProperties properties = new JTProperties();

        assertEquals(mode.getDefaultChannelNo(), properties.resolveVideo360Channel(mode));
    }

    @Test
    void bindsConfiguredChannelsAndKeepsDefaultsForMissingModes() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jt-server.jt808.video360.channels.panorama-left", "201")
                .withProperty("jt-server.jt808.video360.channels.keep-rotation", "202");

        JTProperties properties = Binder.get(environment)
                .bind("jt-server.jt808", JTProperties.class)
                .get();

        assertEquals(201, properties.resolveVideo360Channel(Video360Mode.PANORAMA_LEFT));
        assertEquals(202, properties.resolveVideo360Channel(Video360Mode.KEEP_ROTATION));
        assertEquals(240, properties.resolveVideo360Channel(Video360Mode.PANORAMA));
    }

    @Test
    void rejectsChannelsOutsideByteRange() {
        EnumMap<Video360Mode, Integer> channels = new EnumMap<>(Video360Mode.class);
        channels.put(Video360Mode.PANORAMA, 256);

        JTProperties.CVideo360 video360 = new JTProperties.CVideo360();

        assertThrows(IllegalArgumentException.class, () -> video360.setChannels(channels));
    }

    @Test
    void validatesDirectlyMutatedBoundChannelsOnStartup() {
        JTProperties properties = new JTProperties();
        properties.getVideo360().getChannels().put(Video360Mode.PANORAMA, -1);

        assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);
    }
}

package org.yzh.web.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 360 video modes are modeled as virtual JT1078 channel numbers.
 */
@Getter
public enum Video360Mode {

    PANORAMA("panorama", 0xF0),
    PANORAMA_ANY("any", 0xF1),
    PANORAMA_REAR("rear", 0xF2),
    PANORAMA_FRONT("front", 0xF3),
    PANORAMA_LEFT("left", 0xF4),
    PANORAMA_RIGHT("right", 0xF5),
    KEEP_ROTATION("keep-rotation", 0xF6);

    private static final Map<String, Video360Mode> PATH_VALUES = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Video360Mode::getPath, Function.identity()));

    private final String path;
    private final int defaultChannelNo;

    Video360Mode(String path, int defaultChannelNo) {
        this.path = path;
        this.defaultChannelNo = defaultChannelNo;
    }

    public static Video360Mode fromPath(String path) {
        Video360Mode mode = PATH_VALUES.get(path);
        if (mode == null)
            throw new IllegalArgumentException("Unsupported 360 video mode: " + path);
        return mode;
    }

    public static EnumMap<Video360Mode, Integer> defaultChannels() {
        EnumMap<Video360Mode, Integer> channels = new EnumMap<>(Video360Mode.class);
        for (Video360Mode mode : values())
            channels.put(mode, mode.defaultChannelNo);
        return channels;
    }

    public int resolveChannelNo(Map<Video360Mode, Integer> channels) {
        Integer channelNo = channels == null ? null : channels.get(this);
        return validateChannelNo(this, channelNo == null ? defaultChannelNo : channelNo);
    }

    public static int validateChannelNo(Video360Mode mode, int channelNo) {
        if (channelNo < 0 || channelNo > 255)
            throw new IllegalArgumentException("360 video channel for " + mode.name() + " must be in range 0..255: " + channelNo);
        return channelNo;
    }
}

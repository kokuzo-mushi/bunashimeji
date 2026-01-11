package com.group_finity.mascot.platform;

import com.group_finity.mascot.Mascot;

public interface Platform {
    void createMascot(int x, int y, int vx, int vy);
    void removeMascot(Mascot mascot);
    Mascot getNearestMascot(Mascot mascot);

    class Instance {
        private static Platform instance;
    }

    static void setInstance(Platform platform) {
        Instance.instance = platform;
    }

    static Platform getInstance() {
        return Instance.instance;
    }
}
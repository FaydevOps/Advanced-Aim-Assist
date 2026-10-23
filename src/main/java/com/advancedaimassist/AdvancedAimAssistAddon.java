package com.advancedaimassist;

import com.advancedaimassist.modules.AdvancedAimAssist;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AdvancedAimAssistAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Advanced Aim Assist");

        Modules.get().add(new AdvancedAimAssist());
    }

    @Override
    public String getPackage() {
        return "com.advancedaimassist";
    }
}

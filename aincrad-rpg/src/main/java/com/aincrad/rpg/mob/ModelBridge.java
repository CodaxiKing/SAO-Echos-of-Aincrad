package com.aincrad.rpg.mob;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Ponte para o ModelEngine 4, por reflexão: põe um modelo sobre um mob do Minecraft e esconde o
 * mob de baixo. (Mobs do MythicMobs já escolhem o próprio modelo no arquivo deles.)
 */
final class ModelBridge {

    boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
    }

    /** True se o modelo foi aplicado. */
    boolean apply(Entity e, String model) {
        if (!available()) {
            return false;
        }
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Class<?> modeledType = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeType = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Object active = api.getMethod("createActiveModel", String.class).invoke(null, model);
            if (active == null) {
                return false;
            }
            Object modeled = api.getMethod("createModeledEntity", Entity.class).invoke(null, e);
            modeledType.getMethod("addModel", activeType, boolean.class).invoke(modeled, active, true);
            try {
                modeledType.getMethod("setBaseEntityVisible", boolean.class).invoke(modeled, false);
            } catch (NoSuchMethodException ignored) {
                // Versão sem esse método: o mob de baixo fica visível.
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}

package net.cloud.betterclouds.forge.mixin;

import net.minecraft.client.gl.GlShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlShader.class)
public interface ShaderProgramAccessor {
    @Accessor("activeProgramGlRef")
    static int getActiveProgramGlRef() {
        throw new AssertionError();
    }

    @Accessor("activeProgramGlRef")
    static void setActiveProgramGlRef(int id) {
        throw new AssertionError();
    }
}

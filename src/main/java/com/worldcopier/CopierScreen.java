package com.worldcopier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class CopierScreen extends Screen {
    private ButtonWidget toggleButton;

    public CopierScreen() {
        super(Text.literal("World Copier"));
    }

    private Text toggleLabel() {
        return Text.literal("Kopiowanie: " + (CopierManager.isEnabled() ? "WŁĄCZONE" : "WYŁĄCZONE"));
    }

    @Override
    protected void init() {
        toggleButton = ButtonWidget.builder(toggleLabel(), b -> {
            CopierManager.toggle(this.client);
            b.setMessage(toggleLabel());
        }).dimensions(this.width / 2 - 100, this.height / 2 - 10, 200, 20).build();
        addDrawableChild(toggleButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> this.close())
                .dimensions(this.width / 2 - 100, this.height / 2 + 20, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int y = this.height / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, y - 50, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Zapisane chunki: " + CopierManager.count()), cx, y - 32, 0xFFFF55);
        String path = CopierManager.rootPathString();
        if (path != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(path), cx, y + 50, 0xAAAAAA);
        }
    }
}

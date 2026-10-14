
package dev.deathcompass;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SecondaryKeybindsScreen extends Screen {
    private final Screen parent;
    private String filter = "";
    private int page;
    private boolean confirmReset;
    private KeyMapping listening;
    private boolean editingPrimary;
    private EditBox search;
    private final List<KeyMapping> visible = new ArrayList<>();

    public SecondaryKeybindsScreen(Screen parent) {
        super(Component.literal("Secondary Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        visible.clear();

        String lookup = filter.toLowerCase(Locale.ROOT).trim();

        for (KeyMapping mapping : SecondaryKeybinds.allBindings()) {
            if (
                lookup.isEmpty() ||
                Component.translatable(mapping.getName())
                    .getString()
                    .toLowerCase(Locale.ROOT)
                    .contains(lookup) ||
                mapping.getName()
                    .toLowerCase(Locale.ROOT)
                    .contains(lookup) ||
                mapping.getCategory()
                    .toString()
                    .toLowerCase(Locale.ROOT)
                    .contains(lookup)
            ) {
                visible.add(mapping);
            }
        }

        int left = width / 2 - 207;
        int top = 62;

        search = new EditBox(
            font,
            left,
            27,
            246,
            20,
            Component.literal("Filter keys")
        );

        search.setValue(filter);
        addRenderableWidget(search);

        addRenderableWidget(
            Button.builder(
                Component.literal("Search"),
                b -> {
                    filter = search.getValue().trim();
                    page = 0;
                    rebuildWidgets();
                }
            ).bounds(left + 252, 27, 78, 20).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Clear"),
                b -> {
                    filter = "";
                    page = 0;
                    rebuildWidgets();
                }
            ).bounds(left + 336, 27, 78, 20).build()
        );

        int shown = pageSize();
        int lastPage = Math.max(
            0,
            (visible.size() - 1) / shown
        );

        if (page > lastPage) {
            page = lastPage;
        }

        for (
            int i = page * shown;
            i < Math.min(visible.size(), (page + 1) * shown);
            i++
        ) {
            KeyMapping mapping = visible.get(i);

            int y = top + (i - page * shown) * 23;

            String binding = SecondaryKeybinds.label(mapping);

            if (SecondaryKeybinds.conflicts(mapping)) {
                binding = "! " + binding;
            }

            final KeyMapping action = mapping;

            String primaryLabel =
                mapping.getTranslatedKeyMessage().getString();

            addRenderableWidget(
                Button.builder(
                    Component.literal(
                        listening == mapping && editingPrimary
                            ? "Press key..."
                            : shorten(primaryLabel, 13)
                    ),
                    b -> {
                        listening = action;
                        editingPrimary = true;
                        rebuildWidgets();
                    }
                ).bounds(left + 158, y, 94, 20).build()
            );

            addRenderableWidget(
                Button.builder(
                    Component.literal(
                        listening == mapping && !editingPrimary
                            ? "Press key..."
                            : shorten(binding, 15)
                    ),
                    b -> {
                        listening = action;
                        editingPrimary = false;
                        rebuildWidgets();
                    }
                ).bounds(left + 256, y, 104, 20).build()
            );

            addRenderableWidget(
                Button.builder(
                    Component.literal("Reset"),
                    b -> {
                        SecondaryKeybinds.clear(action);
                        listening = null;
                        rebuildWidgets();
                    }
                ).bounds(left + 365, y, 49, 20).build()
            );
        }

        int foot = height - 53;

        addRenderableWidget(
            Button.builder(
                Component.literal("< Previous"),
                b -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                }
            ).bounds(left, foot, 95, 20).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Next >"),
                b -> {
                    page = Math.min(lastPage, page + 1);
                    rebuildWidgets();
                }
            ).bounds(left + 103, foot, 95, 20).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal(
                    confirmReset
                        ? "Confirm reset ALL"
                        : "Reset all secondary"
                ),
                b -> {
                    if (confirmReset) {
                        SecondaryKeybinds.clearAll();
                        confirmReset = false;
                        listening = null;
                    } else {
                        confirmReset = true;
                    }

                    rebuildWidgets();
                }
            ).bounds(left + 207, foot, 134, 20).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Done"),
                b -> onClose()
            ).bounds(left + 346, foot, 68, 20).build()
        );
    }

    private int pageSize() {
        return Math.max(3, (height - 123) / 23);
    }

    private static String shorten(String text, int length) {
        return text.length() > length
            ? text.substring(0, length - 2) + ".."
            : text;
    }

    private void finish(InputConstants.Key key) {
        if (editingPrimary) {
            listening.setKey(key);
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        } else {
            SecondaryKeybinds.assign(listening, key);
        }

        listening = null;
        editingPrimary = false;
        confirmReset = false;

        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                listening = null;
                rebuildWidgets();
                return true;
            }

            if (
                event.key() == GLFW.GLFW_KEY_DELETE ||
                event.key() == GLFW.GLFW_KEY_BACKSPACE
            ) {
                if (editingPrimary) {
                    finish(
                        InputConstants.Type.KEYSYM.getOrCreate(
                            GLFW.GLFW_KEY_UNKNOWN
                        )
                    );
                } else {
                    SecondaryKeybinds.clear(listening);
                    listening = null;
                    rebuildWidgets();
                }

                return true;
            }

            if (
                event.key() != GLFW.GLFW_KEY_UNKNOWN ||
                event.scancode() != 0
            ) {
                finish(InputConstants.getKey(event));
            }

            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(
        MouseButtonEvent event,
        boolean doubleClick
    ) {
        if (listening != null) {
            finish(
                InputConstants.Type.MOUSE.getOrCreate(
                    event.button()
                )
            );

            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor g,
        int mouseX,
        int mouseY,
        float delta
    ) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        int left = width / 2 - 207;

        g.text(
            font,
            "SECONDARY KEYBINDS - vanilla & modded actions",
            left,
            9,
            0xFFFFFFFF,
            true
        );

        g.text(font, "Action", left, 51, 0xFFCCCCCC, true);
        g.text(font, "Original", left + 158, 51, 0xFFCCCCCC, true);
        g.text(font, "Second key", left + 256, 51, 0xFFCCCCCC, true);
        g.text(font, "! = conflict", left + 365, 51, 0xFFFFAA77, true);

        int shown = pageSize();

        for (
            int i = page * shown;
            i < Math.min(visible.size(), (page + 1) * shown);
            i++
        ) {
            KeyMapping mapping = visible.get(i);
            int y = 62 + (i - page * shown) * 23;

            String label = Component.translatable(
                mapping.getName()
            ).getString();

            g.text(
                font,
                shorten(label, 22),
                left,
                y + 6,
                0xFFFFFFFF,
                true
            );
        }

        String status = listening == null
            ? "Edit original or second key. Esc cancels - Del clears"
            : "PRESS A KEY OR MOUSE BUTTON (Esc cancels)";

        g.text(
            font,
            status,
            left,
            height - 77,
            listening == null ? 0xFFB7C4D8 : 0xFFFFCF77,
            true
        );

        g.text(
            font,
            "Page " + (page + 1) + " / " +
                Math.max(1, (visible.size() - 1) / shown + 1) +
                " - " + visible.size() + " controls",
            left,
            height - 31,
            0xFFAAAAAA,
            true
        );
    }
}

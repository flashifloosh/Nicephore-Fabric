package com.vandendaelen.nicephore.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vandendaelen.nicephore.config.NicephoreConfig;
import com.vandendaelen.nicephore.enums.OperatingSystems;
import com.vandendaelen.nicephore.enums.ScreenshotFilter;
import com.vandendaelen.nicephore.helper.PlayerHelper;
import com.vandendaelen.nicephore.util.CopyImageToClipBoard;
import com.vandendaelen.nicephore.util.FilterListener;
import com.vandendaelen.nicephore.util.Util;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class ScreenshotScreen extends Screen {
    private static final Text TITLE = Text.translatable("nicephore.gui.screenshots");
    private static final File SCREENSHOTS_DIR = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
    private static NativeImageBackedTexture SCREENSHOT_TEXTURE;
    private final NicephoreConfig config;
    private final FilterListener filterListener;
    private ArrayList<File> screenshots;
    private int index;
    private int galleryIndex = -1;
    private float aspectRatio;

    public ScreenshotScreen(int index, int galleryIndex, FilterListener listener) {
        super(TITLE);
        this.index = index;
        this.galleryIndex = galleryIndex;
        config = AutoConfig.getConfigHolder(NicephoreConfig.class).getConfig();
        this.filterListener = listener;
    }

    public ScreenshotScreen(int index, int galleryIndex) {
        this(index, galleryIndex, null);
    }

    public ScreenshotScreen() {
        this(0, -1, null);
    }

    public static boolean canBeShow() {
        return SCREENSHOTS_DIR.exists() && SCREENSHOTS_DIR.list().length > 0;
    }

    private static String getFileSizeMegaBytes(File file) {
        final double size = FileUtils.sizeOf(file);
        final NumberFormat formatter = new DecimalFormat("#0.00");
        final int MB_SIZE = 1024 * 1024;
        final int KB_SIZE = 1024;

        if (size > MB_SIZE) {
            return MessageFormat.format("{0} MB", formatter.format((double) FileUtils.sizeOf(file) / MB_SIZE));
        }
        return MessageFormat.format("{0} KB", formatter.format((double) FileUtils.sizeOf(file) / KB_SIZE));
    }

    @Override
    protected void init() {
        super.init();

        screenshots = (ArrayList<File>) Arrays.stream(SCREENSHOTS_DIR.listFiles(config.getFilter().getPredicate()))
                .sorted(Comparator.comparingLong(File::lastModified).reversed()).collect(Collectors.toList());
        index = getIndex();
        aspectRatio = 1.7777F;

        if (!screenshots.isEmpty()) {

            try (ImageInputStream in = ImageIO.createImageInputStream(screenshots.get(index))) {
                final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(in);
                        aspectRatio = reader.getWidth(0) / (float) reader.getHeight(0);
                    } finally {
                        reader.dispose();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (SCREENSHOT_TEXTURE != null) {
                SCREENSHOT_TEXTURE.close();
            }

            File fileToLoad = screenshots.get(index);
            if (fileToLoad.exists()) {
                SCREENSHOT_TEXTURE = Util.fileToTexture(fileToLoad);
            } else {
                closeScreen("nicephore.screenshots.loading.error");
                return;
            }
        }
    }

    private void changeFilter() {
        ScreenshotFilter nextFilter = config.getFilter().next();
        config.setFilter(nextFilter);
        init();

        if (filterListener != null) {
            filterListener.onFilterChange(nextFilter);
        }
    }

    private void closeScreen(String textComponentId) {
        this.close();
        PlayerHelper.sendHotbarMessage(Text.translatable(textComponentId));
    }

    private void deleteScreenshot(File file) {
        MinecraftClient.getInstance().setScreen(new DeleteConfirmScreen(file, index, galleryIndex));
    }

    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        int centerX = this.width / 2;
        int width = (int) (this.width * 0.5);
        int height = (int) (width / aspectRatio);

        renderBackground(context, mouseX, mouseY, partialTicks);

        final var filterButton = ButtonWidget.builder(Text.translatable("nicephore.screenshot.filter", config.getFilter()
                        .name()), button -> changeFilter())
                .dimensions(10, 10, 100, 20)
                .build();

        final var exitButton = ButtonWidget.builder(Text.translatable("nicephore.screenshot.exit"), button -> close())
                .dimensions(this.width - 60, 10, 50, 20)
                .build();

        this.clearChildren();
        this.addDrawableChild(filterButton);
        this.addDrawableChild(exitButton);

        if (!screenshots.isEmpty()) {
            final var previousButton = ButtonWidget.builder(Text.of("<"), button -> modIndex(-1))
                    .dimensions(this.width / 2 - 80, this.height / 2 + 100, 20, 20).build();
            final var nextButton = ButtonWidget.builder(Text.of(">"), button -> modIndex(1))
                    .dimensions(this.width / 2 + 60, this.height / 2 + 100, 20, 20).build();

            this.addDrawableChild(previousButton);
            this.addDrawableChild(nextButton);

            var copyButton = ButtonWidget.builder(Text.translatable("nicephore.gui.screenshots.copy"), button -> {
                if (CopyImageToClipBoard.getInstance().copyImage(screenshots.get(index))) {
                    PlayerHelper.sendMessage(Text.translatable("nicephore.clipboard.success"));
                } else {
                    PlayerHelper.sendMessage(Text.translatable("nicephore.clipboard.error"));
                }
            }).dimensions(this.width / 2 - 52, this.height / 2 + 75, 50, 20).build();

            copyButton.active = OperatingSystems.getOS().getManager() != null;
            if (!copyButton.active && (mouseX >= (double) copyButton.getX() && mouseY >= (double) copyButton.getY() && mouseX < (double) (copyButton.getX() + copyButton.getWidth()) && mouseY < (double) (copyButton.getY() + copyButton.getHeight()))) {
                List<OrderedText> orderedTexts = new ArrayList<>();
                orderedTexts.add(Text.translatable("nicephore.gui.screenshots.copy.unable")
                        .formatted(Formatting.RED).asOrderedText());
                context.drawOrderedTooltip(client.textRenderer, orderedTexts, mouseX, mouseY);
            }

            this.addDrawableChild(copyButton);
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("nicephore.gui.screenshots.delete"), button -> deleteScreenshot(screenshots.get(index)))
                    .dimensions(this.width / 2 + 3, this.height / 2 + 75, 50, 20).build());
        }

        if (screenshots.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.translatable("nicephore.screenshots.empty"), centerX, 20, Color.RED.getRGB());
        } else {
            final File currentScreenshot = screenshots.get(index);
            if (currentScreenshot.exists()) {
                RenderSystem.setShaderTexture(0, SCREENSHOT_TEXTURE.getGlId());
                RenderSystem.enableBlend();
                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                Matrix4f matrix4f = context.getMatrices().peek().getPositionMatrix();
                BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
                bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                bufferBuilder.vertex(matrix4f, centerX - width / 2, 50, 0.001f).texture(0, 0).next();
                bufferBuilder.vertex(matrix4f, centerX - width / 2, 50 + height, 0.001f).texture(0, 1).next();
                bufferBuilder.vertex(matrix4f, centerX + width / 2, 50 + height, 0.001f).texture(1, 1).next();
                bufferBuilder.vertex(matrix4f, centerX + width / 2, 50, 0.001f).texture(1, 0).next();
                BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
                RenderSystem.disableBlend();

                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.translatable("nicephore.gui.screenshots.pages", index + 1, screenshots.size()), centerX, 20, Color.WHITE.getRGB());
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.of(MessageFormat.format("{0} ({1})", currentScreenshot.getName(), getFileSizeMegaBytes(currentScreenshot))), centerX, 35, Color.WHITE.getRGB());
            } else {
                closeScreen("nicephore.screenshots.loading.error");
            }
        }

        super.render(context, mouseX, mouseY, partialTicks);
    }

    private void modIndex(int value) {
        int max = screenshots.size();
        if (index + value >= 0 && index + value < max) {
            index += value;
        } else {
            if (index + value < 0) {
                index = max - 1;
            } else {
                index = 0;
            }
        }
        init();
    }

    private int getIndex() {
        if (index >= screenshots.size() || index < 0) {
            index = screenshots.size() - 1;
        }
        return index;
    }

    @Override
    public void close() {
        if (SCREENSHOT_TEXTURE != null) {
            SCREENSHOT_TEXTURE.close();
        }

        if (galleryIndex > -1) {
            MinecraftClient.getInstance().setScreen(new GalleryScreen(galleryIndex));
        } else {
            super.close();
        }
    }
}

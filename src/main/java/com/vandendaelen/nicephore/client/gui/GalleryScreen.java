package com.vandendaelen.nicephore.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vandendaelen.nicephore.config.NicephoreConfig;
import com.vandendaelen.nicephore.enums.ScreenshotFilter;
import com.vandendaelen.nicephore.helper.PlayerHelper;
import com.vandendaelen.nicephore.util.FilterListener;
import com.vandendaelen.nicephore.util.Util;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.GuiAtlasManager;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Scaling;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class GalleryScreen extends Screen implements FilterListener {
    private static final Text TITLE = Text.translatable("nicephore.gui.screenshots");
    private static final File SCREENSHOTS_DIR = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
    private static final int ROW = 2;
    private static final int COLUMN = 4;
    private static final int IMAGES_TO_DISPLAY = ROW * COLUMN;
    private static ArrayList<NativeImageBackedTexture> SCREENSHOT_TEXTURES = new ArrayList<>();
    private final NicephoreConfig config;
    private ArrayList<File> screenshots;
    private ArrayList<List<File>> pagesOfScreenshots;
    private int index;
    private float aspectRatio;

    public GalleryScreen() {
        super(TITLE);
        config = AutoConfig.getConfigHolder(NicephoreConfig.class).getConfig();
    }

    public GalleryScreen(int index) {
        super(TITLE);
        this.index = index;
        config = AutoConfig.getConfigHolder(NicephoreConfig.class).getConfig();
    }

    public static boolean canBeShow() {
        return SCREENSHOTS_DIR.exists() && SCREENSHOTS_DIR.list().length > 0;
    }

    @Override
    protected void init() {
        super.init();

        screenshots = (ArrayList<File>) Arrays.stream(SCREENSHOTS_DIR.listFiles(config.getFilter().getPredicate()))
                .sorted(Comparator.comparingLong(File::lastModified).reversed()).collect(Collectors.toList());
        pagesOfScreenshots = (ArrayList<List<File>>) Util.batches(screenshots, IMAGES_TO_DISPLAY)
                .collect(Collectors.toList());
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

            SCREENSHOT_TEXTURES.forEach(NativeImageBackedTexture::close);
            SCREENSHOT_TEXTURES.clear();

            List<File> filesToLoad = pagesOfScreenshots.get(index);
            if (!filesToLoad.isEmpty()) {
                filesToLoad.forEach(file -> SCREENSHOT_TEXTURES.add(Util.fileToTexture(file)));
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
    }

    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        final int centerX = this.width / 2;
        final int imageWidth = (int) (this.width * 1.0 / 5);
        final int imageHeight = (int) (imageWidth / aspectRatio);

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
        }

        if (pagesOfScreenshots.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.translatable("nicephore.screenshots.empty"), centerX, 20, Color.RED.getRGB());
        } else {
            final List<File> currentPage = pagesOfScreenshots.get(index);
            if (currentPage.stream().allMatch(File::exists)) {
                SCREENSHOT_TEXTURES.forEach(TEXTURE -> {
                    final int imageIndex = SCREENSHOT_TEXTURES.indexOf(TEXTURE);
                    final String name = currentPage.get(imageIndex).getName();
                    final Text text = Text.of(StringUtils.abbreviate(name, 13));

                    int x = centerX - (15 - (imageIndex % 4) * 10) - (2 - (imageIndex % 4)) * imageWidth;
                    int y = 50 + (imageIndex / 4 * (imageHeight + 30));

                    /* render billboard */
                    RenderSystem.setShaderTexture(0, TEXTURE.getGlId());
                    RenderSystem.enableBlend();
                    RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                    Matrix4f matrix4f = context.getMatrices().peek().getPositionMatrix();
                    BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
                    bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    bufferBuilder.vertex(matrix4f, x, y, 0.001f).texture(0, 0).next();
                    bufferBuilder.vertex(matrix4f, x, y + imageHeight, 0.001f).texture(0, 1).next();
                    bufferBuilder.vertex(matrix4f, x + imageWidth, y + imageHeight, 0.001f).texture(1, 1).next();
                    bufferBuilder.vertex(matrix4f, x + imageWidth, y, 0.001f).texture(1, 0).next();
                    BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
                    RenderSystem.disableBlend();

                    drawExtensionBadge(context, FilenameUtils.getExtension(name), x - 10, y + 14);
                    var openImageButton = ButtonWidget.builder(text, button -> openScreenshotScreen(screenshots.indexOf(currentPage.get(imageIndex))))
                            .dimensions(x, y + 5 + imageHeight, imageWidth, 20).build();

                    this.addDrawableChild(openImageButton);
                });

                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.translatable("nicephore.gui.gallery.pages", index + 1, pagesOfScreenshots.size()), centerX, this.height / 2 + 105, Color.WHITE.getRGB());
            }
        }
        super.render(context, mouseX, mouseY, partialTicks);
    }

    private void drawExtensionBadge(DrawContext context, String extension, int x, int y) {
        if (config.getFilter() == ScreenshotFilter.BOTH) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.of(extension.toUpperCase()), x + 12, y - 12, Color.WHITE.getRGB());
        }
    }

    private void modIndex(int value) {
        final int max = pagesOfScreenshots.size();
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

    private void openScreenshotScreen(int value) {
        MinecraftClient.getInstance().setScreen(new ScreenshotScreen(value, index, this));
    }

    private int getIndex() {
        if (index >= pagesOfScreenshots.size() || index < 0) {
            index = pagesOfScreenshots.size() - 1;
        }
        return index;
    }

    private void closeScreen(String textComponentId) {
        this.close();
        PlayerHelper.sendHotbarMessage(Text.translatable(textComponentId));
    }


    @Override
    public void close() {
        SCREENSHOT_TEXTURES.forEach(NativeImageBackedTexture::close);
        SCREENSHOT_TEXTURES.clear();

        super.close();
    }

    @Override
    public void onFilterChange(ScreenshotFilter filter) {
        changeFilter();
    }
}
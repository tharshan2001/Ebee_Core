package com.ebee.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

@Slf4j
@Service
public class ImageConversionService {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final float WEBP_QUALITY = 0.8f;

    public boolean isImage(String contentType) {
        return contentType != null && IMAGE_CONTENT_TYPES.contains(contentType);
    }

    public byte[] convertToWebP(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IOException("Cannot read image from file: " + file.getOriginalFilename());
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            log.warn("No WebP writer available, uploading original format");
            return null;
        }

        ImageWriter writer = writers.next();
        ImageOutputStream ios = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(WEBP_QUALITY);

            writer.write(null, new IIOImage(image, null, null), param);

            byte[] webpBytes = baos.toByteArray();
            log.debug("Converted {} to WebP: {} bytes (original: {} bytes)",
                    file.getOriginalFilename(), webpBytes.length, file.getSize());
            return webpBytes;
        } finally {
            if (ios != null) {
                try { ios.close(); } catch (IOException ignored) {}
            }
            writer.dispose();
        }
    }
}

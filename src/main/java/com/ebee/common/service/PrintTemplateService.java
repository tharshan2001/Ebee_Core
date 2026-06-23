package com.ebee.common.service;

import com.ebee.common.config.PrintProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.ImageResource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class PrintTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PrintTemplateService.class);

    private final TemplateEngine templateEngine;
    private final PrintProperties printProperties;
    private Path logoDir;
    private byte[] logoBytes;

    public PrintTemplateService(PrintProperties printProperties) {
        this.printProperties = printProperties;

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/print/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    @PostConstruct
    void extractLogo() {
        try {
            logoDir = Paths.get(System.getProperty("java.io.tmpdir"), "ebee-print");
            Files.createDirectories(logoDir);
            Path logoPath = logoDir.resolve("ebee_logo.png");
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("templates/print/ebee_logo.png")) {
                if (is == null) {
                    log.warn("Logo file not found at templates/print/ebee_logo.png");
                    return;
                }
                try (OutputStream os = Files.newOutputStream(logoPath)) {
                    is.transferTo(os);
                }
            }
            logoBytes = Files.readAllBytes(logoPath);
            log.info("Logo extracted to {} ({} bytes)", logoPath, logoBytes.length);
        } catch (Exception e) {
            log.warn("Failed to extract logo file", e);
        }
    }

    @PreDestroy
    void cleanup() {
        if (logoDir != null) {
            try {
                try (var files = Files.walk(logoDir)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                }
            } catch (Exception e) {
                log.warn("Failed to clean up temp dir", e);
            }
        }
    }

    public byte[] renderToPdf(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariable("company", new CompanyInfo(
                printProperties.getCompanyName(),
                printProperties.getCompanyAddress(),
                printProperties.getCompanyPhone(),
                printProperties.getCompanyEmail(),
                printProperties.getCompanyRegistration(),
                printProperties.getLogoUrl()
        ));
        variables.forEach(context::setVariable);

        String html = templateEngine.process(templateName, context);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();

            if (logoBytes != null) {
                ITextUserAgent customUa = new ITextUserAgent(renderer.getOutputDevice(), 20) {
                    @Override
                    public ImageResource getImageResource(String uri) {
                        if (uri != null && uri.contains("ebee_logo")) {
                            try {
                                org.openpdf.text.Image img = org.openpdf.text.Image.getInstance(logoBytes);
                                return new ImageResource(uri, new ITextFSImage(img));
                            } catch (Exception e) {
                                log.warn("Failed to embed logo for: {}", uri, e);
                            }
                        }
                        return super.getImageResource(uri);
                    }
                };
                renderer.getSharedContext().setUserAgentCallback(customUa);
            }

            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF for template: " + templateName, e);
        }
    }

    public record CompanyInfo(
            String name,
            String address,
            String phone,
            String email,
            String registration,
            String logoUrl
    ) {}
}

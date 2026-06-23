package com.ebee.common.service;

import com.ebee.common.config.PrintProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
public class PrintTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PrintTemplateService.class);

    private final TemplateEngine templateEngine;
    private final PrintProperties printProperties;

    public PrintTemplateService(PrintProperties printProperties) {
        this.printProperties = printProperties;

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/print/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
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

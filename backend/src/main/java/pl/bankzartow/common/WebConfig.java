package pl.bankzartow.common;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.context.annotation.Configuration;

/**
 * Serves the built React frontend as static resources. Any request that does not
 * match an existing file falls back to {@code index.html}, so that reloading a
 * client-side route (for example {@code /jokes/42}) returns the single-page app
 * instead of a 404. API ({@code /api/**}) and actuator endpoints are handled by
 * their own controllers, which take precedence over this resource handler.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";
    private static final ClassPathResource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /**
     * Returns the requested file when it exists, otherwise the SPA entry point.
     * Requests under {@code /api} never reach here when a controller matches; an
     * unmatched {@code /api} path is left unresolved so it still yields a 404.
     */
    private static final class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.equals("api") || resourcePath.startsWith("api/")) {
                return null;
            }
            return INDEX.exists() ? INDEX : null;
        }
    }
}

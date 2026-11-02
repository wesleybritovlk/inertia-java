package io.github.wesleybritovlk.inertia.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Request-scoped engine the library's public API hangs on. One {@link Inertia} instance per
 * request, created and scoped by the adapter (Spring: {@code @RequestScope} bean; Quarkus:
 * {@code @RequestScoped}). Prop resolution, flash, shared props and redirects all run here.
 *
 * <p>Not {@code final} on purpose: scoped beans proxy by CGLIB subclassing.</p>
 */
public class Inertia {

    private final VersionProvider versionProvider;
    private final PageSerializer serializer;
    private final InertiaRequest request;
    private final LinkedHashMap<String, Prop> sharedProps = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> sharedValues = new LinkedHashMap<>();
    /**
     * Deferred-prop failures with rescue are reported here; default is silent because the page
     * survives ({@link PageKeys#RESCUED_PROPS}). Wire your own handler (e.g. to a logger).
     */
    private Consumer<Throwable> errorHandler = failure -> {
    };

    public Inertia(VersionProvider versionProvider, PageSerializer serializer, InertiaRequest request) {
        this.versionProvider = versionProvider;
        this.serializer = serializer;
        this.request = request;
    }

    /**
     * CDI/Quarkus client-proxy support: normal-scoped beans ({@code @RequestScoped}
     * producers) get injected through generated subclasses, which need a no-arg
     * constructor to instantiate. Never call it manually — built through
     * {@link #Inertia(VersionProvider, PageSerializer, InertiaRequest)}.
     */
    protected Inertia() {
        this.versionProvider = null;
        this.serializer = null;
        this.request = null;
    }

    public InertiaRequest request() {
        return request;
    }

    // ---------------------------------------------------------------- shared props

    /** Shared prop included on standard visits (filtered by partial reloads unless always). */
    public Inertia shared(String key, Object value) {
        sharedValues.put(key, value);
        return this;
    }

    /** Shared prop with lazy evaluation. */
    public Inertia shared(String key, Supplier<?> supplier) {
        sharedProps.put(key, new Prop.Plain(supplier));
        return this;
    }

    /** Shared prop always included, even on partial reloads that didn't request it. */
    public Inertia sharedAlways(String key, Object value) {
        sharedProps.put(key, new Prop.Always(() -> value));
        return this;
    }

    /** Shared prop always included with lazy evaluation. */
    public Inertia sharedAlways(String key, Supplier<?> supplier) {
        sharedProps.put(key, new Prop.Always(supplier));
        return this;
    }

    /**
     * Shared once prop (inertia-laravel {@code shareOnce()}): remember-across-pages
     * semantics on shared data; the same once options apply ({@code as}/ {@code until}/
     * {@code fresh}).
     */
    public Inertia sharedOnce(String key, Supplier<?> supplier) {
        return sharedOnce(key, supplier, null);
    }

    /** Shared once prop with customization, see {@link #sharedOnce(String, Supplier)}. */
    public Inertia sharedOnce(String key, Supplier<?> supplier, Consumer<OnceOptions> options) {
        OnceOptions o = new OnceOptions();
        if (options != null) {
            options.accept(o);
        }
        String onceKey = o.onceKey == null ? key : o.onceKey;
        Long fixedExpiry = o.expiresAt == null ? null : o.expiresAt.toEpochMilli();
        sharedProps.put(key, new Prop.Once(o.wrappedProp(supplier), onceKey, fixedExpiry, o.expiresAfter, o.fresh));
        return this;
    }

    /** One-shot values adopted (and consumed) by the next rendered page. */
    public Inertia flash(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        InertiaFlashStore store = request.flash();
        Object previous = store.peek(FlashKeys.FLASH);
        Map<String, Object> merged = new java.util.LinkedHashMap<>();
        if (previous instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) previous).entrySet()) {
                if (entry.getKey() != null) {
                    merged.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        merged.putAll(values);
        store.put(FlashKeys.FLASH, merged);
        return this;
    }

    /** Next rendered page clears the client-side history buffer. */
    public Inertia clearHistory() {
        request.flash().put(FlashKeys.CLEAR_HISTORY, Boolean.TRUE);
        return this;
    }

    /** Next rendered page preserves the URL fragment across client navigation. */
    public Inertia preserveFragment() {
        request.flash().put(FlashKeys.PRESERVE_FRAGMENT, Boolean.TRUE);
        return this;
    }

    /** Registers a failure handler for rescued deferred props (logs, metrics, ...). */
    public Inertia onPropFailure(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler == null ? this.errorHandler : errorHandler;
        return this;
    }

    // ---------------------------------------------------------------- outcomes

    /** Starts a page build; customize with {@code render} consumers. */
    public InertiaPage render(String component) {
        if (component == null || component.trim().isEmpty()) {
            throw new IllegalArgumentException("Inertia component name must not be blank");
        }
        return new InertiaPage(component);
    }

    /** Page build with prop customization, Laravel-{@code Inertia::render(component, props)} style. */
    public InertiaPage render(String component, Consumer<InertiaPage> props) {
        InertiaPage page = render(component);
        if (props != null) {
            props.accept(page);
        }
        return page;
    }

    /** Normal HTTP redirect (adapters may upgrade 302&rarr;303 and fragment redirects per protocol). */
    public InertiaResult redirect(String location) {
        return InertiaResult.redirect(location);
    }

    /**
     * Full page visit redirect (for targets the client JS cannot open in-place, e.g. other
     * apps, SSO flows). Inertia visit &rarr; 409 + {@code X-Inertia-Location}; plain visit &rarr; 302.
     */
    public InertiaResult location(String externalUrl) {
        if (request.isInertiaVisit()) {
            return InertiaResult.externalLocation(externalUrl);
        }
        return InertiaResult.redirect(externalUrl);
    }

    /** Resolves a page against the current request (partial/props/flash) into the final output. */
    public InertiaResult result(InertiaPage page) {
        return PageResolver.resolve(versionProvider, serializer, request, sharedProps, sharedValues, page, errorHandler);
    }
}

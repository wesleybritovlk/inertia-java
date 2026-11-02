package io.github.wesleybritovlk.inertia.demo.quarkus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.quarkus.ValidationErrors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;

/**
 * In-memory CRUD demo over the protocol — the store lives in this controller on
 * purpose (single process demo of the Protocol flow): the list page plus POST
 * (create), PATCH (toggle) and DELETE (remove) mutations that redirect back (the
 * adapter filter upgrades the PATCH/DELETE redirects to 303, mirroring Laravel).
 * The same pages the Spring demo serves: one front-end build, two back-ends.
 */
@Path("/form-todo")
@ApplicationScoped
public class SimpleFormTodoController {

    private static final String ROUTE = "/form-todo";

    private final Inertia inertia;

    private final Map<Long, Todo> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Inject
    SimpleFormTodoController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GET
    public InertiaPage index() {
        return inertia.render("SimpleFormTodo/Index", page -> page
                .with("todos", all()));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public InertiaResult create(Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        if (title == null || title.isBlank()) {
            ValidationErrors.store(inertia, Map.of("title", "The todo title is required."), "default");
            return inertia.redirect(ROUTE);
        }
        add(title.trim());
        return inertia.redirect(ROUTE);
    }

    @PATCH
    @Path("/{id}")
    public InertiaResult toggle(@PathParam("id") long id) {
        Todo todo = store.get(id);
        if (todo == null) {
            throw new java.util.NoSuchElementException("Todo " + id + " not found");
        }
        store.put(id, new Todo(id, todo.title(), !todo.done()));
        return inertia.redirect(ROUTE);
    }

    @DELETE
    @Path("/{id}")
    public InertiaResult delete(@PathParam("id") long id) {
        if (store.remove(id) == null) {
            throw new java.util.NoSuchElementException("Todo " + id + " not found");
        }
        return inertia.redirect(ROUTE);
    }

    private List<Todo> all() {
        return store.values().stream()
                .sorted(Comparator.comparingLong(Todo::id))
                .toList();
    }

    private void add(String title) {
        long id = ids.incrementAndGet();
        store.put(id, new Todo(id, title, false));
    }

    /**
     * Store entry: prop value travels through the application Jackson mapper, which needs
     * this record registered in the native binary (Quarkus' agent-equivalent — the
     * [native doc](../docs/compile-native.md) teaches the general rule for exotic types).
     */
    @io.quarkus.runtime.annotations.RegisterForReflection
    record Todo(Long id, String title, boolean done) {
    }
}

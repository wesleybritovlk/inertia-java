package io.github.wesleybritovlk.inertia.demo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;
import io.github.wesleybritovlk.inertia.core.InertiaResult;
import io.github.wesleybritovlk.inertia.spring.ValidationErrors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * In-memory CRUD demo over the protocol (the Boot 2 twin of the Jakarta demo — java 8:
 * record store becomes a plain final class) — the store lives in this controller on
 * purpose (single process demo of the Protocol flow): the list page plus POST (create),
 * PATCH (toggle) and DELETE (remove) mutations that redirect back (the adapter filter
 * upgrades the PATCH/DELETE redirects to 303, mirroring Laravel).
 */
@RestController
@RequestMapping("/form-todo")
class SimpleFormTodoController {
    private static final String ROUTE = "/form-todo";

    private final Inertia inertia;

    private final Map<Long, Todo> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    SimpleFormTodoController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping
    InertiaPage index() {
        return inertia.render("SimpleFormTodo/Index", page -> page
                .with("todos", all()));
    }

    @PostMapping
    InertiaResult create(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.trim().isEmpty()) {
            org.springframework.validation.MapBindingResult errors =
                    new org.springframework.validation.MapBindingResult(Map.of(), "todo");
            errors.rejectValue("title", "NotBlank", "The todo title is required.");
            ValidationErrors.store(inertia, errors, "default");
            return inertia.redirect(ROUTE);
        }
        add(title.trim());
        return inertia.redirect(ROUTE);
    }

    @PatchMapping("/{id}")
    InertiaResult toggle(@PathVariable long id) {
        Todo todo = store.get(id);
        if (todo == null) {
            throw new java.util.NoSuchElementException("Todo " + id + " not found");
        }
        store.put(id, new Todo(id, todo.getTitle(), !todo.isDone()));
        return inertia.redirect(ROUTE);
    }

    @DeleteMapping("/{id}")
    InertiaResult delete(@PathVariable long id) {
        if (store.remove(id) == null) {
            throw new java.util.NoSuchElementException("Todo " + id + " not found");
        }
        return inertia.redirect(ROUTE);
    }

    private List<Todo> all() {
        return store.values().stream()
                .sorted(Comparator.comparingLong(Todo::getId))
                .collect(java.util.stream.Collectors.toList());
    }

    private void add(String title) {
        long id = ids.incrementAndGet();
        store.put(id, new Todo(id, title, false));
    }

    /**
     * Store entry — java 8 value holder with Jackson-2 visible accessors (Boot 2's
     * Jackson 2.13 does not discover {@code id()}-style accessors; the Boot 3 demo's
     * {@code record} serializes natively there). JSON keys stay {@code id/title/done}.
     */
    public static final class Todo {

        private final Long id;
        private final String title;
        private final boolean done;

        Todo(Long id, String title, boolean done) {
            this.id = id;
            this.title = title;
            this.done = done;
        }

        public Long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public boolean isDone() {
            return done;
        }
    }
}

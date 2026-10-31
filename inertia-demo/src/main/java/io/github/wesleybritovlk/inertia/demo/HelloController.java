package io.github.wesleybritovlk.inertia.demo;

import io.github.wesleybritovlk.inertia.core.Inertia;
import io.github.wesleybritovlk.inertia.core.InertiaPage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Hello world through the adapter: a plain controller returning an Inertia page.
 */
@Controller
class HelloController {

    private final Inertia inertia;

    HelloController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/")
    InertiaPage hello() {
        return inertia.render("Hello/Index")
                .with("greeting", "Hello World from inertia-java-bean")
                .with("builtWith", "Spring Boot + Vite + Inertia");
    }
}

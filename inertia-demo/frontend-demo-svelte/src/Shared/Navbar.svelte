<script>
    import { Link, usePage } from '@inertiajs/svelte';

    const page = usePage();
    const isActive = (href) => page.url === href || (href !== '/' && page.url.startsWith(href));
</script>

<nav class="nav">
    <a href="/" class:active={isActive('/')}>Hello</a>
    <a href="/form-todo" class:active={isActive('/form-todo')}>Simple Form Todo</a>
</nav>

<style>
    .nav {
        display: flex;
        gap: 16px;
        justify-content: center;
        padding: 14px;
        background: #eef2ff;
        margin-bottom: 20px;
    }
    .nav :global(a) {
        text-decoration: none;
        color: #4338ca;
        font-weight: 600;
    }
    .nav :global(a.active) {
        text-decoration: underline;
    }
</style>

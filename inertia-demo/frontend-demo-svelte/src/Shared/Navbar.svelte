<script>
    import { Link, usePage } from '@inertiajs/svelte';

    const page = usePage();
    const isActive = (href) => page.url === href || (href !== '/' && page.url.startsWith(href));
    const navClass = (href) => (isActive(href) ? 'active' : '');
</script>

<nav class="nav">
    <Link href="/" class={navClass('/')}>Hello</Link>
    <Link href="/form-todo" class={navClass('/form-todo')}>Simple Form Todo</Link>
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

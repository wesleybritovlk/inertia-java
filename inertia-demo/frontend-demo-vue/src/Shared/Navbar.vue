<script setup>
import { Link, usePage } from '@inertiajs/vue3';

const page = usePage();
const active = (href) => page.url === href || (href !== '/' && page.url.startsWith(href));
</script>

<template>
  <nav class="nav">
    <Link href="/" :class="{ active: active('/') }">Hello</Link>
    <Link href="/form-todo" :class="{ active: active('/form-todo') }">Simple Form Todo</Link>
  </nav>
</template>

<style>
.nav {
    display: flex;
    gap: 16px;
    justify-content: center;
    padding: 14px;
    background: #eef2ff;
    margin-bottom: 20px;
}
.nav a {
    text-decoration: none;
    color: #4338ca;
    font-weight: 600;
}
.nav a.active {
    text-decoration: underline;
}
</style>

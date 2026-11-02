<script>
    import { usePage } from '@inertiajs/svelte';
    import Navbar from '../../Shared/Navbar.svelte';

    const page = usePage();
</script>

<svelte:head>
    <title>Hello</title>
</svelte:head>

<Navbar />

<div class="wrap">
    <h1>{page.props.greeting}</h1>
    <p class="badge">{page.props.builtWith}</p>
    <p class="hint">Vite build · hashed assets · asset versioning wired to the Vite manifest</p>
</div>

<style>
    .wrap {
        font-family: system-ui, sans-serif;
        text-align: center;
        margin-top: 22vh;
        color: #1f2937;
    }
    h1 {
        font-size: 2.5rem;
        margin: 0 0 12px;
    }
    .badge {
        display: inline-block;
        padding: 8px 14px;
        border-radius: 999px;
        background: #eef2ff;
        color: #4338ca;
        font-size: 0.95rem;
    }
    .hint {
        color: #6b7280;
        font-size: 0.9rem;
    }
</style>

<script>
    import { Link, useForm, usePage } from '@inertiajs/svelte';
    import Navbar from '../../Shared/Navbar.svelte';

    const page = usePage(); // adapter v3 runes state (page.svelte.js) - reactive, NOT a store
    const form = useForm({ title: '' }); // deep-reactive via $state, mutate fields directly

    const submit = (event) => {
        event.preventDefault();
        form.post('/form-todo');
    };
</script>

<Navbar />

<div class="wrap">
    <h1>Todos</h1>

    <form class="form" onsubmit={submit}>
        <input
            placeholder="New todo title"
            value={form.title}
            oninput={(e) => (form.title = e.currentTarget.value)}
        />
        <button type="submit" disabled={form.processing}>Add</button>
        {#if form.errors.title}
            <p class="error">{form.errors.title}</p>
        {/if}
    </form>

    <ul class="list">
        {#each page.props.todos as todo (todo.id)}
            <li class:done={todo.done}>
                <span class="title">{todo.title}</span>
                <Link as="button" method="patch" href={`/form-todo/${todo.id}`}>
                    {todo.done ? 'Undo' : 'Done'}
                </Link>
                <Link as="button" method="delete" href={`/form-todo/${todo.id}`}>
                    Delete
                </Link>
            </li>
        {/each}
    </ul>

    <p class="hint">Inertia protocol flow: POST create (302), PATCH/DELETE mutations (redirect upgraded to 303), validation errors round-tripping through the errors bag — and SPA navigation through this navbar.</p>
</div>

<style>
    .wrap {
        font-family: system-ui, sans-serif;
        max-width: 640px;
        margin: 0 auto;
        color: #1f2937;
        padding: 12px;
    }
    h1 {
        font-weight: 800;
        text-align: center;
    }
    .form {
        display: flex;
        gap: 8px;
        align-items: flex-start;
        margin-bottom: 16px;
    }
    .form input {
        flex: 1;
        padding: 10px;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
    }
    .form button {
        padding: 10px 16px;
        border-radius: 8px;
        border: none;
        background: #4338ca;
        color: #fff;
        font-weight: 600;
        cursor: pointer;
    }
    .error {
        color: #dc2626;
        font-size: 0.9rem;
        margin: 0;
        flex-basis: 100%;
    }
    .list {
        list-style: none;
        padding: 0;
        display: grid;
        gap: 8px;
    }
    .list li {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        padding: 10px 12px;
        display: flex;
        align-items: center;
        gap: 8px;
    }
    .list li.done .title {
        text-decoration: line-through;
        color: #9ca3af;
    }
    .list .title {
        flex: 1;
    }
    .hint {
        color: #6b7280;
        font-size: 0.9rem;
    }
</style>

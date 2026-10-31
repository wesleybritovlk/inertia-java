<script setup>
import { computed } from 'vue';
import { Head, Link, useForm, usePage } from '@inertiajs/vue3';
import Navbar from '../../Shared/Navbar.vue';

const page = usePage();
const todos = computed(() => page.props.todos ?? []);
const form = useForm({ title: '' });

const submit = () => form.post('/form-todo');
</script>

<template>
  <Head title="Simple Form Todo" />
  <Navbar />
  <div class="wrap">
    <h1>Todos</h1>

    <form class="form" @submit.prevent="submit">
      <input v-model="form.title" placeholder="New todo title" />
      <button type="submit" :disabled="form.processing">Add</button>
      <p v-if="form.errors.title" class="error">{{ form.errors.title }}</p>
    </form>

    <ul class="list">
      <li v-for="todo in todos" :key="todo.id" :class="{ done: todo.done }">
        <span class="title">{{ todo.title }}</span>
        <Link as="button" method="patch" :href="`/form-todo/${todo.id}`">
          {{ todo.done ? 'Undo' : 'Done' }}
        </Link>
        <Link as="button" method="delete" :href="`/form-todo/${todo.id}`">Delete</Link>
      </li>
    </ul>

    <p class="hint">Inertia protocol flow: POST create (302), PATCH/DELETE mutations (redirect upgraded to 303), validation errors round-tripping through the errors bag — and SPA navigation through this navbar.</p>
  </div>
</template>

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
.list button {
    padding: 6px 12px;
    border-radius: 6px;
    border: none;
    background: #e5e7eb;
    cursor: pointer;
}
.hint {
    color: #6b7280;
    font-size: 0.9rem;
}
</style>

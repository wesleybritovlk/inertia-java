import { Head, Link, useForm, usePage } from '@inertiajs/react';
import Navbar from '../../Shared/Navbar.jsx';

const wrap = {
    fontFamily: 'system-ui, sans-serif',
    maxWidth: 640,
    margin: '0 auto',
    color: '#1f2937',
    padding: 12,
};
const h1 = { fontWeight: 800, textAlign: 'center' };
const form = { display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 16 };
const input = {
    flex: 1,
    padding: 10,
    border: '1px solid #cbd5e1',
    borderRadius: 8,
};
const submitBtn = {
    padding: '10px 16px',
    borderRadius: 8,
    border: 'none',
    background: '#4338ca',
    color: '#fff',
    fontWeight: 600,
    cursor: 'pointer',
};
const errorStyle = { color: '#dc2626', fontSize: '0.9rem', margin: 0, flexBasis: '100%' };
const listStyle = { listStyle: 'none', padding: 0, display: 'grid', gap: 8 };
const item = {
    background: '#f8fafc',
    border: '1px solid #e2e8f0',
    borderRadius: 10,
    padding: '10px 12px',
    display: 'flex',
    alignItems: 'center',
    gap: 8,
};
const itemTitle = { flex: 1 };
const itemDoneTitle = { flex: 1, textDecoration: 'line-through', color: '#9ca3af' };
const rowBtn = {
    padding: '6px 12px',
    borderRadius: 6,
    border: 'none',
    background: '#e5e7eb',
    cursor: 'pointer',
};
const hint = { color: '#6b7280', fontSize: '0.9rem' };

export default function Index() {
    const { todos } = usePage().props;
    const todoForm = useForm({ title: '' });

    const submit = (event) => {
        event.preventDefault();
        todoForm.post('/form-todo');
    };

    return (
        <>
            <Head title="Simple Form Todo" />
            <Navbar />
            <div style={wrap}>
                <h1 style={h1}>Todos</h1>

                <form style={form} onSubmit={submit}>
                    <input style={input} value={todoForm.title}
                        onChange={(e) => todoForm.setData('title', e.target.value)}
                        placeholder="New todo title" />
                    <button style={submitBtn} type="submit" disabled={todoForm.processing}>Add</button>
                    {todoForm.errors.title ? <p style={errorStyle}>{todoForm.errors.title}</p> : null}
                </form>

                <ul style={listStyle}>
                    {todos.map((todo) => (
                        <li key={todo.id} style={item}>
                            <span style={todo.done ? itemDoneTitle : itemTitle}>{todo.title}</span>
                            <Link style={rowBtn} as="button" method="patch"
                                href={`/form-todo/${todo.id}`}>
                                {todo.done ? 'Undo' : 'Done'}
                            </Link>
                            <Link style={rowBtn} as="button" method="delete"
                                href={`/form-todo/${todo.id}`}>
                                Delete
                            </Link>
                        </li>
                    ))}
                </ul>

                <p style={hint}>Inertia protocol flow: POST create (302), PATCH/DELETE mutations (redirect upgraded to 303), validation errors round-tripping through the errors bag — and SPA navigation through this navbar.</p>
            </div>
        </>
    );
}

import { Head, usePage } from '@inertiajs/react';
import Navbar from '../../Shared/Navbar.jsx';

const card = {
    fontFamily: 'system-ui, sans-serif',
    textAlign: 'center',
    marginTop: '22vh',
    color: '#1f2937',
};
const title = { fontSize: '2.5rem', margin: '0 0 12px' };
const badge = {
    display: 'inline-block',
    padding: '8px 14px',
    borderRadius: '999px',
    background: '#eef2ff',
    color: '#4338ca',
    fontSize: '0.95rem',
};
const hint = { color: '#6b7280', fontSize: '0.9rem' };

export default function Index() {
    const { greeting, builtWith } = usePage().props;

    return (
        <>
            <Navbar />
            <div style={card}>
                <Head title="Hello" />
                <h1 style={title}>{greeting}</h1>
                <p style={badge}>{builtWith}</p>
                <p style={hint}>React 19 · built by Vite · asset versioning wired to the Vite manifest</p>
            </div>
        </>
    );
}

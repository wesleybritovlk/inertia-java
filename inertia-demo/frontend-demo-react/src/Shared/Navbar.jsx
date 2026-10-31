import { Link, usePage } from '@inertiajs/react';

const nav = {
    display: 'flex',
    gap: 16,
    justifyContent: 'center',
    padding: '14px 10px',
    background: '#eef2ff',
    marginBottom: 20,
};
const link = { textDecoration: 'none', color: '#4338ca', fontWeight: 600 };
const active = { textDecoration: 'underline' };

export default function Navbar() {
    const { url } = usePage();
    const isActive = (href) => url === href || (href !== '/' && url.startsWith(href));

    return (
        <nav style={nav}>
            <Link href="/" style={isActive('/') ? { ...link, ...active } : link}>Hello</Link>
            <Link href="/form-todo" style={isActive('/form-todo') ? { ...link, ...active } : link}>Simple Form Todo</Link>
        </nav>
    );
}

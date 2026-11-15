import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'

export function AppHeader({ menuOpen, onToggle }: { menuOpen: boolean; onToggle: () => void }) {
  return (
    <header className="app-header">
      <div className="app-header-inner">
        <Link className="brand" to="/dashboard"><span className="brand-mark" aria-hidden="true">C</span>Canvas 2.0</Link>
        <span className="header-label">Learning management</span>
        <button className="menu-toggle" type="button" aria-expanded={menuOpen} aria-controls="app-sidebar" onClick={onToggle}>
          {menuOpen ? 'Close menu' : 'Open menu'}
        </button>
      </div>
    </header>
  )
}

export function AppSidebar({ open, onNavigate }: { open: boolean; onNavigate: () => void }) {
  return (
    <aside id="app-sidebar" className={`app-sidebar${open ? ' is-open' : ''}`}>
      <p className="sidebar-label">Workspace</p>
      <nav aria-label="Main navigation">
        {['Dashboard', 'Profile', 'Courses'].map(label => (
          <NavLink key={label} to={`/${label.toLowerCase()}`} onClick={onNavigate}>{label}</NavLink>
        ))}
      </nav>
    </aside>
  )
}

export default function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()
  const content = useRef<HTMLElement>(null)

  useEffect(() => {
    content.current?.focus()
    document.title = `${pathname.split('/')[1] || 'Dashboard'} | Canvas 2.0`
  }, [pathname])

  return (
    <>
      <a className="skip-link" href="#main-content">Skip to main content</a>
      <AppHeader menuOpen={menuOpen} onToggle={() => setMenuOpen(value => !value)} />
      <div className="app-shell">
        <AppSidebar open={menuOpen} onNavigate={() => setMenuOpen(false)} />
        <main id="main-content" className="page-content" tabIndex={-1} ref={content}>
          <Outlet />
        </main>
      </div>
    </>
  )
}

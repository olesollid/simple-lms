import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { users } from '../services'
import { logout } from '../services/auth'

export function AppHeader({ menuOpen, onToggle }: { menuOpen: boolean; onToggle: () => void }) {
  const [userEmail, setUserEmail] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    users.getCurrentUser({ signal: controller.signal }).then(
      user => { if (!controller.signal.aborted) setUserEmail(user.email) },
      () => { if (!controller.signal.aborted) setUserEmail(null) },
    )
    return () => controller.abort()
  }, [])

  async function handleLogout() {
    setLoggingOut(true)
    await logout()
    window.location.href = '/'
  }

  return (
    <header className="app-header">
      <div className="app-header-inner">
        <Link className="brand" to="/dashboard"><span className="brand-mark" aria-hidden="true">C</span>Canvas 2.0</Link>
        {userEmail ? (
          <div className="header-account">
            <span className="header-label">{userEmail}</span>
            <button className="text-link header-logout" type="button" onClick={handleLogout} disabled={loggingOut}>
              {loggingOut ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        ) : (
          <div className="header-account">
            <a className="text-link" href="/oauth2/authorization/github">Sign in with GitHub</a>
            <a className="text-link" href="/oauth2/authorization/google">Sign in with Google</a>
          </div>
        )}
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

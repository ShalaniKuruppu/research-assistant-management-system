import { Link, useLocation } from 'react-router-dom'
import { roleLabel, useAuth } from './auth.jsx'

export default function Shell({ children }) {
  const { user, signOut } = useAuth()
  const { pathname } = useLocation()
  const projects = pathname.startsWith('/projects')

  return (
    <div className="shell">
      <header className="topbar">
        <Link className="brand" to="/">Research Assistant Management</Link>
        <div className="who">
          <Link className={pathname === '/' ? 'nav selected' : 'nav'} to="/">Dashboard</Link>
          <Link className={projects ? 'nav selected' : 'nav'} to="/projects">Projects</Link>
          <span>{user.name}</span>
          <span className="role">{roleLabel(user.role)}</span>
          <button type="button" onClick={signOut}>Sign out</button>
        </div>
      </header>
      <main className="page wide">{children}</main>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { roleLabel, useAuth } from './auth.jsx'

const SIDEBAR_KEY = 'rams.sidebarCollapsed'

export default function Shell({ children }) {
  const { user, signOut } = useAuth()
  const { pathname } = useLocation()
  const supervisor = user.role === 'SUPERVISOR'
  const projects = pathname === '/projects' || pathname.startsWith('/projects/')
  const development = pathname.startsWith('/development-projects')
  const assistants = pathname.startsWith('/research-assistants')
  const profile = pathname.startsWith('/profile')
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(SIDEBAR_KEY) === '1'
    } catch {
      return false
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_KEY, collapsed ? '1' : '0')
    } catch {
      /* ignore */
    }
  }, [collapsed])

  return (
    <div className={`shell${collapsed ? ' collapsed' : ''}`}>
      <aside className="sidebar" aria-label="Main navigation">
        <div className="sidebar-top">
          <Link className="brand" to="/" title="Research Assistant Management">
            {collapsed ? 'RAM' : 'Research Assistant Management'}
          </Link>
          <button
            type="button"
            className="sidebar-toggle"
            aria-expanded={!collapsed}
            aria-controls="sidebar-nav"
            onClick={() => setCollapsed((value) => !value)}
          >
            {collapsed ? '»' : '«'}
            <span className="sr-only">{collapsed ? 'Expand navigation' : 'Collapse navigation'}</span>
          </button>
        </div>
        <nav id="sidebar-nav" className="sidebar-nav">
          <Link className={pathname === '/' ? 'nav selected' : 'nav'} to="/" title="Dashboard">
            {collapsed ? 'D' : 'Dashboard'}
          </Link>
          <Link className={projects ? 'nav selected' : 'nav'} to="/projects" title="Projects">
            {collapsed ? 'P' : 'Projects'}
          </Link>
          <Link
            className={development ? 'nav selected' : 'nav'}
            to="/development-projects"
            title="Development projects"
          >
            {collapsed ? 'DP' : 'Development projects'}
          </Link>
          {supervisor ? (
            <Link
              className={assistants ? 'nav selected' : 'nav'}
              to="/research-assistants"
              title="Research Assistants"
            >
              {collapsed ? 'RA' : 'Research Assistants'}
            </Link>
          ) : null}
          <Link className={profile ? 'nav selected' : 'nav'} to="/profile" title="Profile">
            {collapsed ? 'Me' : 'Profile'}
          </Link>
        </nav>
        <div className="sidebar-user">
          <Link className="sidebar-user-link" to="/profile" title="Edit profile">
            {user.profileImageUrl ? (
              <img className="sidebar-avatar" src={user.profileImageUrl} alt="" width={36} height={36} />
            ) : null}
            <span className="sidebar-user-name">
              {collapsed ? user.name.slice(0, 1).toUpperCase() : user.name}
            </span>
          </Link>
          {!collapsed ? <span className="role">{roleLabel(user.role)}</span> : null}
          <button type="button" onClick={signOut} title="Sign out">
            {collapsed ? 'Out' : 'Sign out'}
          </button>
        </div>
      </aside>
      <main className="page wide">{children}</main>
    </div>
  )
}
